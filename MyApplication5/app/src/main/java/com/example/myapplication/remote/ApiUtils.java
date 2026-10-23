package com.example.myapplication.remote;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Retrofit 调用的通用封装：把 {@code Call<ApiResponse<T>>} 统一转换为 {@link ApiCallback<T>}。
 */
public final class ApiUtils {

    private ApiUtils() {
    }

    public static <T> void enqueue(Call<ApiResponse<T>> call, final ApiCallback<T> cb) {
        call.enqueue(new Callback<ApiResponse<T>>() {
            @Override
            public void onResponse(Call<ApiResponse<T>> c, Response<ApiResponse<T>> response) {
                ApiResponse<T> body = response.body();
                if (body != null && body.isSuccess()) {
                    cb.onSuccess(body.data);
                } else {
                    int code = body != null ? body.code : response.code();
                    String msg = (body != null && body.message != null)
                            ? body.message : "请求失败(" + response.code() + ")";
                    cb.onError(code, msg);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<T>> c, Throwable t) {
                cb.onError(-1, "网络错误，请确认后端已启动");
            }
        });
    }
}
