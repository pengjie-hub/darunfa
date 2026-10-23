package com.example.backend.controller;

import com.example.backend.ai.context.AdminActionContext;
import com.example.backend.ai.service.AdminAssistantService;
import com.example.backend.common.ApiResponse;
import com.example.backend.dto.ActionProposal;
import com.example.backend.dto.AiChatRequest;
import com.example.backend.dto.AiChatResponse;
import com.example.backend.dto.AiMemoryItem;
import com.example.backend.entity.AiChatMessage;
import com.example.backend.entity.AiMemory;
import com.example.backend.repository.AiChatMessageRepository;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.service.AdminActionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 后台 AI 运营问答助手接口。
 *
 * <h3>本次改造（T04）：入口从「仅 ADMIN」放宽到「后台角色」</h3>
 * 类上的方法与原来一样，只去掉了 {@code @RequireAdmin}。为什么这不算放宽了安全边界：
 * <ul>
 *   <li>{@code AuthInterceptor} 对 {@code /api/admin/**} 已有兜底闸门：
 *       {@code CONSOLE_ROLES = {ADMIN, OPS, CS}}，普通买家（USER）永远进不来 ——
 *       这正是「后台角色可以找小运」的准确语义；</li>
 *   <li>真正决定「能不能做某件事」的是<b>工具层的权限码</b>：
 *       补货要 {@code product:edit}、发货要 {@code order:ship}、退款要
 *       {@code order:view ∨ cs:manage} … 用 CS 账号问「帮我补货」会得到
 *       「需要「编辑商品」权限」，而不是一张能点的卡片（验收 ②）；</li>
 *   <li>本类的其余 4 个接口都是<b>自服务</b>的（history / memories / memory 都只操作
 *       {@code 当前登录用户} 自己的数据，userId 取自 token 而非请求体），
 *       因此 CS/OPS 能看自己的历史是正确行为，不存在越权读别人记忆的可能。</li>
 * </ul>
 *
 * <h3>operatorId 的唯一来源</h3>
 * 下面 {@code bind(...)} 的三样东西全部来自 {@code AuthInterceptor} 注入的请求属性：
 * {@code ATTR_USER_ID} / {@code ATTR_ROLE} / {@code ATTR_PERMS}。
 * <b>不从请求体取、不从模型参数取</b> —— 模型 Schema 里根本没有 operatorId 这个字段，
 * 它连伪造的入口都没有。上下文用 try-with-resources 绑定，异常路径也能解绑
 *（ThreadLocal 泄漏会让下一次请求带着上一个人的身份跑，这是最危险的一类串号）。
 */
@RestController
@RequestMapping("/api/admin/ai")
public class AiAssistantController {

    private final AdminAssistantService adminAssistantService;
    private final AiChatMessageRepository chatMessageRepository;
    private final AdminActionService adminActionService;

    public AiAssistantController(AdminAssistantService adminAssistantService,
                                 AiChatMessageRepository chatMessageRepository,
                                 AdminActionService adminActionService) {
        this.adminAssistantService = adminAssistantService;
        this.chatMessageRepository = chatMessageRepository;
        this.adminActionService = adminActionService;
    }

    /**
     * 单次问答（自然语言 → 自然语言 + 待确认提案）。工具调用轮数由框架控制，响应时间通常 2-8 秒。
     * <p>
     * 响应体：{@code answer} 必有；{@code proposals} 仅在「确实调用了 prepare_* 且预检通过」时出现；
     * {@code needsClarification} 仅在「小运在追问槽位」时出现。只读问答的响应体与改造前一致。
     */
    @PostMapping("/assistant")
    public ApiResponse<AiChatResponse> ask(@RequestBody AiChatRequest request, HttpServletRequest http) {
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            return ApiResponse.error(400, "请输入问题");
        }
        if (request.getMessage().length() > 500) {
            return ApiResponse.error(400, "问题过长（>500 字），请精简后重试");
        }
        Long userId = currentUserId(http);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        try {
            // 身份上下文：只在这一个请求线程上有效，工具内部据此做预检与权限判定
            try (AdminActionContext ctx = AdminActionContext.bind(
                    userId,
                    currentRole(http),
                    currentPerms(http),
                    AdminActionContext.ENTRY_CHAT,
                    null,
                    http.getRemoteAddr(),
                    http.getHeader("User-Agent"))) {
                AiChatResponse response = adminAssistantService.ask(userId, request.getMessage());
                return ApiResponse.ok(response);
            }
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return ApiResponse.error(502, "AI 服务异常: " + msg);
        }
    }

    /**
     * 当前登录人「未过期 · 待确认」的操作提案（小运常驻入口）。
     *
     * <h3>为什么需要它</h3>
     * 确认卡片原先只渲染在「当轮对话响应」里：一旦刷新页面或重新打开小运，
     * {@code /history} 只回放文字，卡片就凭空消失了（提案本身只有 5 分钟 TTL）。
     * 本接口把「数据库里仍有效的 PROPOSED 行」重新吐成与对话响应<b>同构</b>的
     * {@code ActionProposal[]}，前端用同一个 {@code XiaoyunAction.renderAll} 即可渲染。
     *
     * <h3>权限与隔离</h3>
     * 不加 {@code @RequireAdmin}：OPS / CS 也要能看<b>自己</b>的待确认提案；
     * 后台角色闸门由 {@link AuthInterceptor} 对 {@code /api/admin/**} 兜底。
     * {@code operatorId} 只从请求属性 {@code ATTR_USER_ID} 取，<b>不接受任何前端参数</b>。
     */
    @GetMapping("/actions/pending")
    public ApiResponse<List<ActionProposal>> pendingActions(HttpServletRequest http) {
        Long userId = currentUserId(http);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        try {
            return ApiResponse.ok(adminActionService.pendingProposals(userId, 20));
        } catch (Exception e) {
            // 逐条脏数据已在 service 内跳过；走到这里说明是查询本身异常。
            // 常驻入口是「锦上添花」的辅助面板，绝不因为它把整个小运页面拖垮。
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return ApiResponse.error(500, "读取待确认操作失败: " + msg);
        }
    }

    /**
     * 查询当前用户最近对话历史（最多 20 条，时间正序返回）。
     * <p>数据仍来自 {@code ai_chat_messages} —— 该表现在是 {@code ChatMemoryStore} 的落库目标，
     * 因此「框架托管的记忆」与「页面回放的历史」天然是同一份数据。
     */
    @GetMapping("/history")
    public ApiResponse<List<AiChatMessage>> history(HttpServletRequest http) {
        Long userId = currentUserId(http);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        List<AiChatMessage> desc = chatMessageRepository.findTop20ByUserIdOrderByIdDesc(userId);
        List<AiChatMessage> asc = new ArrayList<>(desc);
        Collections.reverse(asc);
        return ApiResponse.ok(asc);
    }

    /**
     * 清空当前用户的全部 AI 记忆（对话历史 + 长期记忆）。
     */
    @DeleteMapping("/memory")
    public ApiResponse<Void> clearMemory(HttpServletRequest http) {
        Long userId = currentUserId(http);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        adminAssistantService.clearMemory(userId);
        return ApiResponse.ok();
    }

    /**
     * 查询当前用户的长期记忆（偏好/上下文键值对）。
     */
    @GetMapping("/memories")
    public ApiResponse<List<AiMemoryItem>> listMemories(HttpServletRequest http) {
        Long userId = currentUserId(http);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        List<AiMemory> list = adminAssistantService.listMemories(userId);
        List<AiMemoryItem> items = new ArrayList<>(list.size());
        for (AiMemory m : list) {
            items.add(new AiMemoryItem(
                    m.getMemKey(),
                    m.getMemValue(),
                    m.getUpdatedAt() == null ? null : m.getUpdatedAt().toString()));
        }
        return ApiResponse.ok(items);
    }

    /**
     * 写入/更新一条长期记忆。请求体 {@code {key, value}}。
     */
    @PutMapping("/memories")
    public ApiResponse<Void> saveMemory(@RequestBody Map<String, String> body, HttpServletRequest http) {
        Long userId = currentUserId(http);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        if (body == null || body.get("key") == null || body.get("key").isBlank()) {
            return ApiResponse.error(400, "记忆键 key 不能为空");
        }
        String key = body.get("key");
        String value = body.get("value") == null ? "" : body.get("value");
        try {
            adminAssistantService.saveMemory(userId, key, value);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 从鉴权拦截器注入的请求属性中取当前登录用户 ID。 */
    private Long currentUserId(HttpServletRequest http) {
        Object v = http.getAttribute(AuthInterceptor.ATTR_USER_ID);
        return v instanceof Long ? (Long) v : null;
    }

    /** 当前角色（拦截器实时查库得来，不信任 token claims）。 */
    private String currentRole(HttpServletRequest http) {
        Object v = http.getAttribute(AuthInterceptor.ATTR_ROLE);
        return v == null ? null : String.valueOf(v);
    }

    /** 当前权限码集合（拦截器按角色实时加载）。 */
    @SuppressWarnings("unchecked")
    private Set<String> currentPerms(HttpServletRequest http) {
        Object v = http.getAttribute(AuthInterceptor.ATTR_PERMS);
        return v instanceof Set<?> set ? (Set<String>) set : Set.of();
    }
}
