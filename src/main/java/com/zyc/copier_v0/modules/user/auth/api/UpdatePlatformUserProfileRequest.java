package com.zyc.copier_v0.modules.user.auth.api;

import javax.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdatePlatformUserProfileRequest {

    @Size(max = 128)
    private String displayName;

    @Size(max = 128)
    private String currentPassword;

    @Size(min = 6, max = 128)
    private String newPassword;
}
