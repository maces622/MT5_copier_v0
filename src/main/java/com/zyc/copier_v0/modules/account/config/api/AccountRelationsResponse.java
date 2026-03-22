package com.zyc.copier_v0.modules.account.config.api;

import java.util.List;
import lombok.Data;

@Data
public class AccountRelationsResponse {

    private Long accountId;
    private List<CopyRelationResponse> masterRelations;
    private List<CopyRelationResponse> followerRelations;
}
