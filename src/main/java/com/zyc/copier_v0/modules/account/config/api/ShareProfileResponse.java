package com.zyc.copier_v0.modules.account.config.api;

import java.util.List;
import lombok.Data;

@Data
public class ShareProfileResponse {

    private Long userId;
    private String platformId;
    private String shareId;
    private String displayName;
    private List<MasterShareAccountSummaryResponse> masterAccounts;
}
