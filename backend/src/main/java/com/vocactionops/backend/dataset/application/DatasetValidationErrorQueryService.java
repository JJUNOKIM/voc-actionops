package com.vocactionops.backend.dataset.application;

import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.common.response.PageResponse;
import com.vocactionops.backend.dataset.domain.DatasetValidationError;
import com.vocactionops.backend.dataset.domain.DatasetValidationErrorCode;
import com.vocactionops.backend.dataset.repository.DatasetRepository;
import com.vocactionops.backend.dataset.repository.DatasetValidationErrorRepository;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

import static com.vocactionops.backend.common.web.PageRequestFactory.ordered;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN', 'PM')")
public class DatasetValidationErrorQueryService {

	private final DatasetRepository datasetRepository;
	private final DatasetValidationErrorRepository validationErrorRepository;

	public DatasetValidationErrorQueryService(
			DatasetRepository datasetRepository,
			DatasetValidationErrorRepository validationErrorRepository
	) {
		this.datasetRepository = datasetRepository;
		this.validationErrorRepository = validationErrorRepository;
	}

	public PageResponse<ValidationErrorView> getValidationErrors(
			AuthenticatedUser authenticatedUser,
			Long datasetId,
			int page,
			int size
	) {
		datasetRepository.findByIdAndOrganizationId(datasetId, authenticatedUser.organizationId())
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

		Sort sort = Sort.by(Sort.Direction.ASC, "rowNumber")
				.and(Sort.by(Sort.Direction.ASC, "id"));
		return PageResponse.from(validationErrorRepository.findPageByDatasetAndOrganization(
				datasetId,
				authenticatedUser.organizationId(),
				ordered(page, size, sort)
		).map(ValidationErrorView::from));
	}

	public record ValidationErrorView(
			Long id,
			int rowNumber,
			String fieldName,
			DatasetValidationErrorCode errorCode,
			String errorMessage,
			Map<String, String> rawRow,
			LocalDateTime createdAt
	) {
		private static ValidationErrorView from(DatasetValidationError error) {
			return new ValidationErrorView(
					error.getId(),
					error.getRowNumber(),
					error.getFieldName(),
					error.getErrorCode(),
					error.getErrorMessage(),
					error.getRawRow(),
					error.getCreatedAt()
			);
		}
	}
}
