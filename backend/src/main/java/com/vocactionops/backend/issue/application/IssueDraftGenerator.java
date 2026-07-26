package com.vocactionops.backend.issue.application;

import org.springframework.stereotype.Component;

@Component
public class IssueDraftGenerator {

	private static final int MAX_TITLE_LENGTH = 150;
	private static final int MAX_DESCRIPTION_LENGTH = 1000;

	public Draft generate(String summary, String content) {
		String normalizedSummary = normalizeWhitespace(requireText(summary, "summary"));
		String titleWithoutPunctuation = normalizedSummary
				.replaceFirst("[\\p{P}\\p{S}]+$", "")
				.trim();
		String title = titleWithoutPunctuation.isBlank()
				? normalizedSummary
				: titleWithoutPunctuation;
		return new Draft(
				truncate(title, MAX_TITLE_LENGTH),
				truncate(requireText(content, "content").trim(), MAX_DESCRIPTION_LENGTH)
		);
	}

	private static String normalizeWhitespace(String value) {
		return value.replaceAll("\\s+", " ").trim();
	}

	private static String truncate(String value, int maximumLength) {
		if (value.length() <= maximumLength) {
			return value;
		}
		int endIndex = maximumLength;
		if (Character.isHighSurrogate(value.charAt(endIndex - 1))) {
			endIndex--;
		}
		return value.substring(0, endIndex).trim();
	}

	private static String requireText(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
		return value;
	}

	public record Draft(String title, String description) {
	}
}
