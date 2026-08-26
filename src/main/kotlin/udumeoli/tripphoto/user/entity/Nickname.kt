package udumeoli.tripphoto.user.entity

/**
 * 닉네임 규칙을 한 곳에 모아, 가입(REST)과 프로필 수정(GraphQL)이 같은 잣대를 쓰게 한다.
 *
 * 기획 기준은 앞뒤 공백을 털어낸 1~[MAX_LENGTH]자다. 공백만 입력한 값은 무효고,
 * 가운데 공백은 허용하며 길이에 포함한다 ("김 민"은 3자).
 *
 * 두 진입점이 던지는 예외 타입이 서로 달라(AuthException / GraphQlDomainException)
 * 여기서는 판정만 하고 실패를 어떻게 알릴지는 호출부에 맡긴다.
 */
object Nickname {
    const val MAX_LENGTH = 6

    const val RULE_MESSAGE = "닉네임은 1자 이상 ${MAX_LENGTH}자 이하로 입력해주세요."

    /** 규칙을 지킨 값이면 앞뒤 공백을 턴 닉네임, 아니면 null. */
    fun normalizeOrNull(raw: String): String? = raw.trim().takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH }
}
