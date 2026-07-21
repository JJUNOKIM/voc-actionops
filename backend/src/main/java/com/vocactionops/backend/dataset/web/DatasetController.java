package com.vocactionops.backend.dataset.web;

import com.vocactionops.backend.analysis.job.application.AnalysisJobService;
import com.vocactionops.backend.analysis.job.application.AnalysisJobView;
import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.response.ApiResponse;
import com.vocactionops.backend.common.response.PageResponse;
import com.vocactionops.backend.config.OpenApiConfig;
import com.vocactionops.backend.dataset.application.DatasetQueryService;
import com.vocactionops.backend.dataset.application.DatasetQueryService.DatasetDetail;
import com.vocactionops.backend.dataset.application.DatasetQueryService.DatasetSummary;
import com.vocactionops.backend.dataset.application.DatasetUploadService;
import com.vocactionops.backend.dataset.application.DatasetUploadService.DatasetUploadResult;
import com.vocactionops.backend.dataset.application.DatasetValidationErrorQueryService;
import com.vocactionops.backend.dataset.application.DatasetValidationErrorQueryService.ValidationErrorView;
import com.vocactionops.backend.dataset.domain.DatasetStatus;
import com.vocactionops.backend.dataset.domain.SourceType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/datasets")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class DatasetController {

	private final DatasetQueryService datasetQueryService;
	private final DatasetUploadService datasetUploadService;
	private final DatasetValidationErrorQueryService validationErrorQueryService;
	private final AnalysisJobService analysisJobService;

	public DatasetController(
			DatasetQueryService datasetQueryService,
			DatasetUploadService datasetUploadService,
			DatasetValidationErrorQueryService validationErrorQueryService,
			AnalysisJobService analysisJobService
	) {
		this.datasetQueryService = datasetQueryService;
		this.datasetUploadService = datasetUploadService;
		this.validationErrorQueryService = validationErrorQueryService;
		this.analysisJobService = analysisJobService;
	}

	@GetMapping
	public ApiResponse<PageResponse<DatasetSummary>> datasets(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@RequestParam(required = false) SourceType sourceType,
			@RequestParam(required = false) DatasetStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		return ApiResponse.success(datasetQueryService.getDatasets(
				authenticatedUser,
				sourceType,
				status,
				page,
				size
		));
	}

	@GetMapping("/{datasetId}")
	public ApiResponse<DatasetDetail> dataset(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@PathVariable Long datasetId
	) {
		return ApiResponse.success(datasetQueryService.getDataset(authenticatedUser, datasetId));
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<DatasetUploadResult> upload(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@RequestParam("name") String name,
			@RequestParam("sourceType") SourceType sourceType,
			@RequestPart("file") MultipartFile file,
			@RequestPart("columnMapping") Map<String, String> columnMapping
	) {
		return ApiResponse.success(
				datasetUploadService.upload(
						authenticatedUser,
						name,
						sourceType,
						file,
						columnMapping
				),
				"CSV 업로드 및 검증이 완료되었습니다."
		);
	}

	@GetMapping("/{datasetId}/validation-errors")
	public ApiResponse<PageResponse<ValidationErrorView>> validationErrors(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@PathVariable Long datasetId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		return ApiResponse.success(validationErrorQueryService.getValidationErrors(
				authenticatedUser,
				datasetId,
				page,
				size
		));
	}

	@PostMapping("/{datasetId}/analyze")
	public ApiResponse<AnalysisJobView> analyze(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@PathVariable Long datasetId
	) {
		return ApiResponse.success(
				analysisJobService.start(authenticatedUser, datasetId),
				"AI 분석 작업이 시작되었습니다."
		);
	}

	@GetMapping("/{datasetId}/analysis-status")
	public ApiResponse<AnalysisJobView> analysisStatus(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@PathVariable Long datasetId
	) {
		return ApiResponse.success(analysisJobService.getStatus(
				authenticatedUser,
				datasetId
		));
	}
}
