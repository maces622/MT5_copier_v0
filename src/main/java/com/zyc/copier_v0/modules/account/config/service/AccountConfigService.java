package com.zyc.copier_v0.modules.account.config.service;

import com.zyc.copier_v0.modules.account.config.api.BindMt5AccountRequest;
import com.zyc.copier_v0.modules.account.config.api.CopyRelationResponse;
import com.zyc.copier_v0.modules.account.config.api.CreateCopyRelationRequest;
import com.zyc.copier_v0.modules.account.config.api.Mt5AccountResponse;
import com.zyc.copier_v0.modules.account.config.api.RiskRuleResponse;
import com.zyc.copier_v0.modules.account.config.api.SaveRiskRuleRequest;
import com.zyc.copier_v0.modules.account.config.api.SaveSymbolMappingRequest;
import com.zyc.copier_v0.modules.account.config.api.SymbolMappingResponse;
import com.zyc.copier_v0.modules.account.config.api.UpdateCopyRelationRequest;
import com.zyc.copier_v0.modules.account.config.cache.CopyRouteCacheWriter;
import com.zyc.copier_v0.modules.account.config.cache.CopyRouteSnapshotFactory;
import com.zyc.copier_v0.modules.account.config.domain.CopyRelationStatus;
import com.zyc.copier_v0.modules.account.config.domain.Mt5AccountRole;
import com.zyc.copier_v0.modules.account.config.entity.CopyRelationEntity;
import com.zyc.copier_v0.modules.account.config.entity.Mt5AccountEntity;
import com.zyc.copier_v0.modules.account.config.entity.RiskRuleEntity;
import com.zyc.copier_v0.modules.account.config.entity.SymbolMappingEntity;
import com.zyc.copier_v0.modules.account.config.repository.CopyRelationRepository;
import com.zyc.copier_v0.modules.account.config.repository.Mt5AccountRepository;
import com.zyc.copier_v0.modules.account.config.repository.RiskRuleRepository;
import com.zyc.copier_v0.modules.account.config.repository.SymbolMappingRepository;
import java.util.List;
import java.util.Locale;
import javax.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AccountConfigService {

    private final Mt5AccountRepository mt5AccountRepository;
    private final RiskRuleRepository riskRuleRepository;
    private final CopyRelationRepository copyRelationRepository;
    private final SymbolMappingRepository symbolMappingRepository;
    private final CredentialCipherService credentialCipherService;
    private final CopyRelationGraphValidator graphValidator;
    private final CopyRouteCacheWriter routeCacheWriter;
    private final CopyRouteSnapshotFactory routeSnapshotFactory;

    public AccountConfigService(
            Mt5AccountRepository mt5AccountRepository,
            RiskRuleRepository riskRuleRepository,
            CopyRelationRepository copyRelationRepository,
            SymbolMappingRepository symbolMappingRepository,
            CredentialCipherService credentialCipherService,
            CopyRelationGraphValidator graphValidator,
            CopyRouteCacheWriter routeCacheWriter,
            CopyRouteSnapshotFactory routeSnapshotFactory
    ) {
        this.mt5AccountRepository = mt5AccountRepository;
        this.riskRuleRepository = riskRuleRepository;
        this.copyRelationRepository = copyRelationRepository;
        this.symbolMappingRepository = symbolMappingRepository;
        this.credentialCipherService = credentialCipherService;
        this.graphValidator = graphValidator;
        this.routeCacheWriter = routeCacheWriter;
        this.routeSnapshotFactory = routeSnapshotFactory;
    }

    @Transactional
    public Mt5AccountResponse bindAccount(BindMt5AccountRequest request) {
        Mt5AccountEntity account = mt5AccountRepository.findByServerNameAndMt5Login(
                request.getServerName(),
                request.getMt5Login()
        ).orElseGet(Mt5AccountEntity::new);

        if (account.getId() != null && !account.getUserId().equals(request.getUserId())) {
            throw new IllegalStateException("This MT5 account is already bound to another user");
        }

        account.setUserId(request.getUserId());
        account.setBrokerName(request.getBrokerName());
        account.setServerName(request.getServerName());
        account.setMt5Login(request.getMt5Login());
        applyCredential(account, request.getCredential());
        account.setAccountRole(request.getAccountRole());
        account.setStatus(request.getStatus());

        Mt5AccountEntity saved = mt5AccountRepository.save(account);
        routeCacheWriter.refreshAccountBinding(saved);
        return toAccountResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<Mt5AccountResponse> listAccounts() {
        return mt5AccountRepository.findAllByOrderByIdAsc().stream()
                .map(this::toAccountResponse)
                .toList();
    }

    @Transactional
    public RiskRuleResponse saveRiskRule(SaveRiskRuleRequest request) {
        Mt5AccountEntity account = loadAccount(request.getAccountId());
        ensureFollowerCapable(account);

        RiskRuleEntity riskRule = riskRuleRepository.findByAccount_Id(account.getId()).orElseGet(RiskRuleEntity::new);
        riskRule.setAccount(account);
        riskRule.setMaxLot(request.getMaxLot());
        riskRule.setFixedLot(request.getFixedLot());
        riskRule.setBalanceRatio(request.getBalanceRatio());
        riskRule.setMaxSlippagePoints(request.getMaxSlippagePoints());
        riskRule.setMaxSlippagePips(request.getMaxSlippagePips());
        riskRule.setMaxSlippagePrice(request.getMaxSlippagePrice());
        riskRule.setMaxDailyLoss(request.getMaxDailyLoss());
        riskRule.setMaxDrawdownPct(request.getMaxDrawdownPct());
        riskRule.setAllowedSymbols(trimToNull(request.getAllowedSymbols()));
        riskRule.setBlockedSymbols(trimToNull(request.getBlockedSymbols()));
        riskRule.setFollowTpSl(Boolean.TRUE.equals(request.getFollowTpSl()));
        riskRule.setReverseFollow(Boolean.TRUE.equals(request.getReverseFollow()));

        RiskRuleEntity saved = riskRuleRepository.save(riskRule);
        routeCacheWriter.refreshFollowerRisk(account.getId());
        for (Long masterId : routeSnapshotFactory.findMastersByFollower(account.getId())) {
            routeCacheWriter.refreshMasterRoute(masterId);
        }
        return toRiskRuleResponse(saved);
    }

    @Transactional
    public CopyRelationResponse createCopyRelation(CreateCopyRelationRequest request) {
        return createCopyRelationInternal(request, false);
    }

    @Transactional
    public CopyRelationResponse createSharedCopyRelation(CreateCopyRelationRequest request) {
        return createCopyRelationInternal(request, true);
    }

    @Transactional
    protected CopyRelationResponse createCopyRelationInternal(CreateCopyRelationRequest request, boolean allowCrossUser) {
        Mt5AccountEntity masterAccount = loadAccount(request.getMasterAccountId());
        Mt5AccountEntity followerAccount = loadAccount(request.getFollowerAccountId());
        validateAccountsForRelation(masterAccount, followerAccount, allowCrossUser);

        copyRelationRepository.findByMasterAccount_IdAndFollowerAccount_Id(masterAccount.getId(), followerAccount.getId())
                .ifPresent(existing -> {
                    throw new IllegalStateException("Copy relation already exists");
                });

        graphValidator.validate(null, masterAccount.getId(), followerAccount.getId(), request.getStatus());

        CopyRelationEntity relation = new CopyRelationEntity();
        relation.setMasterAccount(masterAccount);
        relation.setFollowerAccount(followerAccount);
        relation.setCopyMode(request.getCopyMode());
        relation.setStatus(request.getStatus());
        relation.setPriority(defaultPriority(request.getPriority()));
        relation.setConfigVersion(1L);

        CopyRelationEntity saved = copyRelationRepository.save(relation);
        routeCacheWriter.refreshMasterRoute(masterAccount.getId());
        return toCopyRelationResponse(saved);
    }

    @Transactional
    public CopyRelationResponse updateCopyRelation(Long relationId, UpdateCopyRelationRequest request) {
        CopyRelationEntity relation = copyRelationRepository.findById(relationId)
                .orElseThrow(() -> new EntityNotFoundException("Copy relation not found: " + relationId));

        if (request.getCopyMode() != null) {
            relation.setCopyMode(request.getCopyMode());
        }
        if (request.getStatus() != null) {
            graphValidator.validate(
                    relation.getId(),
                    relation.getMasterAccount().getId(),
                    relation.getFollowerAccount().getId(),
                    request.getStatus()
            );
            relation.setStatus(request.getStatus());
        }
        if (request.getPriority() != null) {
            relation.setPriority(defaultPriority(request.getPriority()));
        }
        relation.setConfigVersion(relation.getConfigVersion() + 1);

        CopyRelationEntity saved = copyRelationRepository.save(relation);
        routeCacheWriter.refreshMasterRoute(saved.getMasterAccount().getId());
        return toCopyRelationResponse(saved);
    }

    @Transactional
    public void deleteCopyRelation(Long relationId) {
        CopyRelationEntity relation = copyRelationRepository.findById(relationId)
                .orElseThrow(() -> new EntityNotFoundException("Copy relation not found: " + relationId));
        Long masterAccountId = relation.getMasterAccount().getId();
        copyRelationRepository.delete(relation);
        routeCacheWriter.refreshMasterRoute(masterAccountId);
    }

    @Transactional(readOnly = true)
    public List<CopyRelationResponse> listRelationsByMaster(Long masterAccountId) {
        return copyRelationRepository.findByMasterAccount_IdAndStatusOrderByPriorityAscIdAsc(
                        masterAccountId,
                        CopyRelationStatus.ACTIVE
                ).stream()
                .map(this::toCopyRelationResponse)
                .toList();
    }

    @Transactional
    public SymbolMappingResponse saveSymbolMapping(SaveSymbolMappingRequest request) {
        Mt5AccountEntity followerAccount = loadAccount(request.getFollowerAccountId());
        ensureFollowerCapable(followerAccount);

        String masterSymbol = normalizeSymbolKey(request.getMasterSymbol());
        String followerSymbol = normalizeFollowerSymbol(request.getFollowerSymbol());

        SymbolMappingEntity mapping = symbolMappingRepository.findByFollowerAccount_IdAndMasterSymbol(
                followerAccount.getId(),
                masterSymbol
        ).orElseGet(SymbolMappingEntity::new);

        mapping.setFollowerAccount(followerAccount);
        mapping.setMasterSymbol(masterSymbol);
        mapping.setFollowerSymbol(followerSymbol);

        SymbolMappingEntity saved = symbolMappingRepository.save(mapping);
        routeCacheWriter.refreshFollowerRisk(followerAccount.getId());
        for (Long masterId : routeSnapshotFactory.findMastersByFollower(followerAccount.getId())) {
            routeCacheWriter.refreshMasterRoute(masterId);
        }
        return toSymbolMappingResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<SymbolMappingResponse> listSymbolMappings(Long followerAccountId) {
        Mt5AccountEntity followerAccount = loadAccount(followerAccountId);
        ensureFollowerCapable(followerAccount);
        return symbolMappingRepository.findByFollowerAccount_IdOrderByMasterSymbolAsc(followerAccountId).stream()
                .map(this::toSymbolMappingResponse)
                .toList();
    }

    private Mt5AccountEntity loadAccount(Long accountId) {
        return mt5AccountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("MT5 account not found: " + accountId));
    }

    private void validateAccountsForRelation(
            Mt5AccountEntity masterAccount,
            Mt5AccountEntity followerAccount,
            boolean allowCrossUser
    ) {
        if (!allowCrossUser && !masterAccount.getUserId().equals(followerAccount.getUserId())) {
            throw new IllegalArgumentException("Cross-user copy relations are not allowed");
        }
        ensureMasterCapable(masterAccount);
        ensureFollowerCapable(followerAccount);
    }

    private void ensureMasterCapable(Mt5AccountEntity account) {
        if (account.getAccountRole() != Mt5AccountRole.MASTER && account.getAccountRole() != Mt5AccountRole.BOTH) {
            throw new IllegalArgumentException("Account is not allowed to act as master");
        }
    }

    private void ensureFollowerCapable(Mt5AccountEntity account) {
        if (account.getAccountRole() != Mt5AccountRole.FOLLOWER && account.getAccountRole() != Mt5AccountRole.BOTH) {
            throw new IllegalArgumentException("Account is not allowed to act as follower");
        }
    }

    private int defaultPriority(Integer priority) {
        return priority == null ? 100 : priority;
    }

    private void applyCredential(Mt5AccountEntity account, String credential) {
        if (StringUtils.hasText(credential)) {
            account.setCredentialCiphertext(credentialCipherService.encrypt(credential));
            account.setCredentialVersion(account.getCredentialVersion() == null ? 1 : account.getCredentialVersion() + 1);
            return;
        }

        if (account.getCredentialCiphertext() == null) {
            account.setCredentialCiphertext("");
        }
        if (account.getCredentialVersion() == null) {
            account.setCredentialVersion(0);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeSymbolKey(String symbol) {
        String normalized = trimToNull(symbol);
        if (normalized == null) {
            throw new IllegalArgumentException("masterSymbol must not be blank");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeFollowerSymbol(String symbol) {
        String normalized = trimToNull(symbol);
        if (normalized == null) {
            throw new IllegalArgumentException("followerSymbol must not be blank");
        }
        return normalized;
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
}
