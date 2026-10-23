package com.example.backend.ai.assistant;

import com.example.backend.ai.config.AiModelConfig;
import com.example.backend.ai.prompt.AiPrompts;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * 订单风控「综合评分」的声明式契约。
 *
 * <h3>它是可降级环节</h3>
 * 风控的主体是本地规则引擎（金额异常、短时多单、敏感地址、首单大额、凌晨下单、零元单），
 * 纯本地计算、零外部依赖。本接口只负责在规则信号之上再做一次「综合判断」：
 * 给出 0-100 分、等级与处置建议。
 * <p>
 * 因此调用方 {@code RiskDetectionService} 把这里的异常<b>全部吞掉并降级为规则结果</b> ——
 * 模型不可用时风控能力不受影响，这也是原实现的既定设计，本次改造只换了底层调用方式。
 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = AiModelConfig.STRICT_MODEL
)
public interface RiskAnalyst {

    /**
     * 对一笔订单做综合风险判定。
     *
     * @param content 订单快照 + 规则引擎已识别的信号（已排版为结构化文本）
     * @return 模型原始输出，期望形如 {@code {"score":0,"level":"LOW","reason":"...","action":"放行"}}
     */
    @SystemMessage(AiPrompts.RISK_ANALYST)
    String assess(@UserMessage String content);
}
