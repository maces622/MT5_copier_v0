package com.zyc.copier_v0.modules.account.config.web;

import com.zyc.copier_v0.modules.account.config.api.CopyRelationResponse;
import com.zyc.copier_v0.modules.account.config.api.JoinByShareRequest;
import com.zyc.copier_v0.modules.account.config.api.MasterShareConfigResponse;
import com.zyc.copier_v0.modules.account.config.api.SaveMasterShareConfigRequest;
import com.zyc.copier_v0.modules.account.config.api.ShareProfileResponse;
import com.zyc.copier_v0.modules.account.config.service.AccountSharingService;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AccountShareController {

    private final AccountSharingService accountSharingService;

    public AccountShareController(AccountSharingService accountSharingService) {
        this.accountSharingService = accountSharingService;
    }

    @GetMapping("/me/share-profile")
    public ShareProfileResponse getCurrentShareProfile(HttpServletRequest request) {
        return accountSharingService.getCurrentShareProfile(request);
    }

    @PostMapping("/accounts/{accountId}/share-config")
    public MasterShareConfigResponse createShareConfig(
            @PathVariable Long accountId,
            @Valid @RequestBody SaveMasterShareConfigRequest request,
            HttpServletRequest servletRequest
    ) {
        return accountSharingService.saveShareConfig(accountId, request, servletRequest);
    }

    @PutMapping("/accounts/{accountId}/share-config")
    public MasterShareConfigResponse updateShareConfig(
            @PathVariable Long accountId,
            @Valid @RequestBody SaveMasterShareConfigRequest request,
            HttpServletRequest servletRequest
    ) {
        return accountSharingService.saveShareConfig(accountId, request, servletRequest);
    }

    @PostMapping("/copy-relations/join-by-share")
    public CopyRelationResponse joinByShare(
            @Valid @RequestBody JoinByShareRequest request,
            HttpServletRequest servletRequest
    ) {
        return accountSharingService.joinByShare(request, servletRequest);
    }
}
