package udumeoli.tripphoto.common.error

class DomainException(
    val code: ErrorCode,
    override val message: String,
) : RuntimeException(message)
