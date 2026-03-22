package com.zyc.copier_v0.modules.account.config.service;

import com.zyc.copier_v0.modules.account.config.api.CopyRelationResponse;
import com.zyc.copier_v0.modules.account.config.api.CreateCopyRelationRequest;
import com.zyc.copier_v0.modules.account.config.api.JoinByShareRequest;
import com.zyc.copier_v0.modules.account.config.api.MasterShareAccountSummaryResponse;
import com.zyc.copier_v0.modules.account.config.api.MasterShareConfigResponse;
import com.zyc.copier_v0.modules.account.config.api.SaveMasterShareConfigRequest;
import com.zyc.copier_v0.modules.account.config.api.ShareProfileResponse;
import com.zyc.copier_v0.modules.account.config.domain.AccountStatus;
import com.zyc.copier_v0.modules.account.config.domain.CopyRelationStatus;
import com.zyc.copier_v0.modules.account.config.domain.Mt5AccountRole;
import com.zyc.copier_v0.modules.account.config.entity.MasterShareConfigEntity;
import com.zyc.copier_v0.modules.account.config.entity.Mt5AccountEntity;
import com.zyc.copier_v0.modules.account.config.repository.MasterShareConfigRepository;
import com.zyc.copier_v0.modules.account.config.repository.Mt5AccountRepository;
import com.zyc.copier_v0.modules.user.auth.entity.PlatformUserEntity;
import com.zyc.copier_v0.modules.user.auth.repository.PlatformUserRepository;
import com.zyc.copier_v0.modules.user.auth.service.PlatformAuthService;
import com.zyc.copier_v0.modules.user.auth.service.PlatformSecurityTokenService;
import java.time.Instant;
import java.util.List;
import javax.persistence.EntityNotFoundException;
import javax.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AccountSharingService {

    private final PlatformAuthService platformAuthService;
    private final PlatformUserRepository platformUserRepository;
    private final Mt5AccountRepository mt5AccountRepository;
    private final MasterShareConfigRepository masterShareConfigRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformSecurityTokenService tokenService;
    private final AccountConfigService accountConfigService;

    public AccountSharingService(
            PlatformAuthService platformAuthService,
            PlatformUserRepository platformUserRepository,
            Mt5AccountRepository mt5AccountRepository,
            MasterShareConfigRepository masterShareConfigRepository,
            PasswordEncoder passwordEncoder,
            PlatformSecurityTokenService tokenService,
            AccountConfigService accountConfigService
    ) {
        this.platformAuthService = platformAuthService;
        this.platformUserRepository = platformUserRepository;
        this.mt5AccountRepository = mt5AccountRepository;
        this.masterShareConfigRepository = masterShareConfigRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.accountConfigService = accountConfigService;
    }

    @Transactional(readOnly = true)
    public ShareProfileResponse getCurrentShareProfile(HttpServletRequest request) {
        PlatformUserEntity user = platformAuthService.requireCurrentUser(request);
        List<MasterShareAccountSummaryResponse> masterAccounts = mt5AccountRepository.findByUserIdOrderByIdAsc(user.getId()).stream()
                .filter(this::isMasterCapable)
                .map(this::toMasterShareAccountSummary)
                .toList();

        ShareProfileResponse response = new ShareProfileResponse();
        response.setUserId(user.getId());
        response.setPlatformId(user.getPlatformId());
        response.setShareId(user.getShareId());
        response.setDisplayName(user.getDisplayName());
        response.setMasterAccounts(masterAccounts);
        return response;
    }

    @Transactional
    public MasterShareConfigResponse saveShareConfig(
            Long accountId,
            SaveMasterShareConfigRequest request,
            HttpServletRequest servletRequest
    ) {
        PlatformUserEntity currentUser = platformAuthService.requireCurrentUser(servletRequest);
        Mt5AccountEntity masterAccount = loadOwnedMasterAccount(currentUser.getId(), accountId);

        MasterShareConfigEntity config = masterShareConfigRepository.findByMasterAccount_Id(accountId)
                .orElseGet(MasterShareConfigEntity::new);
        config.setMasterAccount(masterAccount);

        boolean enableShare = request.getShareEnabled() != null ? request.getShareEnabled() : config.isShareEnabled();
        String normalizedShareCode = StringUtils.hasText(request.getShareCode())
                ? tokenService.normalizeSecret(request.getShareCode())
                : null;

        if (enableShare && !StringUtils.hasText(config.getShareCodeHash()) && normalizedShareCode == null) {
            throw new IllegalArgumentException("shareCode is required when enabling share for the first time");
        }

        if (normalizedShareCode != null) {
            String fingerprint = tokenService.sha256Hex(normalizedShareCode);
            ensureUniqueShareCodeForUser(currentUser.getId(), masterAccount.getId(), fingerprint);
            config.setShareCodeFingerprint(fingerprint);
            config.setShareCodeHash(passwordEncoder.encode(normalizedShareCode));
            config.setRotatedAt(Instant.now());
        } else if (!StringUtils.hasText(config.getShareCodeHash())) {
            config.setShareCodeFingerprint(tokenService.sha256Hex(masterAccount.getId() + ":" + currentUser.getId() + ":placeholder"));
            config.setShareCodeHash(passwordEncoder.encode(tokenService.generateSessionToken()));
        }

        config.setShareEnabled(enableShare);
        config.setShareNote(trimToNull(request.getShareNote()));
        config.setExpiresAt(request.getExpiresAt());

        MasterShareConfigEntity saved = masterShareConfigRepository.save(config);
        return toMasterShareConfigResponse(saved);
    }

    @Transactional
    public CopyRelationResponse joinByShare(JoinByShareRequest request, HttpServletRequest servletRequest) {
        PlatformUserEntity currentUser = platformAuthService.requireCurrentUser(servletRequest);
        Mt5AccountEntity followerAccount = loadFollowerOwnedByUser(currentUser.getId(), request.getFollowerAccountId());
        if (followerAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Follower account is not active");
        }

        PlatformUserEntity targetUser = platformUserRepository.findByShareId(tokenService.normalizeLogin(request.getShareId()).toUpperCase())
                .orElseThrow(() -> new EntityNotFoundException("Share profile not found"));
        String normalizedShareCode = tokenService.normalizeSecret(request.getShareCode());
        String fingerprint = tokenService.sha256Hex(normalizedShareCode);

        List<MasterShareConfigEntity> matches = masterShareConfigRepository.findByMasterAccount_UserIdAndShareCodeFingerprintAndShareEnabledTrue(
                        targetUser.getId(),
                        fingerprint
                ).stream()
                .filter(this::isShareConfigUsable)
                .filter(config -> passwordEncoder.matches(normalizedShareCode, config.getShareCodeHash()))
                .toList();

        if (matches.isEmpty()) {
            throw new IllegalArgumentException("Invalid shareId or shareCode");
        }
        if (matches.size() > 1) {
            throw new IllegalStateException("Share code matches multiple master accounts");
        }

        MasterShareConfigEntity targetShareConfig = matches.get(0);
        CreateCopyRelationRequest relationRequest = new CreateCopyRelationRequest();
        relationRequest.setMasterAccountId(targetShareConfig.getMasterAccount().getId());
        relationRequest.setFollowerAccountId(followerAccount.getId());
        relationRequest.setCopyMode(request.getCopyMode());
        relationRequest.setPriority(request.getPriority());
        relationRequest.setStatus(CopyRelationStatus.PAUSED);

        return accountConfigService.createSharedCopyRelation(relationRequest);
    }

    private Mt5AccountEntity loadOwnedMasterAccount(Long userId, Long accountId) {
        Mt5AccountEntity account = mt5AccountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("MT5 account not found: " + accountId));
        if (!userId.equals(account.getUserId())) {
            throw new IllegalArgumentException("You do not own this MT5 account");
        }
        if (!isMasterCapable(account)) {
            throw new IllegalArgumentException("Account is not allowed to act as master");
        }
        return account;
    }

    private Mt5AccountEntity loadFollowerOwnedByUser(Long userId, Long accountId) {
        Mt5AccountEntity account = mt5AccountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("MT5 account not found: " + accountId));
        if (!userId.equals(account.getUserId())) {
            throw new IllegalArgumentException("You do not own this follower account");
        }
        if (account.getAccountRole() != Mt5AccountRole.FOLLOWER && account.getAccountRole() != Mt5AccountRole.BOTH) {
            throw new IllegalArgumentException("Account is not allowed to act as follower");
        }
        return account;
    }

    private boolean isMasterCapable(Mt5AccountEntity account) {
        return account.getAccountRole() == Mt5AccountRole.MASTER || account.getAccountRole() == Mt5AccountRole.BOTH;
    }

    private boolean isShareConfigUsable(MasterShareConfigEntity config) {
        return config.getMasterAccount().getStatus() == AccountStatus.ACTIVE
                && isMasterCapable(config.getMasterAccount())
                && (config.getExpiresAt() == null || config.getExpiresAt().isAfter(Instant.now()));
    }

    private void ensureUniqueShareCodeForUser(Long userId, Long currentMasterAccountId, String fingerprint) {
        boolean duplicate = masterShareConfigRepository.findByMasterAccount_UserIdAndShareCodeFingerprintAndShareEnabledTrue(userId, fingerprint)
                .stream()
                .anyMatch(existing -> !existing.getMasterAccount().getId().equals(currentMasterAccountId));
        if (duplicate) {
            throw new IllegalStateException("shareCode must be unique among your enabled master accounts");
        }
    }

    private MasterShareAccountSummaryResponse toMasterShareAccountSummary(Mt5AccountEntity account) {
        MasterShareConfigEntity config = masterShareConfigRepository.findByMasterAccount_Id(account.getId()).orElse(null);
        MasterShareAccountSummaryResponse response = new MasterShareAccountSummaryResponse();
        response.setMasterAccountId(account.getId());
        response.setBrokerName(account.getBrokerName());
        response.setServerName(account.getServerName());
        response.setMt5Login(account.getMt5Login());
        response.setAccountRole(account.getAccountRole());
        response.setShareEnabled(config != null && config.isShareEnabled());
        response.setShareCodeConfigured(config != null && StringUtils.hasText(config.getShareCodeHash()));
        response.setShareNote(config == null ? null : config.getShareNote());
        response.setRotatedAt(config == null ? null : config.getRotatedAt());
        response.setExpiresAt(config == null ? null : config.getExpiresAt());
        response.setUpdatedAt(config == null ? account.getUpdatedAt() : config.getUpdatedAt());
        return response;
    }

    private MasterShareConfigResponse toMasterShareConfigResponse(MasterShareConfigEntity entity) {
        MasterShareConfigResponse response = new MasterShareConfigResponse();
        response.setId(entity.getId());
        response.setMasterAccountId(entity.getMasterAccount().getId());
        response.setShareEnabled(entity.isShareEnabled());
        response.setShareCodeConfigured(StringUtils.hasText(entity.getShareCodeHash()));
        response.setShareNote(entity.getShareNote());
        response.setRotatedAt(entity.getRotatedAt());
        response.setExpiresAt(entity.getExpiresAt());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
