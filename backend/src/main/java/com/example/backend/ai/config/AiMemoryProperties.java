package com.example.backend.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 记忆层参数（绑定 {@code ai.memory.*}）。
 * <p>
 * 会话记忆与长期记忆的容量/截断策略集中在这里，便于按成本与效果调参，
 * 而不必翻找散落在服务里的魔法数字。
 */
@ConfigurationProperties(prefix = "ai.memory")
public class AiMemoryProperties {

    /** 滑动窗口大小（按「条」计，10 条 ≈ 5 轮问答）。超出部分不再注入模型，但数据库仍完整保留。 */
    private int maxMessages = 10;

    /** 单条历史注入模型前的最大字符数，防止长回答把 prompt 撑爆。 */
    private int maxContentChars = 500;

    /** 长期记忆（{@code ai_memories}）注入 system 提示的条数上限。 */
    private int longTermMaxItems = 20;

    /** 单条长期记忆值注入前的最大字符数。 */
    private int longTermMaxValueChars = 300;

    public int getMaxMessages() { return maxMessages; }
    public void setMaxMessages(int maxMessages) { this.maxMessages = maxMessages; }

    public int getMaxContentChars() { return maxContentChars; }
    public void setMaxContentChars(int maxContentChars) { this.maxContentChars = maxContentChars; }

    public int getLongTermMaxItems() { return longTermMaxItems; }
    public void setLongTermMaxItems(int longTermMaxItems) { this.longTermMaxItems = longTermMaxItems; }

    public int getLongTermMaxValueChars() { return longTermMaxValueChars; }
    public void setLongTermMaxValueChars(int longTermMaxValueChars) { this.longTermMaxValueChars = longTermMaxValueChars; }
}
