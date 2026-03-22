package com.zyc.copier_v0.modules.user.auth.entity;

import com.zyc.copier_v0.modules.user.auth.domain.PlatformUserRole;
import com.zyc.copier_v0.modules.user.auth.domain.PlatformUserStatus;
import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "platform_users",
        indexes = {
                @Index(name = "idx_platform_user_platform_id", columnList = "platform_id"),
                @Index(name = "idx_platform_user_share_id", columnList = "share_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_platform_user_platform_id", columnNames = "platform_id"),
                @UniqueConstraint(name = "uk_platform_user_username", columnNames = "username"),
                @UniqueConstraint(name = "uk_platform_user_share_id", columnNames = "share_id")
        }
)
@Getter
@Setter
public class PlatformUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "platform_id", nullable = false, length = 32)
    private String platformId;

    @Column(name = "username", nullable = false, length = 128)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "share_id", nullable = false, length = 32)
    private String shareId;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PlatformUserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private PlatformUserRole role;

    @Column(name = "created_at", nullable = false)
    @Setter(AccessLevel.NONE)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Setter(AccessLevel.NONE)
    private Instant updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    @Setter(AccessLevel.NONE)
    private Long rowVersion;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
