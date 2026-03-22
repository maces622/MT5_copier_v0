package com.zyc.copier_v0.modules.user.auth.api;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterPlatformUserRequest {

    @NotBlank
    @Size(max = 128)
    private String username;

    @NotBlank
    @Size(min = 6, max = 128)
    private String password;

    @Size(max = 128)
    private String displayName;
}
