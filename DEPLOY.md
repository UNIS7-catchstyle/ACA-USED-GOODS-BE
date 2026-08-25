# Railway 배포 가이드

Railway(PaaS)에 이 저장소의 `Dockerfile`을 그대로 배포합니다. HTTPS는 Railway가 자동으로
붙여주므로 별도 리버스 프록시(Caddy 등)가 필요 없습니다. 아래는 Railway 콘솔에서 직접
해야 하는 작업을 순서대로 정리한 것입니다.

## 0. 사전 준비

- GitHub에 이 저장소가 push되어 있어야 합니다 (Railway가 GitHub repo를 보고 빌드합니다).
- `openssl rand -base64 64` 로 운영용 `JWT_SECRET`을 하나 미리 생성해두세요 — `application.yml`
  에 박혀 있는 기본값은 로컬 개발용이라 운영에 그대로 쓰면 안 됩니다.

## 1. Railway 프로젝트 생성 → GitHub repo 연결

1. [railway.app](https://railway.app) 로그인 → **New Project**.
2. **Deploy from GitHub repo** 선택 → 이 저장소 선택.
3. 저장소 루트에 `Dockerfile`이 있으므로 Railway가 자동으로 Dockerfile 빌드 방식을
   감지합니다. 별도 buildpack 설정은 필요 없습니다.
4. 이 시점에 첫 배포가 바로 시작될 수 있는데, 아직 환경변수가 없어 실패합니다 —
   정상입니다. 2~4단계를 마친 뒤 재배포하면 됩니다.

## 2. MySQL 서비스 추가

1. 프로젝트 캔버스에서 **+ New** → **Database** → **Add MySQL**.
2. 프로비저닝이 끝나면 MySQL 서비스의 **Variables** 탭에서 실제 변수명을 확인하세요.
   보통 `MYSQLHOST` / `MYSQLPORT` / `MYSQLUSER` / `MYSQLPASSWORD` / `MYSQLDATABASE`
   (+ 조립된 `MYSQL_URL`) 형태로 제공되지만, 템플릿 버전에 따라 이름이 조금 다를 수
   있으니 반드시 실제 값을 확인하세요.
3. Railway MySQL 플러그인은 보통 프로젝트 내부 전용 호스트(같은 프로젝트 서비스끼리는
   무료/저지연)와 외부 공개용 호스트를 분리 제공합니다. 앱 서비스는 같은 프로젝트에
   있으니 **내부용 호스트 변수**를 참조하세요.

## 3. 앱 서비스 환경변수 설정

앱 서비스의 **Variables** 탭에서 아래를 모두 입력합니다. `${{MySQL.MYSQLHOST}}` 같은
값은 Railway의 **변수 참조 문법**으로, 앱 서비스 안에서 다른 서비스(MySQL)의 변수를
그대로 가져다 씁니다 — 값을 직접 복사/붙여넣기하지 않아도 되고, MySQL 쪽 값이 바뀌면
자동으로 따라갑니다. (`${{`를 입력하면 Railway가 참조 가능한 서비스/변수를 자동완성으로
보여줍니다. 실제 변수명은 2단계에서 확인한 이름으로 바꿔서 넣으세요.)

| 변수명 | 용도 | 예시값 | 필수 여부 |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | 실행 프로파일 | `prod` | **필수** |
| `DB_HOST` | MySQL 호스트 (jdbc URL 조립용) | `${{MySQL.MYSQLHOST}}` | **필수** |
| `DB_PORT` | MySQL 포트 | `${{MySQL.MYSQLPORT}}` | 선택 (기본 3306) |
| `DB_NAME` | 데이터베이스명 | `${{MySQL.MYSQLDATABASE}}` | **필수** |
| `DB_USERNAME` | DB 유저 | `${{MySQL.MYSQLUSER}}` | **필수** |
| `DB_PASSWORD` | DB 비밀번호 | `${{MySQL.MYSQLPASSWORD}}` | **필수** |
| `JWT_SECRET` | JWT 서명 키 (base64, 디코드 시 64바이트 이상) | `openssl rand -base64 64` 결과 | **필수** (운영 기본값 사용 금지) |
| `JWT_ACCESS_TOKEN_EXPIRATION` | Access 토큰 만료(ms) | `3600000` | 선택 (기본 1시간) |
| `JWT_REFRESH_TOKEN_EXPIRATION` | Refresh 토큰 만료(ms) | `1209600000` | 선택 (기본 14일) |
| `CORS_ALLOWED_ORIGINS` | 허용할 프론트 origin (콤마 구분) | `https://your-frontend.example.com` | **필수** (기본값 없음 — 없으면 부팅 실패) |
| `STORAGE_S3_BUCKET` | S3 버킷명 | `aca-used-goods-prod` | S3 쓸 때 **필수** |
| `STORAGE_S3_REGION` | S3 리전 | `ap-northeast-2` | S3 쓸 때 **필수** |
| `STORAGE_S3_ACCESS_KEY` | S3 액세스 키 | `AKIA...` | S3 쓸 때 **필수** |
| `STORAGE_S3_SECRET_KEY` | S3 시크릿 키 | (비밀값) | S3 쓸 때 **필수** |
| `STORAGE_S3_PUBLIC_URL_PREFIX` | S3/CDN 공개 URL 프리픽스 | `https://cdn.example.com` (또는 비워둠) | 선택 |
| `STORAGE_TYPE` | 스토리지 구현체 선택 | `local` | 선택 — **S3 세팅 전 임시 기동용**, 아래 경고 참고 |
| `STORAGE_LOCAL_BASE_URL` | `STORAGE_TYPE=local`일 때 이미지 공개 URL | `https://<railway-domain>/uploads` | `STORAGE_TYPE=local`일 때만 필요 |
| `DDL_AUTO` | Hibernate 스키마 검증 모드 | `update` | **첫 배포에서만** 설정, 이후 제거 (5단계) |

> **`STORAGE_TYPE=local` 경고**: `storage.type` 기본값은 `s3`이고, S3 자격증명이 아직
> 없으면 이 값을 `local`로 오버라이드해서 임시로 띄울 수 있습니다. 다만 Railway
> 컨테이너의 파일시스템은 **휘발성**이라, 재배포/재시작마다 `/app/uploads`에 저장된
> 이미지가 전부 사라집니다 (DB의 URL 레코드만 남고 실제 파일은 404). S3 연동 전까지의
> 임시 데모용으로만 쓰고, 실제 운영 전에는 반드시 S3로 전환하세요.

## 4. jdbc URL 조립 방식에 대해

Railway MySQL은 `DATABASE_URL`(`mysql://user:pass@host:port/db` 형식)도 함께 제공하지만,
이 프로젝트의 `application-prod.yml`은 `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`/
`DB_PASSWORD`로 jdbc URL을 조립하는 구조입니다. 두 형식이 맞지 않는다고 코드에서
`DATABASE_URL`을 파싱하도록 바꾸지 않았습니다 — 위 표처럼 Railway 변수 참조 문법으로
`DB_HOST` 등을 MySQL 서비스의 개별 변수에 매핑하면 코드 변경 없이 그대로 해결됩니다.

## 5. 첫 배포 스키마 생성 절차

`application-prod.yml`은 기본이 `ddl-auto: validate`라 스키마가 이미 있어야 부팅됩니다.
그런데 첫 배포 시점엔 당연히 스키마가 없어서 `validate`가 그 자체로 실패합니다. 아래
순서로 한 번만 우회합니다 (`V1__init.sql`은 건드리지 않습니다):

1. 3단계 환경변수에 `DDL_AUTO=update`를 **추가로** 넣고 배포합니다.
2. 배포 로그 또는 `/actuator/health`가 `UP`으로 뜨는지 확인합니다 — Hibernate가
   엔티티 매핑대로 테이블을 자동 생성합니다.
3. 스키마가 만들어진 걸 확인했으면 `DDL_AUTO` 변수를 **삭제**합니다 (또는 값을
   `validate`로 바꿔도 동일). 다시 배포합니다.
4. 이후부터는 기본값인 `validate`로 부팅되어, 엔티티와 실제 스키마가 어긋나면
   배포 시점에 바로 실패로 드러납니다 (운영에서 조용히 스키마가 바뀌는 사고 방지).

## 6. 도메인 생성

1. 앱 서비스 → **Settings** → **Networking** → **Generate Domain**.
2. `<프로젝트명>.up.railway.app` 형태의 도메인이 생성되고, HTTPS는 Railway가 자동으로
   처리합니다 (별도 인증서 설정 불필요).

## 7. 배포 후 확인

- `https://<도메인>/actuator/health` → `{"status":"UP"}`
- `https://<도메인>/swagger-ui.html` → Swagger UI 정상 로딩
- (스키마만 있고 시드는 없는 상태이므로) `GET /api/markets?category=KPOP` → 200 +
  빈 목록이 정상입니다. 로컬 시드 데이터(`LocalDataSeeder`)는 `local` 프로파일
  전용이라 배포 환경에서는 동작하지 않습니다.

## 8. 카카오 로그인 E2E

**배포 도메인을 카카오 개발자 콘솔에 Redirect URI로 등록할 필요가 없습니다.**
이 백엔드의 로그인 방식(`KakaoOAuthClient`)은 프론트(또는 카카오 SDK)가 이미 발급받은
카카오 access token을 `POST /api/auth/login/kakao` 요청 바디로 받아
`GET https://kapi.kakao.com/v2/user/me`에 그 토큰을 실어 검증하는 구조입니다. 백엔드가
OAuth Authorization Code 리다이렉트 흐름 자체를 시작하지 않으므로, 카카오 콘솔의
Redirect URI 설정은 이 백엔드 도메인과 무관합니다. (프론트가 토큰을 발급받는 방식
자체가 나중에 Authorization Code 방식으로 바뀐다면 이 결론은 다시 확인해야 합니다.)

실제 검증은 README의 "카카오 실 토큰으로 로그인 수동 검증" 절차를 배포 도메인
기준으로 그대로 실행하면 됩니다.

## 9. CORS 갱신 (프론트 배포 후)

프론트 배포 도메인이 정해지면 앱 서비스의 `CORS_ALLOWED_ORIGINS` 값에 그 도메인을
추가하고(콤마로 구분해 여러 개 가능) 재배포하세요. 반영 전까지는 브라우저에서 프론트
도메인으로의 요청이 CORS 에러로 막힙니다.

## 10. 크레딧/비용 관리

- **앱 서비스 메모리 1GB 제한**: 앱 서비스 → **Settings**에서 리소스 제한(메모리/CPU)
  설정 항목을 찾아 메모리를 1GB로 지정하세요. 정확한 탭/항목 이름은 계정 플랜이나
  콘솔 개편에 따라 달라질 수 있어 여기서 단정하지 않습니다 — 배포 시점에 Settings를
  직접 살펴보고 리소스 제한(Resource Limits) 관련 항목을 찾으면 됩니다.
- **크레딧 소진 속도 확인**: 프로젝트의 **Usage** 메뉴에서 서비스별 크레딧 소비량과
  추이를 확인할 수 있습니다. 역시 정확한 위치는 콘솔 버전에 따라 다를 수 있으니,
  배포 후 프로젝트/계정 메뉴에서 "Usage" 또는 "Billing"으로 표기된 항목을 직접
  찾아 확인하세요.

## 11. 시연 종료 후 프로젝트 삭제 절차

1. 프로젝트의 **Usage**(10단계) 메뉴에서 현재까지 소비된 크레딧과 남은 크레딧을
   먼저 확인해 예상 밖의 소비가 없는지 체크합니다.
2. 앱 서비스와 MySQL 서비스를 각각 정지(Stop/Pause)하거나, 프로젝트 자체를
   삭제합니다 — 프로젝트 **Settings** 하단에 프로젝트 삭제 항목이 있고, 보통
   프로젝트 이름을 직접 입력해 확인하는 절차를 거칩니다.
3. 삭제 후 계정 **Usage/Billing** 페이지에서 잔여 크레딧과 최종 청구 내역을 다시
   확인해, 삭제 시점까지의 사용량만 정상적으로 반영됐는지 확인합니다. (Railway는
   보통 사용한 만큼만 크레딧에서 차감하는 종량 방식이지만, 확정 청구는 다음 결제
   주기에 반영될 수 있으니 며칠 뒤 한 번 더 확인하는 걸 권장합니다.)
