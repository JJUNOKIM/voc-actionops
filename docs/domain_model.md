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
* resolved_at
* created_at
* updated_at
* version

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

`priority_score`는 자동 계산 전에는 비어 있을 수 있다. 수동 생성 시 선택한 우선순위 등급만 먼저 저장한다.

`resolved_at`은 Issue가 RESOLVED 상태가 된 시각이다. 이후 MONITORING과 CLOSED 상태에서도 유지하며, MONITORING 중 문제가 재발해 IN_PROGRESS로 돌아가면 비운다. 다시 해결되면 새로운 해결 시각을 기록한다.

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

수동 연결은 `linked_by`를 MANUAL로 저장하며 유사도 점수는 비워 둔다. 추천 후보를 사용자가 확정한 연결은 `linked_by`를 AI로 저장하고 확정 시점의 유사도 점수를 함께 남긴다. 신규 이슈 초안을 확정할 때 최초 피드백은 해당 이슈를 만든 기준 데이터이므로 `linked_by`를 AI, `similarity_score`를 1.0000, `is_representative`를 true로 저장한다. 피드백이 연결될 때 이슈의 `first_seen_at`과 `last_seen_at`을 원문 발생 시각 기준으로 갱신한다.

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
* version

상태:

* TODO
* IN_PROGRESS
* DONE
* CANCELED

액션은 TODO에서 시작한다. TODO는 IN_PROGRESS 또는 CANCELED로, IN_PROGRESS는 DONE 또는 CANCELED로 변경할 수 있다. DONE 전환 시 `completed_at`을 기록한다.

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
* analyzed_feedback_count
* negative_feedback_count
* average_sentiment_score
* average_urgency_score
* priority_score
* unresolved_action_count
* created_at
* updated_at
* version

`issue_id`와 `snapshot_date` 조합은 유일하다. 같은 날짜의 수집을 다시 실행하면 기존 snapshot을 갱신한다. 부정 비율은 미분석 피드백의 영향을 받지 않도록 `analyzed_feedback_count`를 분모로 계산한다.

snapshot은 매일 23:55 KST에 생성하며 ADMIN과 PM은 장애 복구나 당일 확인을 위해 수동으로 다시 생성할 수 있다. 과거 시점의 액션 상태는 현재 데이터만으로 정확히 복원할 수 없으므로 누락일을 임의 값으로 채우지 않는다.

## 1.14 RefreshToken

access token 재발급과 로그아웃을 위한 서버 측 세션 정보다.

주요 속성:

* id
* user_id
* token_hash
* family_id
* expires_at
* used_at
* revoked_at
* replaced_by_token_id
* created_at

클라이언트에는 256비트 난수 원문을 전달하고 DB에는 SHA-256 해시만 저장한다. rotation으로 발급된 토큰은 같은 family를 유지하며 이전 토큰의 `used_at`과 `replaced_by_token_id`를 기록한다.

## 2. 도메인 관계

Organization 1 : N User

Organization 1 : N Dataset

Organization 1 : N Feedback

Organization 1 : N Issue

User 1 : N Dataset

User 1 : N RefreshToken

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

## 3.3 이슈 후보 추천과 연결

SUCCESS 상태로 분석된 Feedback은 같은 조직의 닫히지 않은 기존 Issue와 유사도를 비교할 수 있다. 비교 대상은 분석 카테고리와 Issue 카테고리가 같은 최근 이슈 최대 100개로 제한한다.

유사도 점수는 카테고리 일치 35%와 텍스트 유사도 65%를 합산한다. 텍스트 유사도는 한글과 영문을 함께 처리하기 위해 정규화된 문자 bigram 코사인 유사도 40%와 단어 코사인 유사도 60%로 계산한다. 최종 점수가 0.45 이상인 이슈만 후보로 반환한다.

후보 조회 결과에는 카테고리, 문자, 단어, 결합 텍스트 점수를 각각 제공해 추천 근거를 확인할 수 있게 한다. 실제 연결은 자동으로 수행하지 않고 ADMIN, PM, CS 사용자가 확정한다. 확정 요청에서는 최신 분석 결과로 점수를 다시 계산하고 기준을 만족할 때만 AI 추천 출처와 점수를 IssueFeedback에 저장한다.

