# VOC ActionOps 도메인 모델

## 1. 핵심 도메인 개념

## 1.1 Organization

서비스를 사용하는 회사 또는 팀 단위다.

하나의 Organization은 여러 User, Dataset, Feedback, Issue를 가진다.

주요 속성:

* id
* name
* created_at
* updated_at

## 1.2 User

조직에 소속된 내부 사용자다.

사용자는 역할에 따라 접근 가능한 기능이 다르다.

역할:

* ADMIN
* PM
* CS
* DEVELOPER
* VIEWER

주요 속성:

* id
* organization_id
* email
* password_hash
* name
* role
* created_at
* updated_at

## 1.3 Dataset

업로드된 고객 피드백 파일 또는 수집 단위다.

예를 들어 2026년 7월 앱 리뷰 CSV, 2026년 2분기 고객센터 문의 CSV 등이 하나의 Dataset이 될 수 있다.

주요 속성:

* id
* organization_id
* name
* source_type
* file_url
* column_mapping_json
* status
* total_count
* valid_count
* invalid_count
* created_by
* created_at
* updated_at

상태:

* UPLOADED
* VALIDATING
* VALIDATED
* ANALYZING
* ANALYZED
* FAILED

출처 유형:

* SHOP_REVIEW
* APP_REVIEW
* CS_TICKET
* SURVEY
* INTERNAL_TEST
* ETC

## 1.4 DatasetValidationError

CSV 검증 과정에서 발견한 행 단위 오류다.

주요 속성:

* id
* dataset_id
* row_number
* field_name
* error_code
* error_message
* raw_row_json
* created_at

## 1.5 Feedback

고객이 남긴 원문 피드백이다.

리뷰, 문의, 설문 응답, 앱 리뷰 등이 Feedback에 해당한다.

주요 속성:

* id
* organization_id
* dataset_id
* external_id
* source_type
* customer_segment
* product_name
* rating
* content
* language
* feedback_created_at
* ingested_at

## 1.6 FeedbackAnalysis

Feedback에 대한 AI 분석 결과다.

하나의 Feedback은 하나의 최신 FeedbackAnalysis를 가진다. 분석 이력을 여러 개 남길지 여부는 추후 확장으로 둔다.

주요 속성:

* id
* feedback_id
* sentiment
* sentiment_score
* category
* urgency_score
* summary
* confidence_score
* model_name
* analyzed_at
* status
* error_message
* version

감성 값:

* POSITIVE
* NEUTRAL
* NEGATIVE

분석 상태:

* PENDING
* SUCCESS
* FAILED

새 분석은 PENDING으로 시작하며 SUCCESS 또는 FAILED로 종료된다. 실패한 분석만 다시 PENDING으로 전환해 재시도할 수 있다. `version`은 동시에 들어온 완료·실패·수정 요청이 서로의 결과를 덮어쓰지 않도록 사용한다.

## 1.7 FeedbackEmbedding

유사 피드백을 찾기 위한 임베딩 정보다.

주요 속성:

* id
* feedback_id
* embedding_model
* embedding_json
* content_hash
* created_at

하나의 Feedback은 최대 하나의 최신 FeedbackEmbedding을 가진다. MVP에서는 MySQL JSON으로 저장하고 추후 OpenSearch 또는 벡터 데이터베이스로 교체할 수 있게 둔다.

## 1.8 Issue

여러 Feedback이 모여 만들어진 반복 문제 단위다.

예를 들어 “쿠폰 적용 후 결제 실패”, “배송 지연 문의 증가”, “앱 로그인 오류” 등이 Issue가 될 수 있다.

주요 속성:

* id
* organization_id
* title
* description
* category
* priority
* priority_score
* status
* assignee_id
* first_seen_at
* last_seen_at
* created_at
* updated_at

상태:

* NEW
* TRIAGED
* ASSIGNED
* IN_PROGRESS
* RESOLVED
* MONITORING
* CLOSED

우선순위:

* P0
* P1
* P2
* P3

## 1.9 IssueFeedback

Issue와 Feedback의 연결 테이블이다.

하나의 Issue는 여러 Feedback과 연결될 수 있고, 하나의 Feedback은 원칙적으로 하나의 대표 Issue에 연결된다. 다만 추후 복수 Issue 연결 가능성을 고려해 N:M 구조로 설계한다.

주요 속성:

* id
* issue_id
* feedback_id
* similarity_score
* is_representative
* linked_by
* created_at

## 1.10 Action

Issue를 해결하기 위한 작업 항목이다.

예를 들어 “결제 로그 확인”, “쿠폰 적용 API 재현 테스트”, “FAQ 문구 수정”, “배송 지연 안내 메시지 추가” 등이 Action이 될 수 있다.

주요 속성:

* id
* issue_id
* title
* description
* status
* assignee_id
* due_date
* created_at
* updated_at
* completed_at

상태:

* TODO
* IN_PROGRESS
* DONE
* CANCELED

## 1.11 IssueComment

이슈 처리 과정에서 사용자가 남기는 협업 기록이다.

주요 속성:

* id
* issue_id
* user_id
* content
* created_at
* updated_at

## 1.12 AiCorrection

AI 분석 결과를 사용자가 수정한 기록이다.

AI 결과를 사람이 검토하고 수정할 수 있어야 하므로, 수정 전 값과 수정 후 값을 모두 저장한다.

