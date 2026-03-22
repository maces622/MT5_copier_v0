package com.zyc.copier_v0.modules.account.config.api;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class SaveMyRiskRuleRequest {

    private BigDecimal maxLot;
    private BigDecimal fixedLot;
    private BigDecimal balanceRatio;
    private Integer maxSlippagePoints;
    private BigDecimal maxSlippagePips;
    private BigDecimal maxSlippagePrice;
    private BigDecimal maxDailyLoss;
    private BigDecimal maxDrawdownPct;
    private String allowedSymbols;
    private String blockedSymbols;
    private Boolean followTpSl;
    private Boolean reverseFollow;
}
