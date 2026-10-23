package com.example.backend.ai.memory;

import com.example.backend.ai.config.AiMemoryProperties;
import com.example.backend.entity.AiMemory;
import com.example.backend.repository.AiMemoryRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 长期记忆（key-value 偏好 / 关键上下文）读写。
 *
 * <h3>与会话记忆的分工</h3>
 * <ul>
 *   <li>{@link JpaChatMemoryStore} —— <b>逐条原始对话</b>，交给模型的滑动窗口（表 {@code ai_chat_messages}）；</li>
 *   <li>本类 —— <b>提炼后的结论</b>，例如「口味：不吃香菜」，由模型通过
 *       {@code @Tool remember_user_fact} 主动写入（表 {@code ai_memories}），
 *       每次请求再以文本形式注入 system 提示，实现「跨会话记得你」。</li>
 * </ul>
 *
 * <p>LangChain4j 原生只有「消息级记忆」抽象，没有「提炼式长期记忆」的对应物，
 * 因此这一层保留为自定义组件 —— 这也是本项目在框架能力之外<b>刻意保留</b>的一块领域逻辑。</p>
 */
@Component
public class LongTermMemoryStore {

    private final AiMemoryRepository repository;
    private final AiMemoryProperties props;

    public LongTermMemoryStore(AiMemoryRepository repository, AiMemoryProperties props) {
        this.repository = repository;
        this.props = props;
    }

    /**
     * 写入 / 覆盖一条长期记忆（同一 userId + key 唯一，存在即更新）。
     */
    public void save(Long userId, String key, String value) {
        if (userId == null) return;
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("记忆键 key 不能为空");
        }
        String k = key.trim();
        Optional<AiMemory> existing = repository.findByUserIdAndMemKey(userId, k);
        AiMemory memory;
        if (existing.isPresent()) {
            memory = existing.get();
            memory.setMemValue(value == null ? "" : value);
            memory.setUpdatedAt(LocalDateTime.now());
        } else {
            memory = new AiMemory();
            memory.setUserId(userId);
            memory.setMemKey(k);
            memory.setMemValue(value == null ? "" : value);
        }
        repository.save(memory);
    }

    /** 列出某用户全部长期记忆（按更新时间降序）。 */
    public List<AiMemory> list(Long userId) {
        if (userId == null) return List.of();
        List<AiMemory> list = repository.findByUserIdOrderByUpdatedAtDesc(userId);
        return list == null ? List.of() : list;
    }

    /** 清空某用户全部长期记忆。 */
    public void clear(Long userId) {
        if (userId != null) repository.deleteByUserId(userId);
    }

    /**
     * 构造注入 system 提示的「用户偏好与关键上下文」文本；无记忆时返回空串。
     *
     * <p>返回的文本会作为 {@code {{memory}}} 变量塞进提示词模板，
     * 因此这里自带前导换行与段落标题，保证与提示词正文自然衔接。</p>
     */
    public String buildContext(Long userId) {
        List<AiMemory> memories = list(userId);
        if (memories.isEmpty()) return "";

        int maxItems = props.getLongTermMaxItems();
        int maxValueChars = props.getLongTermMaxValueChars();

        StringBuilder sb = new StringBuilder("\n\n[记忆中的用户偏好与关键上下文]\n");
        int n = 0;
        for (AiMemory memory : memories) {
            if (maxItems > 0 && n >= maxItems) break;
            String value = memory.getMemValue() == null ? "" : memory.getMemValue().trim();
            if (maxValueChars > 0 && value.length() > maxValueChars) {
                value = value.substring(0, maxValueChars);
            }
            sb.append("- ").append(displayKey(memory.getMemKey())).append(": ").append(value).append("\n");
            n++;
        }
        return sb.toString();
    }

    /** 去掉 {@code pref:} / {@code context:} 前缀，让注入文本对模型更友好。 */
    private static String displayKey(String key) {
        if (key == null) return "";
        if (key.startsWith("pref:")) return key.substring("pref:".length());
        if (key.startsWith("context:")) return key.substring("context:".length());
        return key;
    }
}
