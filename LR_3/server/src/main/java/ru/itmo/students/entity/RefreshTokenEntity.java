package ru.itmo.students.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {
    @Id
    private UUID id;

    // Explicit EAGER: this foundation runs without EclipseLink bytecode weaving.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshTokenEntity() { }

    /** Only the SHA-256 hash of the refresh token is persisted, never the raw token. */
    public RefreshTokenEntity(UUID id, UserEntity user, String tokenHash,
                              Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.user = user;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public void revoke(Instant revokedAt) { this.revokedAt = revokedAt; }
    public UUID getId() { return id; }
    public UserEntity getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
