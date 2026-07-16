package com.vocactionops.backend.user.domain;

import com.vocactionops.backend.common.entity.BaseTimeEntity;
import com.vocactionops.backend.organization.domain.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "users")
public class User extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "organization_id", nullable = false)
	private Organization organization;

	@Column(nullable = false, unique = true, length = 255)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private Role role;

	protected User() {
	}

	public User(Organization organization, String email, String passwordHash, String name, Role role) {
		this.organization = Objects.requireNonNull(organization, "organization must not be null");
		this.email = normalizeEmail(email);
		this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
		this.name = Objects.requireNonNull(name, "name must not be null");
		this.role = Objects.requireNonNull(role, "role must not be null");
	}

	private static String normalizeEmail(String email) {
		return Objects.requireNonNull(email, "email must not be null")
				.trim()
				.toLowerCase(Locale.ROOT);
	}

	public Long getId() {
		return id;
	}

	public Organization getOrganization() {
		return organization;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getName() {
		return name;
	}

	public Role getRole() {
		return role;
	}
}
