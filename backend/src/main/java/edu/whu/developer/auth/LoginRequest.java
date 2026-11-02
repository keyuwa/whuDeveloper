package edu.whu.developer.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "请填写用户名") String username,
        @NotBlank(message = "请填写密码") String password
) {
}
