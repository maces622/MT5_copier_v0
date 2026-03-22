package com.zyc.copier_v0.modules.account.config.api;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveMySymbolMappingRequest {

    @NotBlank
    private String masterSymbol;

    @NotBlank
    private String followerSymbol;
}
