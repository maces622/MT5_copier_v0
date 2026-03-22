package com.zyc.copier_v0.modules.account.config.web;

import com.zyc.copier_v0.modules.account.config.api.BindMyMt5AccountRequest;
import com.zyc.copier_v0.modules.account.config.api.CopyRelationResponse;
import com.zyc.copier_v0.modules.account.config.api.CreateCopyRelationRequest;
import com.zyc.copier_v0.modules.account.config.api.Mt5AccountResponse;
import com.zyc.copier_v0.modules.account.config.api.RiskRuleResponse;
import com.zyc.copier_v0.modules.account.config.api.SaveMyRiskRuleRequest;
import com.zyc.copier_v0.modules.account.config.api.SaveMySymbolMappingRequest;
import com.zyc.copier_v0.modules.account.config.api.SymbolMappingResponse;
import com.zyc.copier_v0.modules.account.config.api.UpdateCopyRelationRequest;
import com.zyc.copier_v0.modules.account.config.service.PlatformAccountMutationService;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class PlatformAccountMutationController {

    private final PlatformAccountMutationService platformAccountMutationService;

    public PlatformAccountMutationController(PlatformAccountMutationService platformAccountMutationService) {
        this.platformAccountMutationService = platformAccountMutationService;
    }

    @PostMapping("/accounts")
    public Mt5AccountResponse bindMyAccount(
            @Valid @RequestBody BindMyMt5AccountRequest request,
            HttpServletRequest servletRequest
    ) {
        return platformAccountMutationService.bindMyAccount(request, servletRequest);
    }

    @PostMapping("/accounts/{accountId}/risk-rule")
    public RiskRuleResponse saveMyRiskRule(
            @PathVariable Long accountId,
            @RequestBody SaveMyRiskRuleRequest request,
            HttpServletRequest servletRequest
    ) {
        return platformAccountMutationService.saveMyRiskRule(accountId, request, servletRequest);
    }

    @PostMapping("/copy-relations")
    public CopyRelationResponse createMyCopyRelation(
            @Valid @RequestBody CreateCopyRelationRequest request,
            HttpServletRequest servletRequest
    ) {
        return platformAccountMutationService.createMyCopyRelation(request, servletRequest);
    }

    @PutMapping("/copy-relations/{relationId}")
    public CopyRelationResponse updateMyCopyRelation(
            @PathVariable Long relationId,
            @RequestBody UpdateCopyRelationRequest request,
            HttpServletRequest servletRequest
    ) {
        return platformAccountMutationService.updateMyCopyRelation(relationId, request, servletRequest);
    }

    @DeleteMapping("/copy-relations/{relationId}")
    public void deleteMyCopyRelation(
            @PathVariable Long relationId,
            HttpServletRequest servletRequest
    ) {
        platformAccountMutationService.deleteMyCopyRelation(relationId, servletRequest);
    }

    @PostMapping("/accounts/{accountId}/symbol-mappings")
    public SymbolMappingResponse saveMySymbolMapping(
            @PathVariable Long accountId,
            @Valid @RequestBody SaveMySymbolMappingRequest request,
            HttpServletRequest servletRequest
    ) {
        return platformAccountMutationService.saveMySymbolMapping(accountId, request, servletRequest);
    }
}
