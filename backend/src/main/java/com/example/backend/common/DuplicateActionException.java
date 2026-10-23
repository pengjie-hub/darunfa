package com.example.backend.common;

/**
 * 「该操作已经执行过」的<b>成功语义</b>异常，携带上次执行的结果。
 *
 * <h3>为什么用异常，而不是让执行器返回一个包装对象</h3>
 * 执行器 {@code AdminActionExecutor.run(...)} 被 6 个既有控制器方法调用，签名是
 * {@code <T> T run(Command, Class<T>, Supplier<T>)}。若为了额外返回一个 {@code subCode}
 * 就把返回值改成 {@code Outcome<T>}，6 个写接口的方法体（以及它们的返回语句）都得跟着改，
 * 而这些方法正是「方法体整段原样搬进 lambda、一行不许动」的回归红线。
 * <p>
 * 走异常通道则<b>零改动</b>：执行器抛出本异常 → {@link GlobalExceptionHandler} 统一渲染成
 * {@code {code:0, subCode:"DUPLICATE_ACTION", message:"…", data:<上次的结果>}}。
 * 控制器里 {@code ApiResponse.ok(...)} 那一行永远不会被执行到，既有返回体结构也不受影响。
 *
 * <h3>为什么 {@code code = 0} 而不是 409</h3>
 * 「重复点击」在业务上<b>不是错误</b>：目标状态已经达成了，运营看到的不该是红色报错。
 * 前端据此把卡片渲染成「已完成」态并展示上次结果（设计文档验收 ⑥）。
 * 真正的并发争抢（另一个线程正在执行）才抛 409 {@code ACTION_IN_PROGRESS}。
 *
 * <p>{@code payload} 标记 {@code transient}：它是业务对象，不参与任何序列化/持久化路径，
 * 只在本进程内从执行器传到异常处理器。
 */
public class DuplicateActionException extends BusinessException {

    /** 上次执行的结果（可空：例如上次结果没落库成功，或本次动作无返回值）。 */
    private final transient Object payload;

    /**
     * @param message 面向运营的中文提示
     * @param payload 上次执行的结果对象；为 null 时响应体的 {@code data} 为 null
     */
    public DuplicateActionException(String message, Object payload) {
        super(0, "DUPLICATE_ACTION", message);
        this.payload = payload;
    }

    public Object getPayload() {
        return payload;
    }
}
