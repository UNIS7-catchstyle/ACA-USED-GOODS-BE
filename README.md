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

테스트는 `test` 프로파일로 실행되며 H2 인메모리 DB(`ddl-auto: create-drop`)를 사용합니다.

## 카카오 실 토큰으로 로그인 수동 검증

1. [카카오 개발자 콘솔](https://developers.kakao.com) → 내 애플리케이션 → 앱 생성 후 REST API 키 확인.
2. 좌측 메뉴 "도구 > 토큰 발급/재발급" (또는 카카오 로그인 REST API 문서의 "토큰 발급" 페이지)에서 해당 앱으로 로그인해 **테스트용 access token**을 발급받습니다.
3. 발급받은 토큰으로 로그인 호출:
   ```
   curl -X POST http://localhost:8080/api/auth/login/kakao \
     -H "Content-Type: application/json" \
     -d '{"accessToken":"<발급받은 카카오 access token>"}'
   ```
4. 응답의 `data.accessToken`/`data.refreshToken` 발급 여부와, DB `users` 테이블에 랜덤 닉네임(`형용사 명사NNNN`)으로 신규 유저가 생성됐는지 확인합니다.

## 배포

Railway(PaaS)에 Dockerfile 기반으로 배포합니다. 콘솔에서 해야 하는 작업은
[DEPLOY.md](./DEPLOY.md)에 순서대로 정리되어 있습니다.

## 패키지 구조

- `domain/{auth,user,market,scrap,comment,image}` — 도메인별 controller/service/repository/entity/dto 뼈대
- `global/config` — JPA Auditing, Swagger, CORS 등 공통 설정
- `global/exception` — 공통 에러 코드/예외/핸들러
- `global/response` — 공통 응답 포맷(`ApiResponse`)
- `global/security` — Security 설정 및 JWT 뼈대
- `global/common` — `BaseTimeEntity` 등 공통 엔티티
