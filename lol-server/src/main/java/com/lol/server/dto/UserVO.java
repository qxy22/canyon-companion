package com.lol.server.dto;

/**
 * 返回给前端的用户信息（不含密码）。
 */
public class UserVO {

    private Long id;
    private String username;
    private String nickname;

    public UserVO() {
    }

    public UserVO(Long id, String username, String nickname) {
        this.id = id;
        this.username = username;
        this.nickname = nickname;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
