package com.zyc.copier_v0.modules.account.config.api;

import java.util.List;
import lombok.Data;

@Data
public class PlatformAccountDetailResponse {

    private Mt5AccountResponse account;
    private RiskRuleResponse riskRule;
    private AccountRelationsResponse relations;
    private List<SymbolMappingResponse> symbolMappings;
    private MasterShareConfigResponse shareConfig;
}
