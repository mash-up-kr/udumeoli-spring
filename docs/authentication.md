# 소셜 로그인 연동

## 로그인 흐름

1. 프론트에서 백엔드의 `GET /oauth2/authorization/kakao`로 이동한다.
2. 카카오 인증이 끝나면 백엔드는 프론트의 `/login/callback?code=...`으로 리다이렉트한다.
3. 프론트는 받은 1회용 코드를 `POST /api/auth/exchange`로 교환한다.
4. 백엔드는 교환 시점에 `AUTHENTICATED`이면 앱 access/refresh token을 발급한다.
5. 백엔드는 교환 시점에 `SIGNUP_REQUIRED`이면 signup token을 발급한다.
6. `SIGNUP_REQUIRED`이면 사용자가 닉네임을 직접 입력한 뒤 `POST /api/auth/signup`을 호출한다.

카카오가 반환한 닉네임은 회원가입 닉네임으로 사용하지 않는다. 신규 사용자는 반드시 서비스에서 닉네임을 직접 입력해야 한다.

## 인증 흐름 다이어그램

```mermaid
sequenceDiagram
    autonumber

    actor User as 사용자
    participant FE as Frontend<br/>Vercel
    participant BE as Backend<br/>Spring Boot
    participant Sec as Spring Security<br/>OAuth2 Login
    participant Kakao as Kakao
    participant Auth as AuthService
    participant CodeStore as OAuthLoginCodeStore<br/>Caffeine Cache
    participant Jwt as JwtTokenService
    participant DB as DB<br/>service_user/social_account/refresh_token

    rect rgb(235, 245, 255)
        Note over User,DB: 공통 OAuth 인증 흐름
        User->>FE: 카카오 로그인 버튼 클릭
        FE->>BE: GET /oauth2/authorization/kakao

        BE->>Sec: OAuth2 로그인 시작
        Sec->>Kakao: 카카오 인증 페이지로 리다이렉트
        Kakao->>User: 로그인/동의 화면
        User->>Kakao: 로그인 완료

        Kakao->>BE: GET /login/oauth2/code/kakao?code=...
        BE->>Sec: authorization code 처리
        Sec->>Kakao: Kakao access token 요청
        Kakao-->>Sec: Kakao access token
        Sec->>Kakao: 사용자 정보 요청
        Kakao-->>Sec: Kakao user attributes

        Sec->>BE: OAuth2LoginSuccessHandler 실행
        BE->>BE: extractProfile(provider, providerUserId)
        BE->>Auth: prepareOAuthLogin(profile)
        Auth->>DB: social_account 조회
    end

    alt 기존 사용자 로그인
        rect rgb(237, 252, 242)
            Note over Auth,FE: 기존 사용자 로그인 처리
            DB-->>Auth: social_account 있음

            Auth->>CodeStore: AUTHENTICATED 사용자 id를 1회용 code로 저장
            CodeStore-->>Auth: loginCode

            BE->>BE: OAuth 세션 무효화
            BE-->>FE: 302 Redirect<br/>/login/callback?code={loginCode}

            FE->>BE: POST /api/auth/exchange<br/>{ code }
            BE->>Auth: exchangeLoginCode(code)
            Auth->>CodeStore: consume(code)
            CodeStore-->>Auth: AUTHENTICATED 사용자 id 반환 후 삭제

            Auth->>Jwt: 내부 access token + refresh token 발급
            Jwt-->>Auth: TokenResponse
            Auth->>DB: refresh token hash 저장

            Auth-->>FE: status=AUTHENTICATED<br/>accessToken, refreshToken
            FE->>FE: accessToken 보관
            FE->>BE: GraphQL/API 요청<br/>Authorization: Bearer accessToken
        end
    else 신규 사용자 회원가입
        rect rgb(255, 248, 230)
            Note over Auth,FE: 신규 사용자 회원가입 처리
            DB-->>Auth: social_account 없음

            Auth->>CodeStore: SIGNUP_REQUIRED 소셜 식별자를 1회용 code로 저장
            CodeStore-->>Auth: loginCode

            BE->>BE: OAuth 세션 무효화
            BE-->>FE: 302 Redirect<br/>/login/callback?code={loginCode}

            FE->>BE: POST /api/auth/exchange<br/>{ code }
            BE->>Auth: exchangeLoginCode(code)
            Auth->>CodeStore: consume(code)
            CodeStore-->>Auth: SIGNUP_REQUIRED 소셜 식별자 반환 후 삭제

            Auth->>Jwt: signup token 발급
            Jwt-->>Auth: signupToken

            Auth-->>FE: status=SIGNUP_REQUIRED<br/>signupToken

            FE->>User: 닉네임 입력 화면
            User->>FE: 닉네임 입력

            FE->>BE: POST /api/auth/signup<br/>{ signupToken, nickname }
            BE->>Auth: completeSignup(signupToken, nickname)

            Auth->>Jwt: signupToken 검증
            Jwt-->>Auth: provider, providerUserId

            Auth->>DB: social_account 재조회
            DB-->>Auth: social_account 없음

            Auth->>DB: service_user 생성<br/>nickname 저장
            Auth->>DB: social_account 생성<br/>provider/providerUserId 연결

            Auth->>Jwt: 내부 access token + refresh token 발급
            Jwt-->>Auth: TokenResponse
            Auth->>DB: refresh token hash 저장

            Auth-->>FE: accessToken, refreshToken
            FE->>FE: accessToken 보관
            FE->>BE: GraphQL/API 요청<br/>Authorization: Bearer accessToken
        end
    end
```

