package com.vocactionops.backend.auth.domain;

import com.vocactionops.backend.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "family_id", nullable = false, length = 36)
	private String familyId;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "replaced_by_token_id")
	private RefreshToken replacedByToken;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected RefreshToken() {
	}

	public RefreshToken(
			User user,
			String tokenHash,
			String familyId,
			Instant createdAt,
			Instant expiresAt
	) {
		this.user = Objects.requireNonNull(user, "user must not be null");
		this.tokenHash = requireLength(tokenHash, "tokenHash", 64);
		this.familyId = requireLength(familyId, "familyId", 36);
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
		this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
		if (!expiresAt.isAfter(createdAt)) {
			throw new IllegalArgumentException("expiresAt must be after createdAt");
		}
	}

	public void rotateTo(RefreshToken replacement, Instant rotatedAt) {
		RefreshToken nextToken = Objects.requireNonNull(replacement, "replacement must not be null");
		Instant usedTime = Objects.requireNonNull(rotatedAt, "rotatedAt must not be null");
		if (usedAt != null || revokedAt != null || isExpired(usedTime)) {
			throw new IllegalStateException("refresh token cannot be rotated");
		}
		if (!familyId.equals(nextToken.familyId) || !sameUser(nextToken)) {
			throw new IllegalArgumentException("replacement must belong to the same token family");
		}
		usedAt = usedTime;
		replacedByToken = nextToken;
	}

	public void revoke(Instant revokedAt) {
		if (this.revokedAt == null) {
			this.revokedAt = Objects.requireNonNull(revokedAt, "revokedAt must not be null");
		}
	}

	public boolean isExpired(Instant now) {
		return !expiresAt.isAfter(now);
	}

	public boolean isUsed() {
		return usedAt != null;
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}

	private boolean sameUser(RefreshToken other) {
		if (user == other.user) {
			return true;
		}
		return user.getId() != null && user.getId().equals(other.user.getId());
	}

	private static String requireLength(String value, String fieldName, int length) {
		if (value == null || value.length() != length) {
			throw new IllegalArgumentException(fieldName + " is invalid");
		}
		return value;
	}

	public Long getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public String getFamilyId() {
		return familyId;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getUsedAt() {
		return usedAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public RefreshToken getReplacedByToken() {
		return replacedByToken;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
