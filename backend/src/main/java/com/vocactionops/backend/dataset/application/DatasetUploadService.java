package com.vocactionops.backend.dataset.application;

import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.dataset.application.CsvFeedbackParser.CsvParseResult;
import com.vocactionops.backend.dataset.config.DatasetUploadProperties;
import com.vocactionops.backend.dataset.domain.Dataset;
import com.vocactionops.backend.dataset.domain.DatasetStatus;
import com.vocactionops.backend.dataset.domain.DatasetValidationError;
import com.vocactionops.backend.dataset.domain.SourceType;
import com.vocactionops.backend.dataset.repository.DatasetRepository;
import com.vocactionops.backend.dataset.repository.DatasetValidationErrorRepository;
import com.vocactionops.backend.dataset.storage.LocalDatasetFileStorage;
import com.vocactionops.backend.dataset.storage.LocalDatasetFileStorage.StoredFile;
import com.vocactionops.backend.feedback.domain.Feedback;
import com.vocactionops.backend.feedback.repository.FeedbackRepository;
import com.vocactionops.backend.user.domain.User;
import com.vocactionops.backend.user.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DatasetUploadService {

	private final UserRepository userRepository;
	private final DatasetRepository datasetRepository;
	private final FeedbackRepository feedbackRepository;
	private final DatasetValidationErrorRepository validationErrorRepository;
	private final CsvFeedbackParser csvFeedbackParser;
	private final LocalDatasetFileStorage fileStorage;
	private final DatasetUploadProperties properties;

	public DatasetUploadService(
			UserRepository userRepository,
			DatasetRepository datasetRepository,
			FeedbackRepository feedbackRepository,
			DatasetValidationErrorRepository validationErrorRepository,
			CsvFeedbackParser csvFeedbackParser,
			LocalDatasetFileStorage fileStorage,
			DatasetUploadProperties properties
	) {
		this.userRepository = userRepository;
		this.datasetRepository = datasetRepository;
		this.feedbackRepository = feedbackRepository;
		this.validationErrorRepository = validationErrorRepository;
		this.csvFeedbackParser = csvFeedbackParser;
		this.fileStorage = fileStorage;
		this.properties = properties;
	}

	@Transactional
	@PreAuthorize("hasAnyRole('ADMIN', 'PM')")
	public DatasetUploadResult upload(
			AuthenticatedUser authenticatedUser,
			String name,
			SourceType sourceType,
			MultipartFile file,
			Map<String, String> columnMapping
	) {
		byte[] fileContent = readAndValidateFile(name, sourceType, file);
		User creator = userRepository.findByIdAndOrganizationId(
					authenticatedUser.userId(),
					authenticatedUser.organizationId()
				)
				.orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
		CsvParseResult parseResult = csvFeedbackParser.parse(fileContent, columnMapping);
		StoredFile storedFile = fileStorage.store(authenticatedUser.organizationId(), fileContent);
		registerRollbackCleanup(storedFile);

		Dataset dataset = new Dataset(
				creator.getOrganization(),
				name,
				sourceType,
				storedFile.url(),
				parseResult.columnMapping(),
				creator
		);
		dataset.startValidation();
		datasetRepository.save(dataset);

		List<Feedback> feedbacks = parseResult.feedbacks().stream()
				.map(parsed -> new Feedback(
						creator.getOrganization(),
						dataset,
						parsed.externalId(),
						sourceType,
						parsed.customerSegment(),
						parsed.productName(),
						parsed.rating(),
						parsed.content(),
						parsed.language(),
						parsed.feedbackCreatedAt()
					))
				.toList();
		feedbackRepository.saveAll(feedbacks);

		List<DatasetValidationError> validationErrors = parseResult.validationErrors().stream()
				.map(error -> new DatasetValidationError(
						dataset,
						error.rowNumber(),
						error.fieldName(),
						error.errorCode(),
						error.message(),
						error.rawRow()
					))
				.toList();
		validationErrorRepository.saveAll(validationErrors);

		int validCount = feedbacks.size();
		dataset.completeValidation(
				parseResult.totalCount(),
				validCount,
				parseResult.invalidCount()
		);
		datasetRepository.flush();

		return new DatasetUploadResult(
				dataset.getId(),
				dataset.getStatus(),
				parseResult.totalCount(),
				validCount,
				parseResult.invalidCount()
		);
	}

	private void registerRollbackCleanup(StoredFile storedFile) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status != TransactionSynchronization.STATUS_COMMITTED) {
					fileStorage.deleteQuietly(storedFile);
				}
			}
		});
	}

	private byte[] readAndValidateFile(String name, SourceType sourceType, MultipartFile file) {
		if (name == null || name.isBlank() || name.trim().length() > 150
				|| sourceType == null || file == null || file.isEmpty()) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		String originalFilename = file.getOriginalFilename();
		if (originalFilename == null
				|| !originalFilename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		if (file.getSize() > properties.maxFileSizeBytes()) {
			throw new CustomException(ErrorCode.CSV_VALIDATION_FAILED);
		}
		try {
			return file.getBytes();
		} catch (IOException exception) {
			throw new CustomException(ErrorCode.CSV_VALIDATION_FAILED);
		}
	}

	public record DatasetUploadResult(
			Long datasetId,
			DatasetStatus status,
			int totalCount,
			int validCount,
			int invalidCount
	) {
	}
}
