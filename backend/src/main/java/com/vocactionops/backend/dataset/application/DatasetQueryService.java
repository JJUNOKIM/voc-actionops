package com.vocactionops.backend.dataset.application;

import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.common.response.PageResponse;
import com.vocactionops.backend.dataset.domain.Dataset;
import com.vocactionops.backend.dataset.domain.DatasetStatus;
import com.vocactionops.backend.dataset.domain.SourceType;
import com.vocactionops.backend.dataset.repository.DatasetRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

import static com.vocactionops.backend.common.web.PageRequestFactory.newestFirst;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN', 'PM', 'CS', 'VIEWER')")
public class DatasetQueryService {

	private final DatasetRepository datasetRepository;

	public DatasetQueryService(DatasetRepository datasetRepository) {
		this.datasetRepository = datasetRepository;
	}

	public PageResponse<DatasetSummary> getDatasets(
			AuthenticatedUser authenticatedUser,
			SourceType sourceType,
			DatasetStatus status,
			int page,
			int size
	) {
		return PageResponse.from(datasetRepository.findPageByOrganization(
				authenticatedUser.organizationId(),
				sourceType,
				status,
				newestFirst(page, size, "createdAt")
		).map(DatasetSummary::from));
	}

	public DatasetDetail getDataset(AuthenticatedUser authenticatedUser, Long datasetId) {
		Dataset dataset = datasetRepository.findByIdAndOrganizationId(
						datasetId,
						authenticatedUser.organizationId()
				)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
		return DatasetDetail.from(dataset);
	}

	public record DatasetSummary(
			Long id,
			String name,
			SourceType sourceType,
			DatasetStatus status,
			int totalCount,
			int validCount,
			int invalidCount,
			LocalDateTime createdAt
	) {
		private static DatasetSummary from(Dataset dataset) {
			return new DatasetSummary(
					dataset.getId(),
					dataset.getName(),
					dataset.getSourceType(),
					dataset.getStatus(),
					dataset.getTotalCount(),
					dataset.getValidCount(),
					dataset.getInvalidCount(),
					dataset.getCreatedAt()
			);
		}
	}

	public record DatasetDetail(
			Long id,
			String name,
			SourceType sourceType,
			String fileUrl,
			Map<String, String> columnMapping,
			DatasetStatus status,
			int totalCount,
			int validCount,
			int invalidCount,
			Long createdBy,
			LocalDateTime createdAt,
			LocalDateTime updatedAt
	) {
		private static DatasetDetail from(Dataset dataset) {
			return new DatasetDetail(
					dataset.getId(),
					dataset.getName(),
					dataset.getSourceType(),
					dataset.getFileUrl(),
					dataset.getColumnMapping(),
					dataset.getStatus(),
					dataset.getTotalCount(),
					dataset.getValidCount(),
					dataset.getInvalidCount(),
					dataset.getCreatedBy().getId(),
					dataset.getCreatedAt(),
					dataset.getUpdatedAt()
			);
		}
	}
}
