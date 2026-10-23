package com.example.myapplication.remote;

/**
 * 异步接口回调。
 */
public interface ApiCallback<T> {

    void onSuccess(T data);

    void onError(int code, String message);
}
