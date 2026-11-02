package com.lol.lcu;

/**
 * 一次 LCU 会话的凭证：端口 + 认证 token。
 *
 * @param port  客户端本地接口端口
 * @param token remoting-auth-token，Basic 认证里作为密码使用
 */
public record LcuCredentials(int port, String token) {

    /** 本地接口根地址，例如 https://127.0.0.1:53515 */
    public String baseUrl() {
        return "https://127.0.0.1:" + port;
    }
}
