package udumeoli.tripphoto.auth.entity

enum class OAuth2Provider(
    val registrationId: String,
) {
    KAKAO("kakao"),
    GOOGLE("google"),
    APPLE("apple"),
    ;

    companion object {
        fun fromRegistrationId(registrationId: String): OAuth2Provider =
            entries.find { it.registrationId.equals(registrationId, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unsupported OAuth2 provider: $registrationId")
    }
}