## API

### 로그인 코드 교환

```http
POST /api/auth/exchange
Content-Type: application/json

{"code":"one-time-login-code"}
```

기존 회원 응답:

```json
{
  "status": "AUTHENTICATED",
  "tokens": {
    "accessToken": "...",
    "refreshToken": "...",
    "tokenType": "Bearer",
    "accessTokenExpiresIn": 1800
  },
  "signupToken": null
}
```

신규 회원 응답:

```json
{
  "status": "SIGNUP_REQUIRED",
  "tokens": null,
  "signupToken": "..."
}
```

### 회원가입 완료

```http
POST /api/auth/signup
Content-Type: application/json

{"signupToken":"...","nickname":"직접 입력한 닉네임"}
```

### 토큰 재발급

```http
POST /api/auth/refresh
Content-Type: application/json

{"refreshToken":"..."}
```

재발급할 때 access token과 refresh token이 모두 새로 발급된다. 사용한 refresh token은 즉시 폐기된다.

### 로그아웃

```http
POST /api/auth/logout
Content-Type: application/json

{"refreshToken":"..."}
```

성공 시 `204 No Content`를 반환한다.

### 인증 API 호출

GraphQL을 포함한 보호 API에는 access token을 전송한다.

```http
Authorization: Bearer <accessToken>
```

## 환경변수

| 이름 | 용도 |
| --- | --- |
| `KAKAO_CLIENT_ID` | 카카오 REST API 키 |
| `KAKAO_CLIENT_SECRET` | 카카오 로그인 클라이언트 시크릿 |
| `JWT_SECRET` | 내부 JWT 서명 비밀키. 운영에서는 충분히 긴 임의 값 사용 |
| `FRONTEND_LOGIN_CALLBACK_URL` | 로그인 결과를 받을 프론트 URL |
| `CORS_ALLOWED_ORIGINS` | 쉼표로 구분한 허용 프론트 origin 목록 |

카카오 디벨로퍼스에는 `{백엔드 주소}/login/oauth2/code/kakao`를 Redirect URI로 등록한다. 프록시 뒤에서 실행할 때도 외부 요청의 scheme과 host가 유지되도록 forwarded header 설정이 적용되어 있다.

## 토큰 만료 시간

| 토큰 | 만료 시간 |
| --- | --- |
| access token | 30분 |
| refresh token | 14일 |
| signup token | 10분 |
| 로그인 교환 코드 | 2분, 1회 사용 |
