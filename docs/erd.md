# VOC ActionOps ERD

고객 피드백 원문, AI 분석 결과, 반복 이슈, 실제 처리 액션을 분리하고 조직 단위 데이터 격리를 적용한 MySQL 기준 설계다.

- [ERDCloud에서 보기](https://www.erdcloud.com/d/xxDZWHiM8pZhwuqZJ)
- 아래 Mermaid ERD는 공개 저장소 안에서도 전체 구조를 확인할 수 있도록 같은 테이블과 관계를 표현한다.

## 전체 관계

```mermaid
erDiagram
    organizations ||--o{ users : has
    organizations ||--o{ datasets : owns
    organizations ||--o{ feedbacks : isolates
    organizations ||--o{ issues : owns

    users ||--o{ datasets : creates
    users o|--o{ issues : assigned_to
    users o|--o{ actions : assigned_to
    users ||--o{ issue_comments : writes
    users ||--o{ ai_corrections : corrects

    datasets ||--o{ dataset_validation_errors : records
    datasets ||--o{ feedbacks : contains

    feedbacks ||--o| feedback_analysis : analyzed_as
    feedbacks ||--o| feedback_embeddings : embedded_as
    feedbacks ||--o{ issue_feedbacks : linked_by
    feedbacks ||--o{ ai_corrections : corrected_by

    issues ||--o{ issue_feedbacks : groups
    issues ||--o{ actions : resolves_with
    issues ||--o{ issue_comments : discusses
    issues ||--o{ issue_metrics_snapshot : measures

    organizations {
        bigint id PK
        varchar name
        datetime created_at
        datetime updated_at
    }

    users {
        bigint id PK
        bigint organization_id FK
        varchar email UK
        varchar password_hash
        varchar name
        varchar role
        datetime created_at
        datetime updated_at
    }

    datasets {
        bigint id PK
        bigint organization_id FK
        varchar name
        varchar source_type
        varchar file_url
        json column_mapping_json
        varchar status
        int total_count
        int valid_count
        int invalid_count
        bigint created_by FK
        datetime created_at
        datetime updated_at
    }

    dataset_validation_errors {
        bigint id PK
        bigint dataset_id FK
        int csv_row_number
        varchar field_name
        varchar error_code
        varchar error_message
        json raw_row_json
        datetime created_at
    }

    feedbacks {
        bigint id PK
        bigint organization_id FK
        bigint dataset_id FK
        varchar external_id
        varchar source_type
        varchar customer_segment
        varchar product_name
        decimal rating
        text content
        varchar language
        datetime feedback_created_at
        datetime ingested_at
    }

    feedback_analysis {
        bigint id PK
        bigint feedback_id FK,UK
        varchar sentiment
        decimal sentiment_score
        varchar category
        decimal urgency_score
        varchar summary
        decimal confidence_score
        varchar model_name
        varchar status
        varchar error_message
        datetime analyzed_at
    }

    feedback_embeddings {
        bigint id PK
        bigint feedback_id FK,UK
        varchar embedding_model
        json embedding_json
        varchar content_hash
        datetime created_at
    }

    issues {
        bigint id PK
        bigint organization_id FK
        varchar title
        varchar description
        varchar category
        varchar priority
        decimal priority_score
        varchar status
        bigint assignee_id FK
        datetime first_seen_at
        datetime last_seen_at
        datetime created_at
        datetime updated_at
    }

    issue_feedbacks {
        bigint id PK
        bigint issue_id FK
        bigint feedback_id FK
        decimal similarity_score
        boolean is_representative
        varchar linked_by
        datetime created_at
    }

    actions {
        bigint id PK
        bigint issue_id FK
        varchar title
        varchar description
        varchar status
        bigint assignee_id FK
        date due_date
        datetime created_at
        datetime updated_at
        datetime completed_at
    }

    issue_comments {
        bigint id PK
        bigint issue_id FK
        bigint user_id FK
        varchar content
        datetime created_at
        datetime updated_at
    }

    ai_corrections {
        bigint id PK
        bigint feedback_id FK
        varchar field_name
        varchar ai_value
        varchar corrected_value
        varchar reason
        bigint corrected_by FK
        datetime created_at
    }

    issue_metrics_snapshot {
        bigint id PK
        bigint issue_id FK
        date snapshot_date
        int feedback_count
        int negative_count
        decimal average_sentiment_score
        decimal average_urgency_score
        decimal priority_score
        int unresolved_action_count
        datetime created_at
    }
```

## 핵심 제약

- `users.email`: `UNIQUE`
- `feedback_analysis.feedback_id`: `UNIQUE`로 피드백별 최신 분석 결과 1건 보장
- `feedback_embeddings.feedback_id`: `UNIQUE`로 피드백별 임베딩 1건 보장
- `issue_feedbacks(issue_id, feedback_id)`: 복합 `UNIQUE`
- `issue_metrics_snapshot(issue_id, snapshot_date)`: 복합 `UNIQUE`
- 모든 핵심 조회는 `organization_id`를 기준으로 격리

## 구현 단계

1차 MVP는 `organizations`, `users`, `datasets`, `dataset_validation_errors`, `feedbacks`, `feedback_analysis`, `issues`, `issue_feedbacks`, `actions`, `ai_corrections`를 구현한다.

2차에서 `feedback_embeddings`, `issue_comments`를 추가하고, 성능 개선 단계에서 `issue_metrics_snapshot`을 적용한다.
