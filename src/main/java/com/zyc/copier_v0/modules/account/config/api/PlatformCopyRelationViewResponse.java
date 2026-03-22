package com.zyc.copier_v0.modules.account.config.api;

import lombok.Data;

@Data
public class PlatformCopyRelationViewResponse {

    private CopyRelationResponse relation;
    private RelationAccountSummaryResponse masterAccount;
    private RelationAccountSummaryResponse followerAccount;
    private boolean currentUserOwnsMaster;
    private boolean currentUserOwnsFollower;
}
