# VOC ActionOps API 명세 초안

## 1. 작성 시작

API 명세서 초안

MVP에서 필요한 흐름부터 잡고 구현 과정에서 Swagger와 함께 계속 보완하는 방식으로 진행할 예정이다.

API 설계 기준은 다음과 같다.

* 프론트엔드에서 실제로 필요한 화면 흐름을 기준으로 API 나눔
* 조회, 생성, 수정, 상태 변경 분리
* 오래 걸리는 작업은 요청 안에서 바로 처리하지 않고 비동기 처리로 넘겨서 처리
* 모든 데이터는 조직 단위로 분리해서 조회
* 역할별 접근 가능한 기능 제한
* AI 분석 결과는 확정하지 않고, 사람이 수정할 수 있도록 둠

#### <Base URL>

```http
/api
```

---

## 2. 공통 응답 구조

### 2.1 성공 응답

```json
{
  "success": true,
  "data": {},
  "message": null
}
```

### 2.2 목록 응답

```json
{
  "success": true,
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  },
  "message": null
}
```

### 2.3 실패 응답

```json
{
  "success": false,
  "data": null,
  "message": "요청을 처리할 수 없습니다.",
  "error": {
    "code": "INVALID_REQUEST",
    "details": []
  }
}
```

### 2.4 주요 에러 코드

INVALID_REQUEST

* 요청값 오류

UNAUTHORIZED

* 인증 필요

FORBIDDEN

* 권한 없음

NOT_FOUND

* 데이터 없음

DUPLICATED_RESOURCE

* 중복 데이터

CSV_VALIDATION_FAILED

* CSV 검증 실패

AI_ANALYSIS_FAILED

* AI 분석 실패

INVALID_STATUS_TRANSITION

* 허용되지 않는 상태 변경

---

## 3. 인증 API

### 3.1 로그인

```http
POST /api/auth/login
```

#### <Request>

```json
{
  "email": "pm@example.com",
  "password": "password1234"
}
```

#### <Response>

```json
{
  "success": true,
  "data": {
    "accessToken": "access-token",
    "refreshToken": "refresh-token",
    "user": {
      "id": 1,
      "email": "pm@example.com",
      "name": "PM 사용자",
      "role": "PM",
      "organizationId": 1
    }
  },
  "message": null
}
```

#### <권한>

* PUBLIC

#### <설명>

로그인 이후 요청은 access token을 Authorization Header에 담아서 보냄

```http
Authorization: Bearer {accessToken}
```

---

### 3.2 내 정보 조회

```http
GET /api/auth/me
```

#### <Response>

```json
{
  "success": true,
  "data": {
    "id": 1,
    "organizationId": 1,
    "email": "pm@example.com",
    "name": "PM 사용자",
    "role": "PM"
  },
  "message": null
}
```

#### <권한>

* AUTHENTICATED

---

### 3.3 토큰 재발급

```http
POST /api/auth/refresh
```

#### <Request>

```json
{
  "refreshToken": "refresh-token"
}
```

#### <Response>

```json
{
  "success": true,
  "data": {
    "accessToken": "new-access-token"
  },
  "message": null
}
```

#### <권한>

* PUBLIC

---

## 4. 사용자 API

사용자 관리는 Admin만 가능하게 둠

### 4.1 사용자 목록 조회

```http
GET /api/users
```

#### <Query Parameters>

role

* 역할 필터

keyword

* 이름 또는 이메일 검색

page

* 페이지 번호

size

* 페이지 크기

#### <권한>

* ADMIN

---

### 4.2 사용자 생성

```http
POST /api/users
```

#### <Request>

```json
{
  "email": "cs@example.com",
  "password": "password1234",
  "name": "CS 사용자",
  "role": "CS"
}
```

#### <권한>

* ADMIN

---

### 4.3 사용자 역할 변경

```http
PATCH /api/users/{userId}/role
```

#### <Request>

```json
{
  "role": "DEVELOPER"
}
```

#### <권한>

* ADMIN

---

## 5. 데이터셋 API

데이터셋은 CSV 업로드 단위