기준을 만족하는 기존 후보가 없고 아직 다른 Issue에 연결되지 않은 Feedback은 신규 Issue 초안을 조회할 수 있다. 초안은 FeedbackAnalysis의 summary를 제목으로, Feedback 원문을 설명으로 사용하며 저장하지 않고 요청 시 생성한다. 사용자는 제목과 설명을 편집할 수 있지만 category는 확정 시점의 최신 분석값을 사용한다.

초안 응답에는 FeedbackAnalysis의 version을 포함한다. ADMIN 또는 PM이 확정할 때 버전, 기존 연결, 기존 후보를 다시 확인하며 하나라도 변경되었으면 Issue를 만들지 않는다. 같은 Feedback의 동시 확정은 Feedback과 FeedbackAnalysis 행을 잠가 하나의 요청만 생성과 최초 연결을 완료하도록 한다. Issue 생성, 대표 Feedback 연결, 우선순위 계산은 한 트랜잭션에서 처리한다.

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

MONITORING 상태에서 같은 문제가 재발하면 IN_PROGRESS로 되돌릴 수 있다. ASSIGNED 이후 상태는 담당자가 지정된 경우에만 허용한다.

RESOLVED 전환 시 `resolved_at`을 기록해 생성부터 해결까지의 실제 처리 시간을 계산한다. MONITORING에서 IN_PROGRESS로 돌아가면 이전 값을 비워 재해결 시간을 별도로 측정한다.

권한 없는 사용자는 상태를 변경할 수 없다.

## 3.7 우선순위 계산

Issue에 연결된 Feedback 중 하나 이상이 분석 완료되면 다음 계산식으로 `priority_score`를 갱신한다.

* 피드백 빈도: 최대 30점. 연결 피드백 20건에서 최대가 되며 그 이상은 30점으로 고정한다.
* 부정 감성 비율: 최대 35점. 분석 완료 건 중 `NEGATIVE` 비율을 반영한다.
* 평균 긴급도: 최대 35점. 분석 완료 건의 `urgency_score` 평균을 반영한다.

점수는 소수점 둘째 자리로 반올림하고 80점 이상은 P0, 60점 이상은 P1, 40점 이상은 P2, 그 미만은 P3로 분류한다. 분석 완료 건이 없으면 수동 생성 시 지정한 우선순위를 유지하고 `priority_score`는 비워 둔다.

피드백 연결, 피드백 분석 완료, 감성 또는 긴급도 보정 시 연결된 이슈의 점수를 같은 트랜잭션에서 다시 계산한다.

최근 증가율과 고객 영향도는 시계열 snapshot과 고객 영향 기준이 추가된 이후 계산식에 반영한다.

## 3.8 처리 후 모니터링

Issue가 RESOLVED 상태가 되면 MONITORING 상태를 거쳐 관련 피드백이 감소했는지 확인한다.

관련 피드백이 계속 발생하면 재오픈하거나 상태를 다시 IN_PROGRESS로 변경할 수 있다.

## 3.9 Refresh token rotation

로그인 시 access token과 refresh token을 함께 발급한다.

재발급에 사용한 refresh token은 다시 사용할 수 없다. 사용한 토큰이 재사용되면 같은 family의 활성 토큰을 모두 폐기하고, 로그아웃도 같은 방식으로 family 전체를 폐기한다.

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

## 4.5 Refresh token 원문을 저장하지 않는 이유

DB가 노출되더라도 refresh token을 바로 사용할 수 없도록 복구 불가능한 해시만 저장한다.

rotation 이력을 남기면 정상적인 연속 재발급과 이미 사용한 토큰의 재사용을 구분해 세션 family를 폐기할 수 있다.

## 5. MVP 기준 도메인 범위

MVP에서는 다음 도메인을 우선 구현한다.

* Organization
* User
* RefreshToken
* Dataset
* DatasetValidationError
* Feedback
* FeedbackAnalysis
* Issue
* IssueFeedback
* Action
* AiCorrection

FeedbackEmbedding, IssueComment는 2차 구현에서 추가한다.

IssueMetricsSnapshot은 일별 이슈 추이와 최근 증가율 조회에 사용한다.
