package com.example.app.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

	private static final String BEARER_SCHEME_NAME = "bearerAuth";

	@Bean
	public OpenAPI openAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("ACA Used Goods API")
						.description("""
								ACA 중고거래(팬굿즈) 서비스 API 문서

								## 공통 응답 래핑
								모든 응답은 `{ "success": boolean, "code": string, "message": string, "data": T }` 형태로 감쌉니다.
								성공 시 `code`는 `"SUCCESS"`, 실패 시 아래 에러 코드 체계를 따릅니다. Validation 에러(400)는
								`data`에 `[{ "field": string, "message": string }]` 형태의 필드별 에러 배열이 담깁니다.

								## 에러 코드 체계
								- `C0xx` Common (예: C001 Invalid input value)
								- `A0xx` Auth (예: A004 Token expired)
								- `U0xx` User
								- `M0xx` Market
								- `S0xx` Scrap
								- `I0xx` Image
								- `C1xx` Comment (Common의 C0xx와 자릿수로 구분)

								## 커서 페이징
								목록 조회 API는 `cursor`(다음 페이지 시작점, 없으면 첫 페이지) / `size` 쿼리 파라미터를 받고,
								응답으로 `{ "totalCount": number, "items": [...], "nextCursor": string | null, "hasNext": boolean }`
								형태를 반환합니다. `nextCursor`를 다음 요청의 `cursor` 값으로 그대로 전달하면 됩니다.

								## 인증 및 토큰 만료
								인증이 필요한 API는 `Authorization: Bearer {accessToken}` 헤더가 필요합니다(자물쇠 아이콘 표시).
								Access 토큰이 만료되면 `A004 TOKEN_EXPIRED`로 401이 응답되며, 이때 `POST /api/auth/reissue`에
								Refresh 토큰을 실어 재발급받은 뒤 원래 요청을 재시도하세요. Refresh 토큰까지 만료/무효면
								재로그인이 필요합니다.
								""")
						.version("v0.0.1"))
				.components(new Components()
						.addSecuritySchemes(BEARER_SCHEME_NAME, new SecurityScheme()
								.name(BEARER_SCHEME_NAME)
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")));
	}
}
