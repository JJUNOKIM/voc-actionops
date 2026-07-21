package com.vocactionops.backend.analysis.web;

import com.vocactionops.backend.analysis.application.AiCorrectionService;
import com.vocactionops.backend.analysis.application.AiCorrectionService.CorrectionView;
import com.vocactionops.backend.analysis.application.FeedbackAnalysisView;
import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.response.ApiResponse;
import com.vocactionops.backend.common.response.PageResponse;
import com.vocactionops.backend.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/feedbacks")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class FeedbackAnalysisController {

	private final AiCorrectionService correctionService;

	public FeedbackAnalysisController(AiCorrectionService correctionService) {
		this.correctionService = correctionService;
	}

	@PatchMapping("/{feedbackId}/analysis")
	public ApiResponse<FeedbackAnalysisView> correctAnalysis(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@PathVariable Long feedbackId,
			@Valid @RequestBody CorrectionRequest request
	) {
		return ApiResponse.success(
				correctionService.correct(
						authenticatedUser,
						feedbackId,
						request.fieldName(),
						request.correctedValue(),
						request.reason()
				),
				"AI 분석 결과가 수정되었습니다."
		);
	}

	@GetMapping("/{feedbackId}/corrections")
	public ApiResponse<PageResponse<CorrectionView>> corrections(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@PathVariable Long feedbackId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		return ApiResponse.success(correctionService.getCorrections(
				authenticatedUser,
				feedbackId,
				page,
				size
		));
	}

	public record CorrectionRequest(
			@NotBlank @Size(max = 50) String fieldName,
			@NotBlank @Size(max = 1000) String correctedValue,
			@NotBlank @Size(max = 500) String reason
	) {
	}
}
