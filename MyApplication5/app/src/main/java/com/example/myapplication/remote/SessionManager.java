package com.example.myapplication.remote;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 登录会话管理：保存 / 读取 JWT 令牌与当前用户 ID。
 * 令牌同时缓存在内存与 SharedPreferences，供 OkHttp 拦截器读取。
 */
public final class SessionManager {

    private static final String PREFS = "session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER_ID = "user_id";

    private static volatile String sToken;
    private static volatile long sUserId = -1L;
    private static volatile boolean sInited = false;

    private static SharedPreferences sp;

    private SessionManager() {
    }

    public static synchronized void init(Context context) {
        if (sInited) return;
        sp = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sToken = sp.getString(KEY_TOKEN, null);
        sUserId = sp.getLong(KEY_USER_ID, -1L);
        sInited = true;
    }

    public static void save(String token, long userId) {
        sToken = token;
        sUserId = userId;
        if (sp != null) {
            sp.edit().putString(KEY_TOKEN, token).putLong(KEY_USER_ID, userId).apply();
        }
    }

    public static String getToken() {
        return sToken;
    }

    public static long getUserId() {
        return sUserId;
    }

    public static boolean isLoggedIn() {
        return sToken != null && !sToken.isEmpty();
    }

    public static void clear() {
        sToken = null;
        sUserId = -1L;
        if (sp != null) {
            sp.edit().clear().apply();
        }
    }
}