ex. “2026년 7월 앱 리뷰”, “고객센터 문의 1분기 데이터” 같이 묶어 하나의 DataSet으로 처리

### 5.1 데이터셋 목록 조회

```http
GET /api/datasets
```

#### <Query Parameters>

sourceType

* 데이터 출처

status

* 업로드/분석 상태

page

* 페이지 번호

size

* 페이지 크기

#### <Response 예시>

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "name": "2026년 7월 앱 리뷰",
        "sourceType": "APP_REVIEW",
        "status": "ANALYZED",
        "totalCount": 10000,
        "validCount": 9800,
        "invalidCount": 200,
        "createdAt": "2026-07-03T10:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  },
  "message": null
}
```

#### <권한>

* ADMIN
* PM
* CS
* VIEWER

---

### 5.2 CSV 업로드

```http
POST /api/datasets
Content-Type: multipart/form-data
```

#### <Form Data>

name

* 데이터셋 이름
* 필수

sourceType

* 데이터 출처
* 필수

file

* CSV 파일
* 필수

columnMapping

* CSV 컬럼과 시스템 필드 매핑 정보
* 필수

#### <columnMapping 예시>

```json
{
  "review_text": "content",
  "score": "rating",
  "created_date": "feedback_created_at",
  "product": "product_name"
}
```

#### <Response>

```json
{
  "success": true,
  "data": {
    "datasetId": 1,
    "status": "UPLOADED"
  },
  "message": "CSV 업로드가 완료되었습니다."
}
```

#### <권한>

* ADMIN
* PM

#### <설명>

처음에는 업로드와 저장까지만 처리

CSV 업로드랑 AI 분석을 한 요청에서 처리하면 timeout 위험이 있어, AI 분석은 별도 API 시작으로 분리

---

### 5.3 데이터셋 상세 조회

```http
GET /api/datasets/{datasetId}
```

#### <설명>

데이터셋 기본 정보와 처리 상태를 조회한다.

#### <권한>

* ADMIN
* PM
* CS
* VIEWER

---

### 5.4 검증 오류 조회

```http
GET /api/datasets/{datasetId}/validation-errors
```

#### <설명>

CSV 업로드 과정에서 잘못된 행이 있으면 이 API로 확인한다.

#### <검증 오류 예시>

MISSING_REQUIRED_FIELD

* 필수 컬럼 누락

EMPTY_CONTENT

* content 값 없음

INVALID_RATING_RANGE

* rating 범위 오류

INVALID_DATE_FORMAT

* 날짜 형식 오류

DUPLICATED_EXTERNAL_ID

* external_id 중복

#### <권한>

* ADMIN
* PM

---

### 5.5 AI 분석 시작

```http
POST /api/datasets/{datasetId}/analyze
```

#### <설명>

실제 분석 결과를 바로 반환하지 않음

분석 job을 생성하고, dataset 상태를 ANALYZING으로 변경 후 jobId 반환

#### <Response>

```json
{
  "success": true,
  "data": {
    "datasetId": 1,
    "status": "ANALYZING",
    "jobId": "analysis-job-uuid"
  },
  "message": "AI 분석 작업이 시작되었습니다."
}
```

#### <권한>

* ADMIN
* PM

---

### 5.6 분석 상태 조회

```http
GET /api/datasets/{datasetId}/analysis-status
```

#### <Response>

```json
{
  "success": true,
  "data": {
    "datasetId": 1,
    "status": "ANALYZING",
    "totalCount": 10000,
    "processedCount": 6400,
    "successCount": 6300,
    "failedCount": 100,
    "progressRate": 64.0
  },
  "message": null
}
```

#### <권한>

* ADMIN
* PM
* CS
* VIEWER

---

## 6. 피드백 API

고객이 남긴 원문 데이터

### 6.1 피드백 목록 조회

```http
GET /api/feedbacks
```

#### <Query Parameters>

datasetId

* 데이터셋 ID

sourceType

* 출처

sentiment

* 감성

category

* 카테고리

ratingMin

* 최소 평점

ratingMax

* 최대 평점

keyword

* 원문 검색어

issueLinked

* 이슈 연결 여부

from

* 시작일

to

* 종료일

page

* 페이지 번호

size

* 페이지 크기

#### <Response 예시>

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "datasetId": 1,
        "sourceType": "APP_REVIEW",
        "rating": 1.0,
        "content": "쿠폰 적용 후 결제가 안 돼요.",
        "productName": "모바일 앱",
        "feedbackCreatedAt": "2026-07-01T12:00:00",
        "analysis": {
          "sentiment": "NEGATIVE",
          "sentimentScore": -0.82,
          "category": "PAYMENT",
          "urgencyScore": 0.91,
          "confidenceScore": 0.87
        },
        "linkedIssueId": 10
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 10000,
    "totalPages": 500
  },
  "message": null
}
```

