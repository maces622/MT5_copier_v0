package com.zyc.copier_v0.modules.account.config.api;

import com.zyc.copier_v0.modules.account.config.domain.AccountStatus;
import com.zyc.copier_v0.modules.account.config.domain.Mt5AccountRole;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BindMyMt5AccountRequest {

    @NotBlank
    private String brokerName;

    @NotBlank
    private String serverName;

    @NotNull
    private Long mt5Login;

    private String credential;

    @NotNull
    private Mt5AccountRole accountRole;

    private AccountStatus status = AccountStatus.ACTIVE;
}
