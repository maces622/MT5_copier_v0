package com.zyc.copier_v0.modules.account.config.entity;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
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
        name = "master_share_configs",
        indexes = {
                @Index(name = "idx_master_share_user_fingerprint", columnList = "share_code_fingerprint"),
                @Index(name = "idx_master_share_enabled", columnList = "share_enabled")
        },
        uniqueConstraints = @UniqueConstraint(name = "uk_master_share_account", columnNames = "master_account_id")
)
@Getter
@Setter
public class MasterShareConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "master_account_id", nullable = false)
    private Mt5AccountEntity masterAccount;

    @Column(name = "share_code_hash", nullable = false, length = 255)
    private String shareCodeHash;

    @Column(name = "share_code_fingerprint", nullable = false, length = 128)
    private String shareCodeFingerprint;

    @Column(name = "share_enabled", nullable = false)
    private boolean shareEnabled;

    @Column(name = "share_note", length = 255)
    private String shareNote;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

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
