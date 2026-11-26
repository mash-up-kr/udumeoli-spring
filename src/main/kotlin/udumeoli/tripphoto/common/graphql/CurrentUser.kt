package udumeoli.tripphoto.common.graphql

fun requireCurrentUserId(currentUserId: Long?): Long =
    currentUserId
        ?: throw GraphQlDomainException(GraphQlErrorCode.UNAUTHENTICATED, "error.login.required")
