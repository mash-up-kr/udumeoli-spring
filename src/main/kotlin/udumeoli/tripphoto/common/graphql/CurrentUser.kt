package udumeoli.tripphoto.common.graphql

fun requireCurrentUserId(currentUserId: Long?): Long =
    currentUserId
        ?: throw GraphQlDomainException(GraphQlErrorCode.UNAUTHENTICATED, "로그인이 필요합니다.")
