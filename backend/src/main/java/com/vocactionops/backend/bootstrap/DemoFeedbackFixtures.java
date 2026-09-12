package com.vocactionops.backend.bootstrap;

import com.vocactionops.backend.analysis.domain.Sentiment;

import java.math.BigDecimal;
import java.util.List;

final class DemoFeedbackFixtures {

	private DemoFeedbackFixtures() {
	}

	static List<FeedbackSeed> feedbackSeeds() {
		return List.of(
				negative("DEMO-VOC-001", "쿠폰을 적용하면 결제가 계속 실패해요.", "신규고객", "1.0", 11, 9, 15, "-0.94", "0.98", "쿠폰 적용 시 결제가 완료되지 않음"),
				negative("DEMO-VOC-002", "결제 완료 버튼을 눌렀는데 오류가 나고 주문이 생성되지 않았습니다.", "일반고객", "1.0", 10, 11, 30, "-0.91", "0.95", "결제 직후 주문 생성에 실패함"),
				negative("DEMO-VOC-003", "쿠폰 결제 실패 후 포인트만 차감되었습니다.", "VIP", "1.0", 9, 8, 45, "-0.97", "1.00", "실패한 결제에서 포인트가 차감됨"),
				negative("DEMO-VOC-004", "결제 화면이 멈춰서 다시 시도해야 했어요.", "일반고객", "2.0", 8, 14, 20, "-0.76", "0.92", "결제 화면이 멈추고 재시도가 필요함"),
				negative("DEMO-VOC-005", "카드 승인은 됐는데 앱에서는 결제 실패로 표시됩니다.", "VIP", "1.0", 6, 10, 5, "-0.95", "0.99", "카드 승인과 앱 결제 상태가 일치하지 않음"),
				negative("DEMO-VOC-006", "쿠폰을 해제해야만 주문할 수 있어서 불편합니다.", "일반고객", "2.0", 5, 16, 40, "-0.71", "0.88", "쿠폰 사용 시에만 주문을 완료할 수 없음"),
				negative("DEMO-VOC-007", "앱 업데이트 뒤 결제 버튼이 눌리지 않습니다.", "신규고객", "1.0", 3, 12, 5, "-0.88", "0.93", "업데이트 후 결제 버튼이 동작하지 않음"),
				negative("DEMO-VOC-008", "한 번 결제했는데 카드 알림이 두 번 왔습니다.", "VIP", "1.0", 1, 18, 25, "-0.96", "1.00", "한 주문에서 카드 승인이 중복 발생함"),
				negative("DEMO-VOC-009", "로그인 인증 문자가 오지 않아 앱을 사용할 수 없습니다.", "신규고객", "2.0", 7, 13, 50, "-0.84", "0.88", "로그인 인증 문자를 받지 못함", "ACCOUNT"),
				negative("DEMO-VOC-010", "비밀번호를 바꾼 뒤 로그인이 계속 실패합니다.", "일반고객", "1.0", 2, 9, 35, "-0.89", "0.91", "비밀번호 변경 후 로그인이 거부됨", "ACCOUNT"),
				negative("DEMO-VOC-011", "배송 예정일이 지났는데 아직 도착하지 않았습니다.", "VIP", "2.0", 5, 15, 0, "-0.79", "0.76", "예정일이 지나도 배송이 완료되지 않음", "DELIVERY"),
				negative("DEMO-VOC-012", "배송 조회가 느려서 현재 위치를 확인하기 어렵습니다.", "일반고객", "2.0", 2, 17, 10, "-0.63", "0.58", "배송 위치 정보 반영이 지연됨", "DELIVERY"),
				positive("DEMO-VOC-013", "간편 로그인 기능이 편리하고 좋아요.", "일반고객", "5.0", 1, 10, 10, "0.89", "0.10", "간편 로그인 사용성이 좋음", "ACCOUNT"),
				neutral("DEMO-VOC-014", "자주 사는 상품을 저장하는 기능을 추가해 주세요.", "VIP", "4.0", 0, 15, 0, "0.08", "0.24", "자주 구매하는 상품 저장 기능을 요청함", "PRODUCT")
		);
	}

	private static FeedbackSeed negative(
			String externalId,
			String content,
			String customerSegment,
			String rating,
			int daysAgo,
			int hour,
			int minute,
			String sentimentScore,
			String urgencyScore,
			String summary
	) {
		return negative(
				externalId, content, customerSegment, rating, daysAgo, hour, minute,
				sentimentScore, urgencyScore, summary, "PAYMENT"
		);
	}

	private static FeedbackSeed negative(
			String externalId,
			String content,
			String customerSegment,
			String rating,
			int daysAgo,
			int hour,
			int minute,
			String sentimentScore,
			String urgencyScore,
			String summary,
			String category
	) {
		return seed(
				externalId, content, customerSegment, rating, daysAgo, hour, minute,
				Sentiment.NEGATIVE, sentimentScore, urgencyScore, summary, category
		);
	}

	private static FeedbackSeed positive(
			String externalId,
			String content,
			String customerSegment,
			String rating,
			int daysAgo,
			int hour,
			int minute,
			String sentimentScore,
			String urgencyScore,
			String summary,
			String category
	) {
		return seed(
				externalId, content, customerSegment, rating, daysAgo, hour, minute,
				Sentiment.POSITIVE, sentimentScore, urgencyScore, summary, category
		);
	}

	private static FeedbackSeed neutral(
			String externalId,
			String content,
			String customerSegment,
			String rating,
			int daysAgo,
			int hour,
			int minute,
			String sentimentScore,
			String urgencyScore,
			String summary,
			String category
	) {
		return seed(
				externalId, content, customerSegment, rating, daysAgo, hour, minute,
				Sentiment.NEUTRAL, sentimentScore, urgencyScore, summary, category
		);
	}

	private static FeedbackSeed seed(
			String externalId,
			String content,
			String customerSegment,
			String rating,
			int daysAgo,
			int hour,
			int minute,
			Sentiment sentiment,
			String sentimentScore,
			String urgencyScore,
			String summary,
			String category
	) {
		return new FeedbackSeed(
				externalId,
				content,
				customerSegment,
				decimal(rating),
				daysAgo,
				hour,
				minute,
				sentiment,
				decimal(sentimentScore),
				category,
				decimal(urgencyScore),
				summary,
				decimal("0.9600")
		);
	}

	private static BigDecimal decimal(String value) {
		return new BigDecimal(value);
	}

	record FeedbackSeed(
			String externalId,
			String content,
			String customerSegment,
			BigDecimal rating,
			int daysAgo,
			int hour,
			int minute,
			Sentiment sentiment,
			BigDecimal sentimentScore,
			String category,
			BigDecimal urgencyScore,
			String summary,
			BigDecimal confidenceScore
	) {
	}
}
