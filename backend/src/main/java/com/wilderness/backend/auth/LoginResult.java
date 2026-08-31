package com.wilderness.backend.auth;

import com.wilderness.backend.dto.UserDTO;

/**
 * 登录/注册成功的结果：会话 token（写 Cookie）+ 当前用户信息。
 */
public record LoginResult(String token, UserDTO user) {
}
