package com.zyc.copier_v0.modules.user.auth.api;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginPlatformUserRequest {

    @NotBlank
    @Size(max = 128)
    private String login;

    @NotBlank
    @Size(max = 128)
    private String password;
}
