package com.zyc.copier_v0.modules.account.config.api;

import java.time.Instant;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
public class SaveMasterShareConfigRequest {

    private Boolean shareEnabled;

    @Size(max = 128)
    private String shareCode;

    @Size(max = 255)
    private String shareNote;

    private Instant expiresAt;
}
