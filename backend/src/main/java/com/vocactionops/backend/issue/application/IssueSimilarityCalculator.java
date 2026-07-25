package com.vocactionops.backend.issue.application;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class IssueSimilarityCalculator {

	private static final BigDecimal CATEGORY_WEIGHT = new BigDecimal("0.3500");
	private static final BigDecimal TEXT_WEIGHT = new BigDecimal("0.6500");
	private static final double CHARACTER_SIMILARITY_WEIGHT = 0.4;
	private static final double TOKEN_SIMILARITY_WEIGHT = 0.6;

	public Similarity calculate(
			String feedbackCategory,
			String feedbackText,
			String issueCategory,
			String issueText
	) {
		String normalizedFeedbackCategory = normalizeCategory(feedbackCategory);
		String normalizedIssueCategory = normalizeCategory(issueCategory);
		if (!normalizedFeedbackCategory.equals(normalizedIssueCategory)) {
			return new Similarity(
					false,
					BigDecimal.ZERO,
					BigDecimal.ZERO,
					BigDecimal.ZERO,
					BigDecimal.ZERO,
					BigDecimal.ZERO
			);
		}

		double characterSimilarity = cosineSimilarity(
				bigramFrequencies(normalizeText(feedbackText)),
				bigramFrequencies(normalizeText(issueText))
		);
		double tokenSimilarity = cosineSimilarity(
				tokenFrequencies(feedbackText),
				tokenFrequencies(issueText)
		);
		double rawTextSimilarity = CHARACTER_SIMILARITY_WEIGHT * characterSimilarity
				+ TOKEN_SIMILARITY_WEIGHT * tokenSimilarity;
		BigDecimal textSimilarity = scale(BigDecimal.valueOf(rawTextSimilarity));
		BigDecimal score = scale(CATEGORY_WEIGHT.add(
				TEXT_WEIGHT.multiply(BigDecimal.valueOf(rawTextSimilarity))
		));
		return new Similarity(
				true,
				CATEGORY_WEIGHT,
				scale(BigDecimal.valueOf(characterSimilarity)),
				scale(BigDecimal.valueOf(tokenSimilarity)),
				textSimilarity,
				score
		);
	}

	private static double cosineSimilarity(
			Map<String, Integer> leftFrequencies,
			Map<String, Integer> rightFrequencies
	) {
		if (leftFrequencies.isEmpty() || rightFrequencies.isEmpty()) {
			return 0;
		}

		double dotProduct = 0;
		double leftMagnitude = 0;
		double rightMagnitude = 0;
		for (Map.Entry<String, Integer> entry : leftFrequencies.entrySet()) {
			int frequency = entry.getValue();
			leftMagnitude += (double) frequency * frequency;
			dotProduct += (double) frequency * rightFrequencies.getOrDefault(entry.getKey(), 0);
		}
		for (int frequency : rightFrequencies.values()) {
			rightMagnitude += (double) frequency * frequency;
		}
		return dotProduct / Math.sqrt(leftMagnitude * rightMagnitude);
	}

	private static Map<String, Integer> bigramFrequencies(String value) {
		Map<String, Integer> frequencies = new HashMap<>();
		int[] codePoints = value.codePoints().toArray();
		if (codePoints.length == 1) {
			frequencies.put(value, 1);
			return frequencies;
		}
		for (int index = 0; index < codePoints.length - 1; index++) {
			String bigram = new String(codePoints, index, 2);
			frequencies.merge(bigram, 1, Integer::sum);
		}
		return frequencies;
	}

	private static Map<String, Integer> tokenFrequencies(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("text must not be blank");
		}
		String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
				.toLowerCase(Locale.ROOT);
		Map<String, Integer> frequencies = new HashMap<>();
		for (String token : normalized.split("[^\\p{L}\\p{N}]+")) {
			if (!token.isBlank()) {
				frequencies.merge(token, 1, Integer::sum);
			}
		}
		return frequencies;
	}

	private static String normalizeCategory(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("category must not be blank");
		}
		return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
				.toUpperCase(Locale.ROOT);
	}

	private static String normalizeText(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("text must not be blank");
		}
		String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
				.toLowerCase(Locale.ROOT);
		StringBuilder result = new StringBuilder(normalized.length());
		normalized.codePoints()
				.filter(Character::isLetterOrDigit)
				.forEach(result::appendCodePoint);
		return result.toString();
	}

	private static BigDecimal scale(BigDecimal value) {
		return value.setScale(4, RoundingMode.HALF_UP);
	}

	public record Similarity(
			boolean categoryMatched,
			BigDecimal categoryScore,
			BigDecimal characterSimilarity,
			BigDecimal tokenSimilarity,
			BigDecimal textSimilarity,
			BigDecimal score
	) {
	}
}
