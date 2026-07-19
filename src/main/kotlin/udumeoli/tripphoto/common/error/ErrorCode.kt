package udumeoli.tripphoto.common.error

/**
 * GraphQL errors의 extensions.code로 내려가는 코드 목록 — schema.graphqls [에러 규격]과 1:1.
 * 전송 계층(GraphQL/REST) 매핑은 각 계층이 담당하고 여기서는 코드만 정의한다.
 */
enum class ErrorCode {
    UNAUTHENTICATED,
    FORBIDDEN,
    PARTY_NOT_FOUND,
    TRIP_NOT_FOUND,
    IMAGE_NOT_FOUND,
    REGION_NOT_FOUND,
    MEMBER_NOT_FOUND,
    INVALID_INVITE_CODE,
    IMAGE_NOT_UPLOADED,
    CANNOT_REMOVE_OWNER,
    PARTY_HAS_MEMBERS,
    OWNER_CANNOT_LEAVE,
    RATE_LIMITED,
    VALIDATION_ERROR,
}
