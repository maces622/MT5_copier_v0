package com.zyc.copier_v0.modules.account.config.api;

import lombok.Data;

@Data
public class RelationAccountSummaryResponse {

    private Long id;
    private String brokerName;
    private String serverName;
    private Long mt5Login;
    private String accountRole;
    private String status;
}