주요 속성:

* id
* feedback_id
* field_name
* ai_value
* corrected_value
* reason
* corrected_by
* created_at

수정 가능 필드:

* sentiment
* category
* urgency_score

`ai_value`에는 최초 AI 값이 아니라 각 수정 시점의 수정 직전 값을 저장한다. 이슈 연결 변경은 Issue 도메인의 연결 이력으로 별도 관리한다.

## 1.13 IssueMetricsSnapshot

Issue의 일별 지표를 저장하는 snapshot 테이블이다.

대시보드 집계 성능을 높이고, 이슈 해결 전후 지표를 비교하기 위해 사용한다.

주요 속성:

* id
* issue_id
* snapshot_date
* feedback_count
* negative_count
* average_sentiment_score
* average_urgency_score
* priority_score
* unresolved_action_count
* created_at

## 2. 도메인 관계

Organization 1 : N User

Organization 1 : N Dataset

Organization 1 : N Feedback

Organization 1 : N Issue

User 1 : N Dataset

Dataset 1 : N Feedback

Dataset 1 : N DatasetValidationError

Feedback 1 : 1 FeedbackAnalysis

Feedback 1 : 1 FeedbackEmbedding

Issue N : M Feedback

Issue 1 : N Action

Issue 1 : N IssueComment

Feedback 1 : N AiCorrection

Issue 1 : N IssueMetricsSnapshot

## 3. 핵심 비즈니스 규칙

## 3.1 조직 데이터 분리

모든 핵심 데이터는 organization_id를 기준으로 분리한다.

사용자는 자신이 속한 조직의 데이터만 조회할 수 있다.

## 3.2 피드백 분석

Feedback이 저장되면 AI 분석 대상이 된다.

AI 분석 결과는 FeedbackAnalysis에 저장한다.

분석 실패 시 status를 FAILED로 변경하고 error_message를 저장한다.

SUCCESS 또는 PENDING 상태에서 중복 분석을 시작하지 않으며, FAILED 상태만 재시도한다.

## 3.3 이슈 생성

분석된 Feedback은 기존 Issue와 유사도 비교를 수행한다.

기존 Issue와 유사도가 기준 이상이면 해당 Issue에 연결한다.

기준 미만이면 새 Issue를 생성한다.

## 3.4 대표 피드백

Issue에 연결된 Feedback 중 하나 이상은 대표 피드백으로 지정할 수 있다.

대표 피드백은 이슈 상세 화면에서 원문 근거로 사용한다.

## 3.5 AI 수정 이력

사용자가 AI 분석 결과를 수정하면 AiCorrection에 수정 이력을 저장한다.

수정 이력은 삭제하지 않는다.

분석이 SUCCESS 상태일 때만 수정할 수 있고, 수정 전후 값이 같으면 이력을 만들지 않는다.

## 3.6 이슈 상태 전이

Issue는 다음 순서로 상태가 변경된다.

NEW
→ TRIAGED
→ ASSIGNED
→ IN_PROGRESS
→ RESOLVED
→ MONITORING
→ CLOSED

권한 없는 사용자는 상태를 변경할 수 없다.

## 3.7 우선순위 계산

Issue의 우선순위 점수는 다음 요소를 기반으로 계산한다.

* 피드백 빈도
* 부정 감성 비율
* 긴급도 평균
* 최근 증가율
* 고객 영향도

## 3.8 처리 후 모니터링

Issue가 RESOLVED 상태가 되면 MONITORING 상태를 거쳐 관련 피드백이 감소했는지 확인한다.

관련 피드백이 계속 발생하면 재오픈하거나 상태를 다시 IN_PROGRESS로 변경할 수 있다.

## 4. 설계상 중요한 결정

## 4.1 Feedback과 Issue를 분리한 이유

Feedback은 고객이 남긴 개별 원문이고, Issue는 반복적으로 발생하는 문제 단위다.

피드백을 이슈와 분리하면 개별 고객 의견을 보존하면서도 운영자는 문제 단위로 대응할 수 있다.

## 4.2 FeedbackAnalysis를 Feedback과 분리한 이유

원문 피드백과 AI 분석 결과는 성격이 다르다.

Feedback은 원본 데이터이고, FeedbackAnalysis는 모델과 프롬프트에 따라 달라질 수 있는 분석 결과다.

따라서 두 데이터를 분리해 저장한다.

## 4.3 AiCorrection을 별도 테이블로 둔 이유

AI 분석 결과는 틀릴 수 있다.

수정 이력을 별도 테이블로 관리하면 AI 결과의 신뢰도와 사람의 보정 과정을 추적할 수 있다.

## 4.4 IssueMetricsSnapshot을 둔 이유

대시보드에서 매번 전체 Feedback과 Issue를 집계하면 성능 문제가 발생할 수 있다.

일별 snapshot을 저장하면 대시보드 조회 성능을 개선하고, 이슈 해결 전후 지표도 비교할 수 있다.

## 5. MVP 기준 도메인 범위

MVP에서는 다음 도메인을 우선 구현한다.

* Organization
* User
* Dataset
* DatasetValidationError
* Feedback
* FeedbackAnalysis
* Issue
* IssueFeedback
* Action
* AiCorrection

FeedbackEmbedding, IssueComment는 2차 구현에서 추가한다.

IssueMetricsSnapshot은 성능 개선 단계에서 추가한다.
