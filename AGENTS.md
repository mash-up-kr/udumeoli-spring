# udumeoli-spring

## 팀 위키 (제품 규칙 · FE↔BE 계약 · 결정)

위키: https://github.com/inhachoi/pinnned-llm-wiki

- 읽을 때: FE가 기대는 계약(응답 형태, 상태 코드, 에러 코드, 토큰 수명)이나 제품 동작을 바꿀 때.
- 읽지 않을 때: 바깥 동작이 그대로인 작업(내부 리팩터링, 로깅, 인프라, 테스트).
- 읽는 법: 목차(`wiki/index.md`)의 BE 경로·키워드로 해당하는 쪽만 골라 연다. 나머지 규칙은 목차와 위키의 `AGENTS.md`를 따른다.

```bash
# 옆 폴더에 클론이 있으면 ../pinnned-llm-wiki/wiki/index.md 를 읽는다. 없으면:
gh api repos/inhachoi/pinnned-llm-wiki/contents/wiki/index.md --jq .content | base64 -d
gh api repos/inhachoi/pinnned-llm-wiki/contents/wiki/<쪽 경로> --jq .content | base64 -d
```

- 지금 어떻게 동작하나는 코드·배포본이, 어떻게 동작해야 하나는 위키의 확정 결정이 기준이다. 둘이 다르면 고르지 말고 보고한다.
- 계약을 바꾸거나 위키가 코드와 다른 것을 발견하면 PR 본문 "리뷰어에게"에 `wiki: <한 줄>`을 남긴다. 위키의 주간 수집이 이 줄을 가장 먼저 읽는다.
