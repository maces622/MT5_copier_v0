package com.zyc.copier_v0.modules.account.config.api;

import com.zyc.copier_v0.modules.account.config.domain.Mt5AccountRole;
import java.time.Instant;
import lombok.Data;

@Data
public class MasterShareAccountSummaryResponse {

    private Long masterAccountId;
    private String brokerName;
    private String serverName;
    private Long mt5Login;
    private Mt5AccountRole accountRole;
    private boolean shareEnabled;
    private boolean shareCodeConfigured;
    private String shareNote;
    private Instant rotatedAt;
    private Instant expiresAt;
    private Instant updatedAt;
}
