package com.example.backend.common;

/**
 * 业务异常。抛出后由 {@link GlobalExceptionHandler} 统一转为响应。
 *
 * <h3>{@code subCode} 是什么</h3>
 * {@code code}（400/404/409/410/500）表达的是「HTTP 语义」这一层，粒度太粗：
 * 同为 409，前端需要区分「这个操作已经执行过了（好消息，卡片显示成功态）」和
 * 「刚有同事抢先处理了（坏消息，让用户刷新）」。因此本次<b>纯增量</b>新增
 * {@code subCode}（如 {@code DUPLICATE_ACTION} / {@code CONFLICT_STOCK_CHANGED}）。
 * <p>
 * 兼容承诺：两参构造与单参构造<b>原样保留</b>，其 {@code subCode} 为 null，
 * 序列化后该字段根本不出现 —— 所有历史抛点的响应体与改造前逐字节一致。
 * 只有本次新写的抛点才带 {@code subCode}。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    /** 细化错误码；null 表示「沿用旧的粗粒度语义」。 */
    private final String subCode;

    /** 旧构造：不带细化错误码。 */
    public BusinessException(int code, String message) {
        this(code, null, message);
    }

    /** 旧构造：不带细化错误码。 */
    public BusinessException(String message) {
        this(400, null, message);
    }

    /**
     * @param code    HTTP 语义码（400/403/404/409/410/500）
     * @param subCode 细化错误码，见 PRD ⑧；null 表示不细化
     * @param message 面向运营的中文提示
     */
    public BusinessException(int code, String subCode, String message) {
        super(message);
        this.code = code;
        this.subCode = subCode;
    }

    public int getCode() {
        return code;
    }

    public String getSubCode() {
        return subCode;
    }
}