#### <권한>

* ADMIN
* PM
* CS
* DEVELOPER
* VIEWER

---

### 6.2 피드백 상세 조회

```http
GET /api/feedbacks/{feedbackId}
```

#### <설명>

피드백 원문, AI 분석 결과, 연결된 이슈를 함께 조회 가능

#### <권한>

* ADMIN
* PM
* CS
* DEVELOPER
* VIEWER

---

### 6.3 AI 분석 결과 수정

```http
PATCH /api/feedbacks/{feedbackId}/analysis
```

#### <설명>

AI가 분류한 결과에 문제가 있을 때 사람이 직접 수정 가능

수정 전 값과 수정 후 값은 ai_corrections에 남겨 로그 기록

#### <Request>

```json
{
  "fieldName": "category",
  "correctedValue": "PAYMENT",
  "reason": "배송 문제가 아니라 결제 단계 오류로 판단됨"
}
```

#### <처리 흐름>

1. 기존 AI 분석 값 조회
2. feedback_analysis 값을 수정
3. ai_corrections에 수정 로그 저장
4. 수정된 결과를 반환

#### <권한>

* ADMIN
* PM
* CS

---

### 6.4 피드백을 이슈에 수동 연결

```http
POST /api/feedbacks/{feedbackId}/issue-links
```

#### <설명>

AI가 잘못 묶었거나, 아직 이슈에 연결되지 않은 피드백을 사용자가 직접 연결할 때 사용

#### <Request>

```json
{
  "issueId": 10,
  "isRepresentative": false
}
```

#### <권한>

* ADMIN
* PM
* CS

---

### 6.5 AI 수정 이력 조회

```http
GET /api/feedbacks/{feedbackId}/corrections
```

#### <설명>

해당 피드백의 AI 분석 결과 수정 로그 확인

#### <권한>

* ADMIN
* PM
* CS

---

## 7. 이슈 API

여러 피드백이 묶인 반복 문제 단위

### 7.1 이슈 목록 조회

```http
GET /api/issues
```

#### <Query Parameters>

status

* 상태

priority

* 우선순위

category

* 카테고리

assigneeId

* 담당자

keyword

* 제목 또는 설명 검색

from

* 시작일

to

* 종료일

page

* 페이지 번호

size

* 페이지 크기

