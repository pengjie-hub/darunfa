package com.example.myapplication.remote;

/**
 * 与后端 {@code ApiResponse} 对应的统一响应体。
 * {@code code == 0} 表示成功，非 0 为业务错误码（{@code message} 说明原因）。
 */
public class ApiResponse<T> {

    public int code;
    public String message;
    public T data;

    public boolean isSuccess() {
        return code == 0;
    }
}
