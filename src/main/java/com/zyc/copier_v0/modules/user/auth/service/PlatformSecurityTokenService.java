package com.zyc.copier_v0.modules.user.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PlatformSecurityTokenService {

    private static final char[] PUBLIC_ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateSessionToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String generateUniquePublicId(String prefix, int randomLength, Predicate<String> existsPredicate) {
        for (int attempt = 0; attempt < 32; attempt++) {
            String candidate = prefix + randomSegment(randomLength);
            if (!existsPredicate.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate unique public identifier");
    }

    public String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalizeSecret(raw).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public String normalizeLogin(String raw) {
        String normalized = trimToNull(raw);
        if (normalized == null) {
            throw new IllegalArgumentException("login must not be blank");
        }
        return normalized;
    }

    public String normalizeUsername(String raw) {
        String normalized = trimToNull(raw);
        if (normalized == null) {
            throw new IllegalArgumentException("username must not be blank");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    public String normalizeSecret(String raw) {
        String normalized = trimToNull(raw);
        if (normalized == null) {
            throw new IllegalArgumentException("secret must not be blank");
        }
        return normalized;
    }

    private String randomSegment(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(PUBLIC_ID_ALPHABET[secureRandom.nextInt(PUBLIC_ID_ALPHABET.length)]);
        }
        return builder.toString();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
