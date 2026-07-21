import re

from app.schemas import FeedbackAnalysisRequest, ProviderAnalysis, Sentiment


class DeterministicAnalysisProvider:
    _CATEGORY_KEYWORDS = {
        "PAYMENT": ("결제", "쿠폰", "환불", "payment", "coupon", "refund"),
        "AUTHENTICATION": ("로그인", "인증", "비밀번호", "login", "auth", "password"),
        "DELIVERY": ("배송", "배달", "도착", "delivery", "shipping", "arrival"),
        "PERFORMANCE": ("느려", "속도", "멈춤", "slow", "latency", "freeze"),
        "USABILITY": ("불편", "어려", "복잡", "usability", "difficult", "confusing"),
        "FEATURE_REQUEST": ("추가", "기능", "원해", "feature", "wish", "request"),
    }
    _NEGATIVE_KEYWORDS = (
        "안 돼",
        "안돼",
        "오류",
        "실패",
        "불편",
        "최악",
        "느려",
        "error",
        "fail",
        "broken",
        "bad",
        "slow",
    )
    _POSITIVE_KEYWORDS = (
        "좋아",
        "만족",
        "편리",
        "감사",
        "great",
        "good",
        "satisfied",
        "helpful",
    )
    _URGENT_KEYWORDS = (
        "긴급",
        "지금",
        "즉시",
        "실패",
        "오류",
        "환불",
        "urgent",
        "immediately",
        "critical",
        "error",
    )

    @property
    def model_name(self) -> str:
        return "deterministic-v1"

    def analyze(self, request: FeedbackAnalysisRequest) -> ProviderAnalysis:
        content = request.content.lower()
        category, category_matched = self._classify_category(content)
        sentiment, sentiment_score = self._classify_sentiment(content, request.rating)
        urgency_score = self._calculate_urgency(content, request.rating)
        confidence_score = 0.65
        if request.rating is not None:
            confidence_score += 0.1
        if category_matched:
            confidence_score += 0.1

        return ProviderAnalysis(
            sentiment=sentiment,
            sentiment_score=sentiment_score,
            category=category,
            urgency_score=urgency_score,
            summary=self._summarize(request.content),
            confidence_score=round(min(confidence_score, 0.95), 4),
        )

    def _classify_category(self, content: str) -> tuple[str, bool]:
        for category, keywords in self._CATEGORY_KEYWORDS.items():
            if any(keyword in content for keyword in keywords):
                return category, True
        return "OTHER", False

    def _classify_sentiment(
        self,
        content: str,
        rating: float | None,
    ) -> tuple[Sentiment, float]:
        if rating is not None and rating <= 2:
            return Sentiment.NEGATIVE, round(max(-1.0, (rating - 3) / 2), 5)
        if rating is not None and rating >= 4:
            return Sentiment.POSITIVE, round(min(1.0, (rating - 3) / 2), 5)
        if any(keyword in content for keyword in self._NEGATIVE_KEYWORDS):
            return Sentiment.NEGATIVE, -0.75
        if any(keyword in content for keyword in self._POSITIVE_KEYWORDS):
            return Sentiment.POSITIVE, 0.75
        return Sentiment.NEUTRAL, 0.0

    def _calculate_urgency(self, content: str, rating: float | None) -> float:
        urgency = 0.35
        if any(keyword in content for keyword in self._URGENT_KEYWORDS):
            urgency = 0.85
        if rating is not None and rating <= 1:
            urgency = max(urgency, 0.75)
        elif rating is not None and rating <= 2:
            urgency = max(urgency, 0.6)
        return round(urgency, 4)

    def _summarize(self, content: str) -> str:
        normalized = re.sub(r"\s+", " ", content).strip()
        if len(normalized) <= 160:
            return normalized
        return normalized[:157].rstrip() + "..."