#### <Response 예시>

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 10,
        "title": "쿠폰 적용 후 결제 실패",
        "category": "PAYMENT",
        "priority": "P0",
        "priorityScore": 91.25,
        "status": "IN_PROGRESS",
        "assignee": {
          "id": 3,
          "name": "개발자"
        },
        "feedbackCount": 128,
        "negativeCount": 122,
        "firstSeenAt": "2026-07-01T12:00:00",
        "lastSeenAt": "2026-07-03T09:30:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  },
  "message": null
}
```

#### <권한>

* ADMIN
* PM
* CS
* DEVELOPER
* VIEWER

---

### 7.2 이슈 상세 조회

```http
GET /api/issues/{issueId}
```

#### <포함 정보>

이슈 기본 정보

담당자

우선순위 점수

대표 피드백

관련 액션

최근 상태

#### <권한>

* ADMIN
* PM
* CS
* DEVELOPER
* VIEWER

---

### 7.3 이슈 생성

```http
POST /api/issues
```

#### <설명>

수동으로 이슈를 생성

이슈는 AI 클러스터링으로 생성되지만, 운영자가 직접 추가도 할 수 있도록 함

#### <Request>

```json
{
  "title": "쿠폰 적용 후 결제 실패",
  "description": "쿠폰 적용 이후 결제 실패 피드백이 반복적으로 발생함",
  "category": "PAYMENT",
  "priority": "P1",
  "assigneeId": 3
}
```

#### <권한>

* ADMIN
* PM

---

### 7.4 이슈 상태 변경

```http
PATCH /api/issues/{issueId}/status
```

#### <Request>

```json
{
  "status": "IN_PROGRESS"
}
```

#### <허용 상태 흐름>

NEW
→ TRIAGED
→ ASSIGNED
→ IN_PROGRESS
→ RESOLVED
→ MONITORING
→ CLOSED

#### <설명>

단순 필드 수정이 아니라 비즈니스 규칙이 들어가기 때문에, 이슈 전체 수정 API에 넣지 않고 별도 API로 분리

#### <권한>

* ADMIN
* PM
* ASSIGNEE

---

### 7.5 이슈 담당자 변경

```http
PATCH /api/issues/{issueId}/assignee
```

#### <Request>

```json
{
  "assigneeId": 3
}
```

#### <권한>

* ADMIN
* PM

---

### 7.6 이슈 우선순위 변경

```http
PATCH /api/issues/{issueId}/priority
```

#### <Request>

```json
{
  "priority": "P0",
  "reason": "최근 7일간 결제 실패 피드백이 급증함"
}
```

#### <권한>

* ADMIN
* PM

---

### 7.7 이슈 관련 피드백 조회

```http
GET /api/issues/{issueId}/feedbacks
```

#### <Query Parameters>

representativeOnly

* 대표 피드백만 조회

page

* 페이지 번호

size

* 페이지 크기

#### <권한>

* ADMIN
* PM
* CS
* DEVELOPER
* VIEWER

---

### 7.8 이슈 코멘트 작성

```http
POST /api/issues/{issueId}/comments
```

#### <Request>

```json
{
  "content": "결제 API 로그 확인 결과 쿠폰 검증 단계에서 timeout이 발생했습니다."
}
```

#### <권한>

* ADMIN
* PM
* CS
* DEVELOPER

---

### 7.9 이슈 코멘트 조회

```http
GET /api/issues/{issueId}/comments
```

#### <권한>

* ADMIN
* PM
* CS
* DEVELOPER
* VIEWER

---

## 8. 액션 API

액션은 이슈를 해결하기 위한 작업 단위

ex. “결제 로그 확인”, “FAQ 수정”, “배송 안내 문구 변경” 같은 작업을 액션으로 관리

### 8.1 액션 생성

```http
POST /api/issues/{issueId}/actions
```

#### <Request>

```json
{
  "title": "쿠폰 적용 결제 로그 확인",
  "description": "쿠폰 적용 후 결제 실패 로그를 확인한다.",
  "assigneeId": 3,
  "dueDate": "2026-07-10"
}
```

#### <권한>

* ADMIN
* PM

---

### 8.2 액션 상태 변경

```http
PATCH /api/actions/{actionId}/status
```

#### <Request>

```json
{
  "status": "DONE"
}
```

#### <권한>

* ADMIN
* PM
* ASSIGNEE

---

### 8.3 내 액션 목록 조회

```http
GET /api/actions/my
```

#### <Query Parameters>

status

* 액션 상태

page

* 페이지 번호

size

* 페이지 크기

#### <권한>

* AUTHENTICATED

---

## 9. 대시보드 API

대시보드는 처음에는 MySQL 집계로 구현하고, 데이터가 많아지면 snapshot과 Redis 캐싱으로 개선

### 9.1 요약 지표 조회

```http
GET /api/dashboard/summary
```

#### <Query Parameters>

from

* 시작일

to

* 종료일

#### <Response 예시>

```json
{
  "success": true,
  "data": {
    "totalFeedbackCount": 10000,
    "negativeFeedbackRate": 42.5,
    "newIssueCount": 25,
    "p0IssueCount": 3,
    "p1IssueCount": 8,
    "unresolvedIssueCount": 16,
    "averageResolutionHours": 36.5
  },
  "message": null
}
```

#### <권한>

* ADMIN
* PM
* CS
* VIEWER

---

### 9.2 카테고리별 통계

```http
GET /api/dashboard/category-breakdown
```

#### <설명>

카테고리별 이슈 수, 피드백 수, 부정 비율을 조회

#### <권한>

* ADMIN
* PM
* CS
* VIEWER

---

### 9.3 TOP 이슈 조회

```http
GET /api/dashboard/top-issues
```

#### <Query Parameters>

limit

* 조회 개수

sortBy

* 정렬 기준
* priority_score
* feedback_count
* growth_rate

#### <권한>

* ADMIN
* PM
* CS
* VIEWER

---

### 9.4 이슈 추이 조회

```http
GET /api/dashboard/issue-trends
```

#### <Query Parameters>

issueId

* 이슈 ID

from

* 시작일

to

* 종료일

#### <권한>

* ADMIN
* PM
* CS
* VIEWER

---

## 10. 권한 정리

ADMIN

* 사용자 관리 가능
* 데이터셋 업로드 가능
* 데이터셋 조회 가능
* 피드백 조회 가능
* AI 분석 시작 가능
* AI 분석 결과 수정 가능
* 이슈 생성 가능
* 이슈 조회 가능
* 이슈 담당자 변경 가능
* 이슈 상태 변경 가능
* 액션 생성 가능
* 액션 상태 변경 가능
* 코멘트 작성 가능
* 대시보드 조회 가능

PM

* 데이터셋 업로드 가능
* 데이터셋 조회 가능
* 피드백 조회 가능
* AI 분석 시작 가능
* AI 분석 결과 수정 가능
* 이슈 생성 가능
* 이슈 조회 가능
* 이슈 담당자 변경 가능
* 이슈 상태 변경 가능
* 액션 생성 가능
* 액션 상태 변경 가능
* 코멘트 작성 가능
* 대시보드 조회 가능

CS

* 데이터셋 조회 가능
* 피드백 조회 가능
* AI 분석 결과 수정 가능
* 이슈 조회 가능
* 코멘트 작성 가능
* 대시보드 조회 가능

DEVELOPER

* 피드백 조회 가능
* 이슈 조회 가능
* 담당 이슈 상태 변경 가능
* 담당 액션 상태 변경 가능
* 코멘트 작성 가능

VIEWER

* 데이터셋 조회 가능
* 피드백 조회 가능
* 이슈 조회 가능
* 대시보드 조회 가능

---

## 11. API 구현 순서

### 11.1 1차 구현

```http
POST /api/auth/login
GET  /api/auth/me

