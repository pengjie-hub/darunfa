package com.example.myapplication.remote;

/**
 * 后端服务地址配置。
 * <p>
 * 说明：
 * <ul>
 *   <li>Android 模拟器访问宿主机本机后端用 {@code 10.0.2.2}；</li>
 *   <li>真机调试需改为宿主机局域网 IP，例如 {@code http://192.168.1.100:8080/}。</li>
 * </ul>
 */
public final class ApiConfig {

    private ApiConfig() {
    }

    public static final String BASE_URL = "http://10.0.2.2:8080/";
}
