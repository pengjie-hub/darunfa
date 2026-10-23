package com.example.myapplication.remote;

import android.content.Context;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Retrofit 单例：构建 {@link ApiService}，并为每个请求自动附加
 * {@code Authorization: Bearer <token>} 鉴权头。
 */
public final class RetrofitClient {

    private static volatile ApiService sApiService;

    private RetrofitClient() {
    }

    public static synchronized void init(Context context) {
        SessionManager.init(context);
        if (sApiService != null) return;

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    String token = SessionManager.getToken();
                    okhttp3.Request.Builder builder = chain.request().newBuilder();
                    if (token != null && !token.isEmpty()) {
                        builder.header("Authorization", "Bearer " + token);
                    }
                    return chain.proceed(builder.build());
                })
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(ApiConfig.BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        sApiService = retrofit.create(ApiService.class);
    }

    public static ApiService api() {
        if (sApiService == null) {
            throw new IllegalStateException("RetrofitClient 尚未初始化，请先调用 init(context)");
        }
        return sApiService;
    }
}
