# ACA Used Goods Backend

Spring Boot 3.5 + Java 17 + Gradle(Kotlin DSL) 기반 백엔드 스켈레톤.

## 실행 순서

1. 환경 변수 설정 (선택 — local 프로파일은 기본값이 있어 생략 가능)
   ```
   cp .env.example .env
   ```
2. MySQL 기동
   ```
   docker compose up -d
   ```
3. 애플리케이션 실행
   ```
   ./gradlew bootRun
   ```
4. Swagger UI 접속: http://localhost:8080/swagger-ui.html
5. OpenAPI 스펙: http://localhost:8080/v3/api-docs

## 빌드 / 테스트

```
./gradlew clean build
```

테스트는 `test` 프로파일로 실행되며 DataSource/JPA 자동설정을 제외해 DB 없이 통과합니다.

## 패키지 구조

- `domain/{auth,user,market,scrap,comment,image}` — 도메인별 controller/service/repository/entity/dto 뼈대
- `global/config` — JPA Auditing, Swagger, CORS 등 공통 설정
- `global/exception` — 공통 에러 코드/예외/핸들러
- `global/response` — 공통 응답 포맷(`ApiResponse`)
- `global/security` — Security 설정 및 JWT 뼈대
- `global/common` — `BaseTimeEntity` 등 공통 엔티티
