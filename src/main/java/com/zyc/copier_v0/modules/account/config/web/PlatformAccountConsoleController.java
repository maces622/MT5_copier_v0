package com.zyc.copier_v0.modules.account.config.web;

import com.zyc.copier_v0.modules.account.config.api.AccountRelationsResponse;
import com.zyc.copier_v0.modules.account.config.api.Mt5AccountResponse;
import com.zyc.copier_v0.modules.account.config.api.PlatformAccountDetailResponse;
import com.zyc.copier_v0.modules.account.config.api.PlatformCopyRelationViewResponse;
import com.zyc.copier_v0.modules.account.config.api.RiskRuleResponse;
import com.zyc.copier_v0.modules.account.config.api.SymbolMappingResponse;
import com.zyc.copier_v0.modules.account.config.service.PlatformAccountConsoleService;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PlatformAccountConsoleController {

    private final PlatformAccountConsoleService platformAccountConsoleService;

    public PlatformAccountConsoleController(PlatformAccountConsoleService platformAccountConsoleService) {
        this.platformAccountConsoleService = platformAccountConsoleService;
    }

    @GetMapping("/me/accounts")
    public List<Mt5AccountResponse> listMyAccounts(HttpServletRequest request) {
        return platformAccountConsoleService.listMyAccounts(request);
    }

    @GetMapping("/me/copy-relations")
    public List<PlatformCopyRelationViewResponse> listMyCopyRelations(HttpServletRequest request) {
        return platformAccountConsoleService.listMyCopyRelations(request);
    }

    @GetMapping("/accounts/{accountId}")
    public Mt5AccountResponse getAccount(@PathVariable Long accountId, HttpServletRequest request) {
        return platformAccountConsoleService.getAccount(accountId, request);
    }

    @GetMapping("/accounts/{accountId}/detail")
    public PlatformAccountDetailResponse getAccountDetail(@PathVariable Long accountId, HttpServletRequest request) {
        return platformAccountConsoleService.getAccountDetail(accountId, request);
    }

    @GetMapping("/accounts/{accountId}/risk-rule")
    public RiskRuleResponse getRiskRule(@PathVariable Long accountId, HttpServletRequest request) {
        return platformAccountConsoleService.getRiskRule(accountId, request);
    }

    @GetMapping("/accounts/{accountId}/relations")
    public AccountRelationsResponse getRelations(@PathVariable Long accountId, HttpServletRequest request) {
        return platformAccountConsoleService.getRelations(accountId, request);
    }

    @GetMapping("/accounts/{accountId}/symbol-mappings")
    public List<SymbolMappingResponse> getSymbolMappings(@PathVariable Long accountId, HttpServletRequest request) {
        return platformAccountConsoleService.getSymbolMappings(accountId, request);
    }
}
