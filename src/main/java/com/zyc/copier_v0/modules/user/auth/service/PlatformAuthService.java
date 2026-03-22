package com.zyc.copier_v0.modules.user.auth.service;

import com.zyc.copier_v0.modules.user.auth.api.AuthenticatedPlatformUserResponse;
import com.zyc.copier_v0.modules.user.auth.api.LoginPlatformUserRequest;
import com.zyc.copier_v0.modules.user.auth.api.RegisterPlatformUserRequest;
import com.zyc.copier_v0.modules.user.auth.api.UpdatePlatformUserProfileRequest;
import com.zyc.copier_v0.modules.user.auth.config.PlatformAuthProperties;
import com.zyc.copier_v0.modules.user.auth.domain.PlatformUserRole;
import com.zyc.copier_v0.modules.user.auth.domain.PlatformUserStatus;
import com.zyc.copier_v0.modules.user.auth.entity.PlatformUserEntity;
import com.zyc.copier_v0.modules.user.auth.entity.PlatformUserSessionEntity;
import com.zyc.copier_v0.modules.user.auth.repository.PlatformUserRepository;
import com.zyc.copier_v0.modules.user.auth.repository.PlatformUserSessionRepository;
import java.time.Instant;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;

@Service
public class PlatformAuthService {

    private final PlatformUserRepository platformUserRepository;
    private final PlatformUserSessionRepository platformUserSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformSecurityTokenService tokenService;
    private final PlatformAuthProperties properties;

    public PlatformAuthService(
            PlatformUserRepository platformUserRepository,
            PlatformUserSessionRepository platformUserSessionRepository,
            PasswordEncoder passwordEncoder,
            PlatformSecurityTokenService tokenService,
            PlatformAuthProperties properties
    ) {
        this.platformUserRepository = platformUserRepository;
        this.platformUserSessionRepository = platformUserSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.properties = properties;
    }

    @Transactional
    public AuthenticatedPlatformUserResponse register(
            RegisterPlatformUserRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        String normalizedUsername = tokenService.normalizeUsername(request.getUsername());
        if (platformUserRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new IllegalStateException("Username already exists");
        }

        PlatformUserEntity user = new PlatformUserEntity();
        user.setUsername(normalizedUsername);
        user.setPasswordHash(passwordEncoder.encode(tokenService.normalizeSecret(request.getPassword())));
        user.setPlatformId(tokenService.generateUniquePublicId("P", 8, platformUserRepository::existsByPlatformId));
        user.setShareId(tokenService.generateUniquePublicId("S", 10, platformUserRepository::existsByShareId));
        user.setDisplayName(StringUtils.hasText(request.getDisplayName()) ? request.getDisplayName().trim() : normalizedUsername);
        user.setStatus(PlatformUserStatus.ACTIVE);
        user.setRole(PlatformUserRole.USER);

        PlatformUserEntity saved = platformUserRepository.save(user);
        issueSession(saved, servletRequest, servletResponse);
        return toAuthenticatedResponse(saved);
    }

