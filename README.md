# VOC ActionOps

고객 리뷰, 문의, 설문 데이터를 수집해 반복 이슈를 자동 분류하고, 우선순위 산정과 담당자 액션 관리, 처리 후 지표 추적까지 제공하는 AI 기반 고객 피드백 운영 플랫폼입니다.

## 프로젝트 목적

고객 피드백이 실제 개선 작업으로 이어지도록 하고, 운영에 편의를 제공하는 서비스를 가진 운영형 플랫폼을 목표로 합니다.

## 핵심 기능

- CSV 기반 고객 피드백 업로드
- 피드백 감성, 카테고리, 긴급도 분석
- 유사 피드백 이슈 클러스터링
- 이슈 우선순위 계산
- 담당자 배정 및 액션 관리
- AI 분석 결과 수정 이력 관리
- 대시보드 지표 제공

## 기술 스택

### Backend
- Java 17
- Spring Boot 3
- Spring Security
- JPA
- QueryDSL
- MySQL
- Redis

### Frontend
- Next.js
- TypeScript
- TanStack Query
- Tailwind CSS

### AI Worker
- Python
- FastAPI

### Infra
- Docker
- AWS S3
- AWS SQS
- GitHub Actions

## 문서

- 문제 정의: `docs/problem_definition.md`
- 요구사항: `docs/requirements.md`
- ERD: `docs/erd.md`
- API 명세: `docs/api.md`