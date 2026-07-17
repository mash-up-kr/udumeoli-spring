package udumeoli.tripphoto.common.graphql

class GraphQlDomainException(
    val code: GraphQlErrorCode,
    override val message: String,
) : RuntimeException(message)
