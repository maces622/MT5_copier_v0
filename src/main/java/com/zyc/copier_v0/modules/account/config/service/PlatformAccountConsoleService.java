package com.zyc.copier_v0.modules.account.config.service;

import com.zyc.copier_v0.modules.account.config.api.AccountRelationsResponse;
import com.zyc.copier_v0.modules.account.config.api.CopyRelationResponse;
import com.zyc.copier_v0.modules.account.config.api.MasterShareConfigResponse;
import com.zyc.copier_v0.modules.account.config.api.Mt5AccountResponse;
import com.zyc.copier_v0.modules.account.config.api.PlatformAccountDetailResponse;
import com.zyc.copier_v0.modules.account.config.api.PlatformCopyRelationViewResponse;
import com.zyc.copier_v0.modules.account.config.api.RelationAccountSummaryResponse;
import com.zyc.copier_v0.modules.account.config.api.RiskRuleResponse;
import com.zyc.copier_v0.modules.account.config.api.SymbolMappingResponse;
import com.zyc.copier_v0.modules.account.config.entity.CopyRelationEntity;
import com.zyc.copier_v0.modules.account.config.entity.MasterShareConfigEntity;
import com.zyc.copier_v0.modules.account.config.entity.Mt5AccountEntity;
import com.zyc.copier_v0.modules.account.config.entity.RiskRuleEntity;
import com.zyc.copier_v0.modules.account.config.entity.SymbolMappingEntity;
import com.zyc.copier_v0.modules.account.config.repository.CopyRelationRepository;
import com.zyc.copier_v0.modules.account.config.repository.MasterShareConfigRepository;
import com.zyc.copier_v0.modules.account.config.repository.Mt5AccountRepository;
import com.zyc.copier_v0.modules.account.config.repository.RiskRuleRepository;
import com.zyc.copier_v0.modules.account.config.repository.SymbolMappingRepository;
import com.zyc.copier_v0.modules.user.auth.domain.PlatformUserRole;
import com.zyc.copier_v0.modules.user.auth.entity.PlatformUserEntity;
import com.zyc.copier_v0.modules.user.auth.service.PlatformAuthService;
import java.util.List;
import javax.persistence.EntityNotFoundException;
import javax.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAccountConsoleService {

    private final PlatformAuthService platformAuthService;
    private final Mt5AccountRepository mt5AccountRepository;
    private final RiskRuleRepository riskRuleRepository;
    private final CopyRelationRepository copyRelationRepository;
    private final SymbolMappingRepository symbolMappingRepository;
    private final MasterShareConfigRepository masterShareConfigRepository;

    public PlatformAccountConsoleService(
            PlatformAuthService platformAuthService,
            Mt5AccountRepository mt5AccountRepository,
            RiskRuleRepository riskRuleRepository,
            CopyRelationRepository copyRelationRepository,
            SymbolMappingRepository symbolMappingRepository,
            MasterShareConfigRepository masterShareConfigRepository
    ) {
        this.platformAuthService = platformAuthService;
        this.mt5AccountRepository = mt5AccountRepository;
        this.riskRuleRepository = riskRuleRepository;
        this.copyRelationRepository = copyRelationRepository;
        this.symbolMappingRepository = symbolMappingRepository;
        this.masterShareConfigRepository = masterShareConfigRepository;
    }

    @Transactional(readOnly = true)
    public List<Mt5AccountResponse> listMyAccounts(HttpServletRequest request) {
        PlatformUserEntity currentUser = platformAuthService.requireCurrentUser(request);
        return selectAccessibleAccounts(currentUser).stream()
                .map(this::toAccountResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlatformCopyRelationViewResponse> listMyCopyRelations(HttpServletRequest request) {
        PlatformUserEntity currentUser = platformAuthService.requireCurrentUser(request);
        List<CopyRelationEntity> relations;
        if (currentUser.getRole() == PlatformUserRole.ADMIN) {
            relations = copyRelationRepository.findAllByOrderByUpdatedAtDescIdDesc();
        } else {
            List<Long> accountIds = mt5AccountRepository.findByUserIdOrderByIdAsc(currentUser.getId()).stream()
                    .map(Mt5AccountEntity::getId)
                    .toList();
            if (accountIds.isEmpty()) {
                return List.of();
            }
            relations = copyRelationRepository.findByMasterAccount_IdInOrFollowerAccount_IdInOrderByUpdatedAtDescIdDesc(accountIds, accountIds);
        }
        return relations.stream()
                .map(relation -> toRelationViewResponse(relation, currentUser))
                .toList();
    }

    @Transactional(readOnly = true)
    public Mt5AccountResponse getAccount(Long accountId, HttpServletRequest request) {
        return toAccountResponse(loadAccessibleAccount(accountId, request));
    }

    @Transactional(readOnly = true)
    public PlatformAccountDetailResponse getAccountDetail(Long accountId, HttpServletRequest request) {
        Mt5AccountEntity account = loadAccessibleAccount(accountId, request);

        PlatformAccountDetailResponse response = new PlatformAccountDetailResponse();
        response.setAccount(toAccountResponse(account));
        response.setRiskRule(riskRuleRepository.findByAccount_Id(account.getId()).map(this::toRiskRuleResponse).orElse(null));
        response.setRelations(buildRelationsResponse(account.getId()));
        response.setSymbolMappings(symbolMappingRepository.findByFollowerAccount_IdOrderByMasterSymbolAsc(account.getId()).stream()
                .map(this::toSymbolMappingResponse)
                .toList());
        response.setShareConfig(masterShareConfigRepository.findByMasterAccount_Id(account.getId()).map(this::toShareConfigResponse).orElse(null));
        return response;
    }

    @Transactional(readOnly = true)
    public RiskRuleResponse getRiskRule(Long accountId, HttpServletRequest request) {
        Mt5AccountEntity account = loadAccessibleAccount(accountId, request);
        return riskRuleRepository.findByAccount_Id(account.getId()).map(this::toRiskRuleResponse).orElse(null);
    }

    @Transactional(readOnly = true)
    public AccountRelationsResponse getRelations(Long accountId, HttpServletRequest request) {
        Mt5AccountEntity account = loadAccessibleAccount(accountId, request);
        return buildRelationsResponse(account.getId());
    }

    @Transactional(readOnly = true)
    public List<SymbolMappingResponse> getSymbolMappings(Long accountId, HttpServletRequest request) {
        Mt5AccountEntity account = loadAccessibleAccount(accountId, request);
        return symbolMappingRepository.findByFollowerAccount_IdOrderByMasterSymbolAsc(account.getId()).stream()
                .map(this::toSymbolMappingResponse)
                .toList();
    }

    private List<Mt5AccountEntity> selectAccessibleAccounts(PlatformUserEntity currentUser) {
        if (currentUser.getRole() == PlatformUserRole.ADMIN) {
            return mt5AccountRepository.findAllByOrderByIdAsc();
        }
        return mt5AccountRepository.findByUserIdOrderByIdAsc(currentUser.getId());
    }

    private Mt5AccountEntity loadAccessibleAccount(Long accountId, HttpServletRequest request) {
        PlatformUserEntity currentUser = platformAuthService.requireCurrentUser(request);
        Mt5AccountEntity account = mt5AccountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("MT5 account not found: " + accountId));
        if (currentUser.getRole() != PlatformUserRole.ADMIN && !currentUser.getId().equals(account.getUserId())) {
            throw new IllegalArgumentException("You do not have access to this MT5 account");
        }
        return account;
    }

    private AccountRelationsResponse buildRelationsResponse(Long accountId) {
        AccountRelationsResponse response = new AccountRelationsResponse();
        response.setAccountId(accountId);
        response.setMasterRelations(copyRelationRepository.findByMasterAccount_IdOrderByPriorityAscIdAsc(accountId).stream()
                .map(this::toCopyRelationResponse)
                .toList());
        response.setFollowerRelations(copyRelationRepository.findByFollowerAccount_IdOrderByPriorityAscIdAsc(accountId).stream()
                .map(this::toCopyRelationResponse)
                .toList());
        return response;
    }

    private PlatformCopyRelationViewResponse toRelationViewResponse(CopyRelationEntity entity, PlatformUserEntity currentUser) {
        PlatformCopyRelationViewResponse response = new PlatformCopyRelationViewResponse();
        response.setRelation(toCopyRelationResponse(entity));
        response.setMasterAccount(toRelationAccountSummary(entity.getMasterAccount()));
        response.setFollowerAccount(toRelationAccountSummary(entity.getFollowerAccount()));
        response.setCurrentUserOwnsMaster(currentUser.getRole() == PlatformUserRole.ADMIN
                || currentUser.getId().equals(entity.getMasterAccount().getUserId()));
        response.setCurrentUserOwnsFollower(currentUser.getRole() == PlatformUserRole.ADMIN
                || currentUser.getId().equals(entity.getFollowerAccount().getUserId()));
        return response;
    }

    private Mt5AccountResponse toAccountResponse(Mt5AccountEntity entity) {
        Mt5AccountResponse response = new Mt5AccountResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setBrokerName(entity.getBrokerName());
        response.setServerName(entity.getServerName());
        response.setMt5Login(entity.getMt5Login());
        response.setCredentialVersion(entity.getCredentialVersion());
        response.setCredentialConfigured(entity.getCredentialCiphertext() != null && !entity.getCredentialCiphertext().isBlank());
        response.setAccountRole(entity.getAccountRole());
        response.setStatus(entity.getStatus());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private RiskRuleResponse toRiskRuleResponse(RiskRuleEntity entity) {
        RiskRuleResponse response = new RiskRuleResponse();
        response.setId(entity.getId());
        response.setAccountId(entity.getAccount().getId());
        response.setMaxLot(entity.getMaxLot());
        response.setFixedLot(entity.getFixedLot());
        response.setBalanceRatio(entity.getBalanceRatio());
        response.setMaxSlippagePoints(entity.getMaxSlippagePoints());
        response.setMaxSlippagePips(entity.getMaxSlippagePips());
        response.setMaxSlippagePrice(entity.getMaxSlippagePrice());
        response.setMaxDailyLoss(entity.getMaxDailyLoss());
        response.setMaxDrawdownPct(entity.getMaxDrawdownPct());
        response.setAllowedSymbols(entity.getAllowedSymbols());
        response.setBlockedSymbols(entity.getBlockedSymbols());
        response.setFollowTpSl(entity.isFollowTpSl());
        response.setReverseFollow(entity.isReverseFollow());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private CopyRelationResponse toCopyRelationResponse(CopyRelationEntity entity) {
        CopyRelationResponse response = new CopyRelationResponse();
        response.setId(entity.getId());
        response.setMasterAccountId(entity.getMasterAccount().getId());
        response.setFollowerAccountId(entity.getFollowerAccount().getId());
        response.setCopyMode(entity.getCopyMode());
        response.setStatus(entity.getStatus());
        response.setPriority(entity.getPriority());
        response.setConfigVersion(entity.getConfigVersion());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private SymbolMappingResponse toSymbolMappingResponse(SymbolMappingEntity entity) {
        SymbolMappingResponse response = new SymbolMappingResponse();
        response.setId(entity.getId());
        response.setFollowerAccountId(entity.getFollowerAccount().getId());
        response.setMasterSymbol(entity.getMasterSymbol());
        response.setFollowerSymbol(entity.getFollowerSymbol());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private MasterShareConfigResponse toShareConfigResponse(MasterShareConfigEntity entity) {
        MasterShareConfigResponse response = new MasterShareConfigResponse();
        response.setId(entity.getId());
        response.setMasterAccountId(entity.getMasterAccount().getId());
        response.setShareEnabled(entity.isShareEnabled());
        response.setShareCodeConfigured(entity.getShareCodeHash() != null && !entity.getShareCodeHash().isBlank());
        response.setShareNote(entity.getShareNote());
        response.setRotatedAt(entity.getRotatedAt());
        response.setExpiresAt(entity.getExpiresAt());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private RelationAccountSummaryResponse toRelationAccountSummary(Mt5AccountEntity entity) {
        RelationAccountSummaryResponse response = new RelationAccountSummaryResponse();
        response.setId(entity.getId());
        response.setBrokerName(entity.getBrokerName());
        response.setServerName(entity.getServerName());
        response.setMt5Login(entity.getMt5Login());
        response.setAccountRole(entity.getAccountRole().name());
        response.setStatus(entity.getStatus().name());
        return response;
    }
}
