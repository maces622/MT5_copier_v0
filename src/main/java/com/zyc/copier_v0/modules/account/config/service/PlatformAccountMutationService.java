package com.zyc.copier_v0.modules.account.config.service;

import com.zyc.copier_v0.modules.account.config.api.BindMt5AccountRequest;
import com.zyc.copier_v0.modules.account.config.api.BindMyMt5AccountRequest;
import com.zyc.copier_v0.modules.account.config.api.CopyRelationResponse;
import com.zyc.copier_v0.modules.account.config.api.CreateCopyRelationRequest;
import com.zyc.copier_v0.modules.account.config.api.Mt5AccountResponse;
import com.zyc.copier_v0.modules.account.config.api.RiskRuleResponse;
import com.zyc.copier_v0.modules.account.config.api.SaveMyRiskRuleRequest;
import com.zyc.copier_v0.modules.account.config.api.SaveMySymbolMappingRequest;
import com.zyc.copier_v0.modules.account.config.api.SaveRiskRuleRequest;
import com.zyc.copier_v0.modules.account.config.api.SaveSymbolMappingRequest;
import com.zyc.copier_v0.modules.account.config.api.SymbolMappingResponse;
import com.zyc.copier_v0.modules.account.config.api.UpdateCopyRelationRequest;
import com.zyc.copier_v0.modules.account.config.entity.CopyRelationEntity;
import com.zyc.copier_v0.modules.account.config.entity.Mt5AccountEntity;
import com.zyc.copier_v0.modules.account.config.repository.CopyRelationRepository;
import com.zyc.copier_v0.modules.account.config.repository.Mt5AccountRepository;
import com.zyc.copier_v0.modules.user.auth.domain.PlatformUserRole;
import com.zyc.copier_v0.modules.user.auth.entity.PlatformUserEntity;
import com.zyc.copier_v0.modules.user.auth.service.PlatformAuthService;
import javax.persistence.EntityNotFoundException;
import javax.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAccountMutationService {

    private final PlatformAuthService platformAuthService;
    private final Mt5AccountRepository mt5AccountRepository;
    private final CopyRelationRepository copyRelationRepository;
    private final AccountConfigService accountConfigService;

    public PlatformAccountMutationService(
            PlatformAuthService platformAuthService,
            Mt5AccountRepository mt5AccountRepository,
            CopyRelationRepository copyRelationRepository,
            AccountConfigService accountConfigService
    ) {
        this.platformAuthService = platformAuthService;
        this.mt5AccountRepository = mt5AccountRepository;
        this.copyRelationRepository = copyRelationRepository;
        this.accountConfigService = accountConfigService;
    }

    @Transactional
    public Mt5AccountResponse bindMyAccount(BindMyMt5AccountRequest request, HttpServletRequest servletRequest) {
        PlatformUserEntity currentUser = platformAuthService.requireCurrentUser(servletRequest);

        BindMt5AccountRequest delegated = new BindMt5AccountRequest();
        delegated.setUserId(currentUser.getId());
        delegated.setBrokerName(request.getBrokerName());
        delegated.setServerName(request.getServerName());
        delegated.setMt5Login(request.getMt5Login());
        delegated.setCredential(request.getCredential());
        delegated.setAccountRole(request.getAccountRole());
        delegated.setStatus(request.getStatus());
        return accountConfigService.bindAccount(delegated);
    }

    @Transactional
    public RiskRuleResponse saveMyRiskRule(Long accountId, SaveMyRiskRuleRequest request, HttpServletRequest servletRequest) {
        loadAccessibleAccount(accountId, servletRequest);

        SaveRiskRuleRequest delegated = new SaveRiskRuleRequest();
        delegated.setAccountId(accountId);
        delegated.setMaxLot(request.getMaxLot());
        delegated.setFixedLot(request.getFixedLot());
        delegated.setBalanceRatio(request.getBalanceRatio());
        delegated.setMaxSlippagePoints(request.getMaxSlippagePoints());
        delegated.setMaxSlippagePips(request.getMaxSlippagePips());
        delegated.setMaxSlippagePrice(request.getMaxSlippagePrice());
        delegated.setMaxDailyLoss(request.getMaxDailyLoss());
        delegated.setMaxDrawdownPct(request.getMaxDrawdownPct());
        delegated.setAllowedSymbols(request.getAllowedSymbols());
        delegated.setBlockedSymbols(request.getBlockedSymbols());
        delegated.setFollowTpSl(request.getFollowTpSl());
        delegated.setReverseFollow(request.getReverseFollow());
        return accountConfigService.saveRiskRule(delegated);
    }

    @Transactional
    public CopyRelationResponse createMyCopyRelation(CreateCopyRelationRequest request, HttpServletRequest servletRequest) {
        loadAccessibleAccount(request.getMasterAccountId(), servletRequest);
        loadAccessibleAccount(request.getFollowerAccountId(), servletRequest);
        return accountConfigService.createCopyRelation(request);
    }

    @Transactional
    public CopyRelationResponse updateMyCopyRelation(Long relationId, UpdateCopyRelationRequest request, HttpServletRequest servletRequest) {
        CopyRelationEntity relation = copyRelationRepository.findById(relationId)
                .orElseThrow(() -> new EntityNotFoundException("Copy relation not found: " + relationId));
        assertManageableFollowerRelation(relation, servletRequest);
        return accountConfigService.updateCopyRelation(relationId, request);
    }

    @Transactional
    public void deleteMyCopyRelation(Long relationId, HttpServletRequest servletRequest) {
        CopyRelationEntity relation = copyRelationRepository.findById(relationId)
                .orElseThrow(() -> new EntityNotFoundException("Copy relation not found: " + relationId));
        assertAccessibleFollowerRelation(relation, servletRequest);
        accountConfigService.deleteCopyRelation(relationId);
    }

    @Transactional
    public SymbolMappingResponse saveMySymbolMapping(
            Long followerAccountId,
            SaveMySymbolMappingRequest request,
            HttpServletRequest servletRequest
    ) {
        loadAccessibleAccount(followerAccountId, servletRequest);

        SaveSymbolMappingRequest delegated = new SaveSymbolMappingRequest();
        delegated.setFollowerAccountId(followerAccountId);
        delegated.setMasterSymbol(request.getMasterSymbol());
        delegated.setFollowerSymbol(request.getFollowerSymbol());
        return accountConfigService.saveSymbolMapping(delegated);
    }

    private Mt5AccountEntity loadAccessibleAccount(Long accountId, HttpServletRequest servletRequest) {
        PlatformUserEntity currentUser = platformAuthService.requireCurrentUser(servletRequest);
        Mt5AccountEntity account = mt5AccountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("MT5 account not found: " + accountId));
        if (currentUser.getRole() != PlatformUserRole.ADMIN && !currentUser.getId().equals(account.getUserId())) {
            throw new IllegalArgumentException("You do not have access to this MT5 account");
        }
        return account;
    }

    private void assertManageableFollowerRelation(CopyRelationEntity relation, HttpServletRequest servletRequest) {
        PlatformUserEntity currentUser = platformAuthService.requireCurrentUser(servletRequest);
        if (currentUser.getRole() == PlatformUserRole.ADMIN) {
            return;
        }
        boolean ownsFollower = currentUser.getId().equals(relation.getFollowerAccount().getUserId());
        if (!ownsFollower) {
            throw new IllegalArgumentException("Only the follower owner can manage this copy relation");
        }
    }

    private void assertAccessibleFollowerRelation(CopyRelationEntity relation, HttpServletRequest servletRequest) {
        PlatformUserEntity currentUser = platformAuthService.requireCurrentUser(servletRequest);
        if (currentUser.getRole() == PlatformUserRole.ADMIN) {
            return;
        }
        boolean ownsFollower = currentUser.getId().equals(relation.getFollowerAccount().getUserId());
        if (!ownsFollower) {
            throw new IllegalArgumentException("Only the follower owner can unlink this copy relation");
        }
    }
}