POST /api/datasets
GET  /api/datasets
GET  /api/datasets/{datasetId}

GET  /api/feedbacks
GET  /api/feedbacks/{feedbackId}

GET   /api/issues
GET   /api/issues/{issueId}
PATCH /api/issues/{issueId}/status
PATCH /api/issues/{issueId}/assignee

POST  /api/issues/{issueId}/actions
PATCH /api/actions/{actionId}/status
```

### 11.2 2차 구현

```http
POST /api/datasets/{datasetId}/analyze
GET  /api/datasets/{datasetId}/analysis-status

PATCH /api/feedbacks/{feedbackId}/analysis
GET   /api/feedbacks/{feedbackId}/corrections

POST /api/issues/{issueId}/comments
GET  /api/issues/{issueId}/comments

GET /api/dashboard/summary
```

### 11.3 3차 구현

```http
GET  /api/datasets/{datasetId}/validation-errors
POST /api/feedbacks/{feedbackId}/issue-links
GET  /api/issues/{issueId}/feedbacks

GET /api/dashboard/category-breakdown
GET /api/dashboard/top-issues
GET /api/dashboard/issue-trends
```

---

## 12. 아직 확정하지 않은 부분

구현하면서 아래 내용은 조정할 수 있다.

refresh token 저장 위치

CSV 검증 실패 시 전체 rollback 여부

AI 분석 실패 건 재시도 방식

issue clustering threshold 기준

dashboard 집계 시점

OpenSearch 도입 시점

Redis 캐싱 범위
