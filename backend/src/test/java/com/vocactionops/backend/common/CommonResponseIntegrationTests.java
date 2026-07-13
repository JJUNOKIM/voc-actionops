package com.vocactionops.backend.common;

import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CommonResponseIntegrationTests.TestController.class)
@WithMockUser
class CommonResponseIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void returnsCommonSuccessResponse() throws Exception {
		mockMvc.perform(get("/test/common/success"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.name").value("sample"))
				.andExpect(jsonPath("$.message").value(nullValue()));
	}

	@Test
	void convertsCustomExceptionToErrorResponse() throws Exception {
		mockMvc.perform(get("/test/common/custom-error"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.data").value(nullValue()))
				.andExpect(jsonPath("$.message").value(ErrorCode.NOT_FOUND.message()))
				.andExpect(jsonPath("$.error.code").value(ErrorCode.NOT_FOUND.code()))
				.andExpect(jsonPath("$.error.details").isEmpty());
	}

	@Test
	void returnsFieldDetailsForValidationError() throws Exception {
		mockMvc.perform(post("/test/common/validation")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":""}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(ErrorCode.INVALID_REQUEST.message()))
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_REQUEST.code()))
				.andExpect(jsonPath("$.error.details[0].field").value("name"))
				.andExpect(jsonPath("$.error.details[0].message").value("name is required"));
	}

	@Test
	void hidesUnexpectedExceptionDetails() throws Exception {
		mockMvc.perform(get("/test/common/unexpected-error"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_SERVER_ERROR.message()))
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INTERNAL_SERVER_ERROR.code()));
	}

	@RestController
	@RequestMapping("/test/common")
	static class TestController {

		@GetMapping("/success")
		ApiResponse<Map<String, String>> success() {
			return ApiResponse.success(Map.of("name", "sample"));
		}

		@GetMapping("/custom-error")
		void customError() {
			throw new CustomException(ErrorCode.NOT_FOUND);
		}

		@PostMapping("/validation")
		ApiResponse<Map<String, String>> validate(@Valid @RequestBody TestRequest request) {
			return ApiResponse.success(Map.of("name", request.name()));
		}

		@GetMapping("/unexpected-error")
		void unexpectedError() {
			throw new IllegalStateException("internal detail");
		}
	}

	record TestRequest(
			@NotBlank(message = "name is required") String name
	) {
	}
}