    @Transactional
    public AuthenticatedPlatformUserResponse login(
            LoginPlatformUserRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        String normalizedLogin = tokenService.normalizeLogin(request.getLogin());
        PlatformUserEntity user = platformUserRepository.findByPlatformId(normalizedLogin.toUpperCase())
                .or(() -> platformUserRepository.findByUsernameIgnoreCase(normalizedLogin))
                .orElseThrow(() -> new UnauthorizedException("Invalid login or password"));

        if (user.getStatus() != PlatformUserStatus.ACTIVE) {
            throw new UnauthorizedException("User is not active");
        }
        if (!passwordEncoder.matches(tokenService.normalizeSecret(request.getPassword()), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid login or password");
        }

        issueSession(user, servletRequest, servletResponse);
        return toAuthenticatedResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthenticatedPlatformUserResponse me(HttpServletRequest servletRequest) {
        return toAuthenticatedResponse(requireCurrentUser(servletRequest));
    }

    @Transactional
    public AuthenticatedPlatformUserResponse updateProfile(
            UpdatePlatformUserProfileRequest request,
            HttpServletRequest servletRequest
    ) {
        PlatformUserEntity user = requireCurrentUser(servletRequest);

        if (StringUtils.hasText(request.getDisplayName())) {
            user.setDisplayName(request.getDisplayName().trim());
        }

        boolean wantsPasswordChange = StringUtils.hasText(request.getNewPassword());
        boolean providedCurrentPassword = StringUtils.hasText(request.getCurrentPassword());
        if (providedCurrentPassword && !wantsPasswordChange) {
            throw new IllegalArgumentException("newPassword must be provided when changing password");
        }
        if (wantsPasswordChange) {
            if (!providedCurrentPassword) {
                throw new IllegalArgumentException("currentPassword is required when changing password");
            }
            if (!passwordEncoder.matches(tokenService.normalizeSecret(request.getCurrentPassword()), user.getPasswordHash())) {
                throw new UnauthorizedException("Current password is incorrect");
            }
            user.setPasswordHash(passwordEncoder.encode(tokenService.normalizeSecret(request.getNewPassword())));
        }

        return toAuthenticatedResponse(platformUserRepository.save(user));
    }

    @Transactional
    public void logout(HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        Cookie cookie = WebUtils.getCookie(servletRequest, properties.getCookieName());
        if (cookie != null && StringUtils.hasText(cookie.getValue())) {
            String sessionTokenHash = tokenService.sha256Hex(cookie.getValue());
            platformUserSessionRepository.findBySessionTokenHashAndExpiresAtAfter(sessionTokenHash, Instant.now())
                    .ifPresent(platformUserSessionRepository::delete);
        }
        clearSessionCookie(servletResponse);
    }

    @Transactional
    public PlatformUserEntity requireCurrentUser(HttpServletRequest servletRequest) {
        PlatformUserSessionEntity session = requireCurrentSession(servletRequest);
        session.setLastSeenAt(Instant.now());
        return session.getUser();
    }

    private PlatformUserSessionEntity requireCurrentSession(HttpServletRequest servletRequest) {
        Cookie cookie = WebUtils.getCookie(servletRequest, properties.getCookieName());
        if (cookie == null || !StringUtils.hasText(cookie.getValue())) {
            throw new UnauthorizedException("Authentication is required");
        }
        String sessionTokenHash = tokenService.sha256Hex(cookie.getValue());
        return platformUserSessionRepository.findBySessionTokenHashAndExpiresAtAfter(sessionTokenHash, Instant.now())
                .orElseThrow(() -> new UnauthorizedException("Session is invalid or expired"));
    }

    private void issueSession(
            PlatformUserEntity user,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        String rawToken = tokenService.generateSessionToken();
        Instant now = Instant.now();

        PlatformUserSessionEntity session = new PlatformUserSessionEntity();
        session.setUser(user);
        session.setSessionTokenHash(tokenService.sha256Hex(rawToken));
        session.setExpiresAt(now.plus(properties.getTtl()));
        session.setLastSeenAt(now);
        session.setIp(trimToNull(servletRequest.getRemoteAddr()));
        session.setUserAgent(trimToNull(servletRequest.getHeader("User-Agent")));
        platformUserSessionRepository.save(session);

        Cookie cookie = new Cookie(properties.getCookieName(), rawToken);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setSecure(properties.isSecure());
        cookie.setMaxAge((int) properties.getTtl().getSeconds());
        servletResponse.addCookie(cookie);
    }

    private void clearSessionCookie(HttpServletResponse servletResponse) {
        Cookie cookie = new Cookie(properties.getCookieName(), "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setSecure(properties.isSecure());
        cookie.setMaxAge(0);
        servletResponse.addCookie(cookie);
    }

    private AuthenticatedPlatformUserResponse toAuthenticatedResponse(PlatformUserEntity user) {
        AuthenticatedPlatformUserResponse response = new AuthenticatedPlatformUserResponse();
        response.setId(user.getId());
        response.setPlatformId(user.getPlatformId());
        response.setUsername(user.getUsername());
        response.setShareId(user.getShareId());
        response.setDisplayName(user.getDisplayName());
        response.setStatus(user.getStatus());
        response.setRole(user.getRole());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        return response;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
