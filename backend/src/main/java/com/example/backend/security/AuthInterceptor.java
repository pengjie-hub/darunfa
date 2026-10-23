package com.example.backend.security;

import com.example.backend.entity.Role;
import com.example.backend.entity.RolePermission;
import com.example.backend.entity.User;
import com.example.backend.entity.UserRole;
import com.example.backend.repository.PermissionRepository;
import com.example.backend.repository.RolePermissionRepository;
import com.example.backend.repository.RoleRepository;
import com.example.backend.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 鉴权拦截器（基于 RBAC 的细粒度权限校验）：
 * <ul>
 *   <li>公开路径（登录/注册、商品与分类浏览、AI 能力）直接放行；</li>
 *   <li>其余路径要求携带合法 JWT，解析出 userId 后**实时查 DB** 拿最新 role / status；</li>
 *   <li>再根据 user.role → role.id → role_permissions → permission.code，加载该用户所有权限码；</li>
 *   <li>按方法/类上的 {@link RequireAdmin} / {@link RequirePermission} 注解校验：都满足才放行（AND 语义）。</li>
 * </ul>
 *
 * <p>关键：role 与 permission 都以 DB 为准。这样管理员把账号「降级」或「禁用」后，
 * 下一次请求即生效，不会因旧 token 仍带旧 role 而保留全部权限。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER_ID = "auth_userId";
    public static final String ATTR_ROLE = "auth_role";
    public static final String ATTR_PERMS = "auth_perms";

    /**
     * 允许访问 {@code /api/admin/**} 的后台角色。
     * <p>普通用户（USER）永远不在其中：App 端不调用任何 {@code /api/admin/**} 接口（已核对 ApiService 全部端点）。
     */
    private static final Set<String> CONSOLE_ROLES = Set.of("ADMIN", "OPS", "CS");

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;

    public AuthInterceptor(JwtUtil jwtUtil,
                           UserRepository userRepository,
                           RoleRepository roleRepository,
                           RolePermissionRepository rolePermissionRepository,
                           PermissionRepository permissionRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
    }

    private boolean isPublic(String path) {
        return path.startsWith("/api/auth/")
                || path.equals("/api/ping")
                || path.startsWith("/api/chat")
                || path.startsWith("/api/ai/")
                || path.startsWith("/api/categories")
                || path.startsWith("/error");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        String path = request.getRequestURI();

        // 商品/分类/促销浏览类 GET 请求公开（营销内容与商品一样面向所有访客）
        boolean browsePublic = "GET".equalsIgnoreCase(request.getMethod())
                && (path.equals("/api/products")
                    || path.matches("/api/products/\\d+")
                    || path.equals("/api/promotions")
                    || path.matches("/api/promotions/\\d+"));

        if (isPublic(path) || browsePublic) {
            return true;
        }

        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(response, "缺少登录凭证");
        }

        String token = auth.substring(7).trim();
        try {
            Claims claims = jwtUtil.parse(token);
            Long userId = jwtUtil.getUserId(claims);
            request.setAttribute(ATTR_USER_ID, userId);

            // 实时查 DB 获取最新 role / status，不信任 token claims 中的 role
            Optional<User> opt = userRepository.findById(userId);
            if (opt.isEmpty()) {
                return unauthorized(response, "登录凭证无效或已过期");
            }
            User user = opt.get();
            if (user.getStatus() == null || user.getStatus() == 0) {
                return unauthorized(response, "账号已禁用");
            }
            String role = user.getRole() == null ? UserRole.USER.name() : user.getRole().name();
            request.setAttribute(ATTR_ROLE, role);

            // 兜底闸门：后台接口只对后台角色开放，普通用户永远不得触达 /api/admin/**。
            // 与逐方法的 @RequireAdmin/@RequirePermission 互补 —— 后者是「细粒度授权」，本闸门保证
            // 「漏标注解」不会变成漏洞：AdminController 当年为了细粒度而移除了类级 @RequireAdmin，
            // 结果 /admin/dashboard 与 /admin/dashboard/charts 两个方法谁都没标，任何已登录用户
            // （包括 App 里的普通买家）都能直接读到营业额/订单数/支付分布等经营数据。
            // 加了这条之后，今后新增后台方法忘记标注解只会「过宽地开放给后台同事」，不会再漏给普通用户。
            if (path.startsWith("/api/admin/") && !CONSOLE_ROLES.contains(role)) {
                return forbidden(response, "无权访问后台接口");
            }

            // 加载该用户当前角色的所有权限码（空集表示无任何权限）
            Set<String> perms = loadPermissionCodes(role);
            request.setAttribute(ATTR_PERMS, perms);

            // 解析方法/类上的鉴权注解
            RequireAdmin requireAdmin = handlerMethod.getMethodAnnotation(RequireAdmin.class);
            if (requireAdmin == null) {
                requireAdmin = handlerMethod.getBeanType().getAnnotation(RequireAdmin.class);
            }
            RequirePermission requirePerm = handlerMethod.getMethodAnnotation(RequirePermission.class);
            if (requirePerm == null) {
                requirePerm = handlerMethod.getBeanType().getAnnotation(RequirePermission.class);
            }

            // 校验 RequireAdmin：必须 ADMIN 角色
            if (requireAdmin != null && !UserRole.ADMIN.name().equals(role)) {
                return forbidden(response, "需要管理员权限");
            }
            // 校验 RequirePermission：任一权限码在用户权限集合中即满足（OR 语义）
            if (requirePerm != null) {
                boolean ok = false;
                for (String code : requirePerm.value()) {
                    if (perms.contains(code)) { ok = true; break; }
                }
                if (!ok) {
                    return forbidden(response, "权限不足：需要 " + String.join(" / ", requirePerm.value()));
                }
            }
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(response, "登录凭证无效或已过期");
        }
    }

    /** 根据角色名加载其全部权限码（"product:view" 等）；空集表示无任何权限。 */
    private Set<String> loadPermissionCodes(String roleName) {
        Set<String> codes = new HashSet<>();
        Optional<Role> opt = roleRepository.findByCode(roleName);
        if (opt.isEmpty()) return codes;
        List<RolePermission> rps = rolePermissionRepository.findAllByRoleId(opt.get().getId());
        if (rps.isEmpty()) return codes;
        // 收集全部 permission id，一次查 Permission 表拿 code
        List<Long> pids = rps.stream().map(RolePermission::getPermissionId).toList();
        var perms = permissionRepository.findAllById(pids);
        for (var p : perms) {
            if (p.getCode() != null) codes.add(p.getCode());
        }
        return codes;
    }

    private boolean unauthorized(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(
                ("{\"code\":401,\"message\":\"" + msg + "\",\"data\":null}")
                        .getBytes(StandardCharsets.UTF_8));
        return false;
    }

    private boolean forbidden(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(
                ("{\"code\":403,\"message\":\"" + msg + "\",\"data\":null}")
                        .getBytes(StandardCharsets.UTF_8));
        return false;
    }
}
