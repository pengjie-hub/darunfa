package com.example.backend.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 重复执行（幂等命中）：<b>业务上算成功</b>，因此 {@code code=0}，
     * 只用 {@code subCode=DUPLICATE_ACTION} 告诉前端「别再点了，这是上次的结果」。
     *
     * <p>必须排在 {@link #handleBusiness} 之前被 Spring 选中（它按最具体类型匹配，
     * 与声明顺序无关），否则会被父类处理器吞掉，前端就拿不到 {@code subCode} 与上次结果。
     *
     * @see DuplicateActionException
     */
    @ExceptionHandler(DuplicateActionException.class)
    public ResponseEntity<ApiResponse<Object>> handleDuplicate(DuplicateActionException e) {
        return ResponseEntity.ok(
                new ApiResponse<>(0, e.getSubCode(), e.getMessage(), e.getPayload()));
    }

    /**
     * 业务异常：HTTP 200，通过 code 区分。
     * <p>
     * 本次把 {@link BusinessException#getSubCode()} 透传进响应体：为 null 时
     * {@code ApiResponse} 上的 {@code @JsonInclude(NON_NULL)} 会让该字段不出现，
     * 因此所有历史抛点的响应体保持不变。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        return ResponseEntity.ok(ApiResponse.error(e.getCode(), e.getSubCode(), e.getMessage()));
    }

    /** 参数校验失败。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.ok(ApiResponse.error(400, msg));
    }

    /** 静态资源/路径不存在（如访问 /favicon.ico、未定义的接口路径等）：返回 404 而非 500。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, "接口不存在: " + e.getResourcePath()));
    }

    /** 其它未处理异常。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "服务器内部错误"));
    }
}
