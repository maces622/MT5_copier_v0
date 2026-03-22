package com.zyc.copier_v0.modules.account.config.api;

import java.time.Instant;
import lombok.Data;

@Data
public class MasterShareConfigResponse {

    private Long id;
    private Long masterAccountId;
    private boolean shareEnabled;
    private boolean shareCodeConfigured;
    private String shareNote;
    private Instant rotatedAt;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;
}
