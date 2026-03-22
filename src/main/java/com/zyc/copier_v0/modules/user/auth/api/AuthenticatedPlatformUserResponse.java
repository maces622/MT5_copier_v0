package com.zyc.copier_v0.modules.user.auth.api;

import com.zyc.copier_v0.modules.user.auth.domain.PlatformUserRole;
import com.zyc.copier_v0.modules.user.auth.domain.PlatformUserStatus;
import java.time.Instant;
import lombok.Data;

@Data
public class AuthenticatedPlatformUserResponse {

    private Long id;
    private String platformId;
    private String username;
    private String shareId;
    private String displayName;
    private PlatformUserStatus status;
    private PlatformUserRole role;
    private Instant createdAt;
    private Instant updatedAt;
}
