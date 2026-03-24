package com.zyc.copier_v0.modules.account.config.cache;

import java.math.BigDecimal;
import java.util.Map;
import lombok.Data;

@Data
public class FollowerRiskCacheSnapshot {

    private Long accountId;
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
    private boolean followTpSl = true;
    private boolean reverseFollow;
    private Map<String, String> symbolMappings;
}
