package com.vocactionops.backend.organization.domain;

import com.vocactionops.backend.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "organizations")
public class Organization extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	protected Organization() {
	}

	public Organization(String name) {
		this.name = Objects.requireNonNull(name, "name must not be null");
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
