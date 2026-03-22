package com.zyc.copier_v0.modules.account.config.api;

import com.zyc.copier_v0.modules.account.config.domain.CopyMode;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
public class JoinByShareRequest {

    @NotNull
    private Long followerAccountId;

    @NotBlank
    @Size(max = 32)
    private String shareId;

    @NotBlank
    @Size(max = 128)
    private String shareCode;

    @NotNull
    private CopyMode copyMode = CopyMode.BALANCE_RATIO;

    private Integer priority = 100;
}
