package com.lol.lcu;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;

/**
 * 封装对 LCU 本地接口的 HTTPS 调用。
 *
 * LCU 使用自签名证书，因此这里创建一个"信任所有证书"的 SSLContext。
 * 认证方式是 Basic Auth：用户名固定为 riot，密码是 remoting-auth-token。
 */
public final class LcuClient {

    private final HttpClient http;
    private final String baseUrl;
    private final String authHeader;

    public LcuClient(LcuCredentials credentials) {
        this.baseUrl = credentials.baseUrl();
        this.authHeader = "Basic " + Base64.getEncoder()
                .encodeToString(("riot:" + credentials.token()).getBytes());
        this.http = HttpClient.newBuilder()
                .sslContext(trustAllSslContext())
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /** 发起 GET 请求并返回响应体文本。 */
    public String get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Authorization", authHeader)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + " on " + path);
        }
        return response.body();
    }

    private static SSLContext trustAllSslContext() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }
                    }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, new SecureRandom());
            return ctx;
        } catch (Exception e) {
            throw new RuntimeException("无法初始化信任所有证书的 SSLContext", e);
        }
    }
}
