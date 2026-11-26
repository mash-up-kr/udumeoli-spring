# API 언어 선택

REST와 GraphQL 요청에 표준 `Accept-Language` 헤더를 보낸다. `en`과 `en-US`는 영어, `ko`와 `ko-KR`은 한국어로 응답한다. 헤더가 없거나 지원하지 않는 언어면 한국어를 사용한다.

```http
POST /graphql
Accept-Language: en-US
Content-Type: application/json
Authorization: Bearer <access-token>

{"query":"query { regions { regionCode name nameKo nameEn } }"}
```

`regions`는 지도 GeoJSON의 `properties.code`와 맞는 지역 코드 168개와 이름을 반환한다. `name`은 요청 언어를 따르고, `nameKo`와 `nameEn`은 고정 언어다. 기존 `Trip`, `VisitedRegion`, `MapCell`에서도 `regionName`을 선택하면 같은 규칙으로 이름을 받을 수 있다. `MapCell`의 `KR`은 한국어 `대한민국`, 영어 `South Korea`다.

오류의 `code`, 지역 코드, `TripKeyword` enum 값은 언어에 따라 바뀌지 않는다. 오류 문구와 날짜 스칼라 오류는 요청 언어를 따른다. 사용자가 직접 작성한 팟 이름, 닉네임, 여행 코멘트, 피드백은 번역하지 않는다.

오류 문구는 스프링 `MessageSource`가 `messages.properties`(한국어)와 `messages_en.properties`(영어)에서 메시지 키로 조회한다. 새 언어를 추가할 때 해당 언어의 메시지 파일을 추가하고 지역명 데이터도 준비한다. 정의되지 않은 언어는 한국어 기본 문구를 사용한다.

프론트에서 언어 전환을 제공할 때는 선택된 언어를 매 요청의 헤더에 넣는다. 지역 이름을 화면에 표시할 때 GeoJSON의 이름 대신 API의 `name` 또는 `regionName`을 사용해야 화면 언어가 함께 바뀐다.
