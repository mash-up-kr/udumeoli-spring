package udumeoli.tripphoto.common.error

/** 도메인 규칙 위반. GraphQL 계층에서 errors\[].extensions.code = [code] 로 변환된다. */
class DomainException(
    val code: ErrorCode,
    override val message: String,
) : RuntimeException(message)
