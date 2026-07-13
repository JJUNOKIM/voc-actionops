package com.vocactionops.backend.common.exception;

import com.vocactionops.backend.common.response.ErrorResponse;
import com.vocactionops.backend.common.response.ErrorResponse.ValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Comparator;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(CustomException.class)
	public ResponseEntity<ErrorResponse> handleCustomException(CustomException exception) {
		return createResponse(exception.errorCode());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidationException(
			MethodArgumentNotValidException exception
	) {
		List<ValidationError> details = exception.getBindingResult()
				.getFieldErrors()
				.stream()
				.map(fieldError -> new ValidationError(
						fieldError.getField(),
						fieldError.getDefaultMessage()
				))
				.sorted(Comparator.comparing(ValidationError::field))
				.toList();

		ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
		return ResponseEntity
				.status(errorCode.httpStatus())
				.body(ErrorResponse.from(errorCode, details));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleUnreadableRequest() {
		return createResponse(ErrorCode.INVALID_REQUEST);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
		log.error("Unhandled exception", exception);
		return createResponse(ErrorCode.INTERNAL_SERVER_ERROR);
	}

	private ResponseEntity<ErrorResponse> createResponse(ErrorCode errorCode) {
		return ResponseEntity
				.status(errorCode.httpStatus())
				.body(ErrorResponse.from(errorCode));
	}
}
