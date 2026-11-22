# udumeoli-spring

## 팀 위키 (제품 규칙 · FE↔BE 계약 · 결정)

GraphQL 스키마, REST 응답, 인증·토큰, 에러 코드, 지역코드, 이미지 업로드를 바꾸기 전에 팀 위키 목차를 읽고 관련 1~3쪽만 연다. 위키: https://github.com/inhachoi/pinnned-llm-wiki

```bash
# 옆 폴더에 클론이 있으면 ../pinnned-llm-wiki/wiki/index.md 를 읽는다. 없으면:
gh api repos/inhachoi/pinnned-llm-wiki/contents/wiki/index.md --jq .content | base64 -d
gh api repos/inhachoi/pinnned-llm-wiki/contents/wiki/<쪽 경로> --jq .content | base64 -d
```

| 바꾸는 것 | 먼저 읽을 쪽 |
| --- | --- |
| 로그인·토큰·refresh·강제 로그아웃 | `wiki/contract/auth.md` |
| GraphQL 에러 코드 | `wiki/contract/errors.md` |
| 지역코드 | `wiki/contract/regions.md` |
| 이미지 업로드·썸네일 | `wiki/contract/images.md` |
| 제품 동작(여행·핀·키워드·앨범 규칙 등) | `wiki/decisions.md`, `wiki/open-questions.md` |

- 계획과 결과에 근거 ID를 남긴다 (`D-003`, `Q07`). `status: draft` 쪽은 단서로만 쓴다.
- 지금 어떻게 동작하나는 코드·배포본이, 어떻게 동작해야 하나는 위키의 확정 결정이 기준이다. 둘이 다르면 고르지 말고 보고한다.
- `open-questions.md`에서 상태가 `열림`인 것은 확정처럼 구현하지 않는다.
- FE가 기대는 계약(응답 형태, 상태 코드, 에러 코드, 토큰 수명)을 바꾸거나 위키가 코드와 다른 것을 발견하면 PR 본문 "리뷰어에게"에 `wiki: <한 줄>`을 남긴다. 위키의 주간 수집이 이 줄을 가장 먼저 읽는다.
