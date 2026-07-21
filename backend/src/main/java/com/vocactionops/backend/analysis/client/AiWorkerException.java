package com.vocactionops.backend.analysis.client;

public class AiWorkerException extends RuntimeException {

	public AiWorkerException(String message) {
		super(message);
	}

	public AiWorkerException(String message, Throwable cause) {
		super(message, cause);
	}
}
