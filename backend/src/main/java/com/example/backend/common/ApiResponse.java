package com.example.backend.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一 API 响应包装。
 * <p>
 * 约定：HTTP 状态码正常为 200；业务结果由 {@code code} 区分：
 * {@code 0} 成功，非 0 为业务错误码（{@code message} 说明原因）。
 * 鉴权失败由拦截器直接返回 HTTP 401/403。
 *
 * <h3>为什么类上加 {@code @JsonInclude(NON_NULL)}，却又给三个老字段标 {@code ALWAYS}</h3>
 * 本次新增 {@link #subCode}（细化错误码，如 {@code DUPLICATE_ACTION}），前端用它做差异化 UI。
 * 但 {@code subCode} 只对错误响应有意义，<b>成功响应里多出一个 {@code "subCode":null} 会污染
 * 全部既有接口的输出</b>，前台商城 App 也得多解析一个无意义字段。
 * <p>
 * 类级 {@code NON_NULL} 能把 null 的 {@code subCode} 抹掉，但它同样会抹掉 {@code "data":null}
 * （{@code ApiResponse.ok()} 就返回 null data）—— 那就是对既有响应体的破坏。
 * 因此 {@code code / message / data} 三个字段显式标 {@code ALWAYS}，把它们的输出行为
 * <b>逐字节还原成改造前的样子</b>：老响应体一字不差，只有 {@code subCode} 在非空时才出现。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private int code;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String message;

    /** 细化业务错误码（见 PRD ⑧）；成功响应为 null，此时不参与序列化。 */
    private String subCode;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private T data;

    public ApiResponse() {
    }

    /** 旧构造：{@code subCode = null}，行为与改造前完全一致。 */
    public ApiResponse(int code, String message, T data) {
        this(code, null, message, data);
    }

    public ApiResponse(int code, String subCode, String message, T data) {
        this.code = code;
        this.subCode = subCode;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(0, "ok", null);
    }

    /** 旧工厂：不带细化错误码。 */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    /**
     * 带细化错误码的错误响应。
     *
     * @param code    HTTP 语义码（400/404/409/410/500…），沿用既有约定放在 body 里
     * @param subCode 细化错误码；为 null 时该字段不出现在 JSON 中
     * @param message 面向运营的中文提示
     */
    public static <T> ApiResponse<T> error(int code, String subCode, String message) {
        return new ApiResponse<>(code, subCode, message, null);
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getSubCode() { return subCode; }
    public void setSubCode(String subCode) { this.subCode = subCode; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
