package com.vocactionops.backend.dataset.domain;

import com.vocactionops.backend.common.entity.BaseTimeEntity;
import com.vocactionops.backend.organization.domain.Organization;
import com.vocactionops.backend.user.domain.User;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.Objects;

@Entity
@Table(name = "datasets")
public class Dataset extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "organization_id", nullable = false)
	private Organization organization;

	@Column(nullable = false, length = 150)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_type", nullable = false, length = 50)
	private SourceType sourceType;

	@Column(name = "file_url", length = 500)
	private String fileUrl;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "column_mapping_json", columnDefinition = "json")
	private Map<String, String> columnMapping;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private DatasetStatus status;

	@Column(name = "total_count", nullable = false)
	private int totalCount;

	@Column(name = "valid_count", nullable = false)
	private int validCount;

	@Column(name = "invalid_count", nullable = false)
	private int invalidCount;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "created_by", nullable = false)
	private User createdBy;

	protected Dataset() {
	}

	public Dataset(
			Organization organization,
			String name,
			SourceType sourceType,
			String fileUrl,
			Map<String, String> columnMapping,
			User createdBy
	) {
		this.organization = Objects.requireNonNull(organization, "organization must not be null");
		this.name = requireText(name, "name");
		this.sourceType = Objects.requireNonNull(sourceType, "sourceType must not be null");
		this.fileUrl = normalizeNullable(fileUrl);
		this.columnMapping = columnMapping == null ? null : Map.copyOf(columnMapping);
		this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
		validateOrganization(organization, createdBy);
		this.status = DatasetStatus.UPLOADED;
	}

	public Dataset(Organization organization, String name, SourceType sourceType, User createdBy) {
		this(organization, name, sourceType, null, null, createdBy);
	}

	public void startValidation() {
		if (status != DatasetStatus.UPLOADED) {
			throw new IllegalStateException("only uploaded dataset can start validation");
		}
		status = DatasetStatus.VALIDATING;
	}

	public void completeValidation(int totalCount, int validCount, int invalidCount) {
		if (status != DatasetStatus.VALIDATING) {
			throw new IllegalStateException("dataset must be validating");
		}
		if (totalCount < 0 || validCount < 0 || invalidCount < 0
				|| validCount + invalidCount != totalCount) {
			throw new IllegalArgumentException("dataset counts are invalid");
		}

		this.totalCount = totalCount;
		this.validCount = validCount;
		this.invalidCount = invalidCount;
		this.status = DatasetStatus.VALIDATED;
	}

	private static void validateOrganization(Organization organization, User user) {
		Organization userOrganization = user.getOrganization();
		if (organization == userOrganization) {
			return;
		}
		if (organization.getId() == null
				|| !organization.getId().equals(userOrganization.getId())) {
			throw new IllegalArgumentException("createdBy must belong to the dataset organization");
		}
	}

	private static String requireText(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
		return value.trim();
	}

	private static String normalizeNullable(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	public Long getId() {
		return id;
	}

	public Organization getOrganization() {
		return organization;
	}

	public String getName() {
		return name;
	}

	public SourceType getSourceType() {
		return sourceType;
	}

	public String getFileUrl() {
		return fileUrl;
	}

	public Map<String, String> getColumnMapping() {
		return columnMapping == null ? null : Map.copyOf(columnMapping);
	}

	public DatasetStatus getStatus() {
		return status;
	}

	public int getTotalCount() {
		return totalCount;
	}

	public int getValidCount() {
		return validCount;
	}

	public int getInvalidCount() {
		return invalidCount;
	}

	public User getCreatedBy() {
		return createdBy;
	}
}
