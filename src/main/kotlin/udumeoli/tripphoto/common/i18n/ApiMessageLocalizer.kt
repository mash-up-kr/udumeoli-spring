package udumeoli.tripphoto.common.i18n

import org.springframework.stereotype.Component
import java.util.Locale

/** Keeps existing Korean exception messages while translating responses selected by Accept-Language. */
@Component
class ApiMessageLocalizer {
    fun localize(
        message: String,
        locale: Locale,
    ): String =
        if (locale.language != Locale.ENGLISH.language) {
            message
        } else {
            translations[message]
                ?: when {
                    message.startsWith("존재하지 않는 지역입니다: ") ->
                        "Region not found: ${message.substringAfter(": ")}"
                    message.startsWith("존재하지 않는 이미지입니다: ") ->
                        "Image not found: ${message.substringAfter(": ")}"
                    message.startsWith("허용되지 않는 contentType: ") ->
                        "Unsupported content type: ${message.substringAfter(": ").replace("(허용:", "(allowed:")}"
                    else -> message
                }
        }

    private companion object {
        val translations =
            mapOf(
                "로그인이 필요합니다." to "Login is required.",
                "접근 권한이 없습니다." to "Access denied.",
                "방장 정보를 찾을 수 없습니다." to "Party owner not found.",
                "여행팟 멤버만 접근할 수 있습니다." to "Only party members can access this resource.",
                "여행팟을 찾을 수 없습니다." to "Party not found.",
                "멤버를 찾을 수 없습니다." to "Member not found.",
                "핀을 찾을 수 없습니다." to "Trip pin not found.",
                "삭제할 내 기록이 없습니다." to "You have no trip record to delete.",
                "존재하지 않는 초대코드입니다." to "Invite code not found.",
                "초대코드는 영문과 숫자 6자리여야 합니다." to "The invite code must be six letters or digits.",
                "이미 참여 중인 여행팟입니다." to "You have already joined this party.",
                "정원이 다 찼어요. (6/6)" to "This party is full. (6/6)",
                "초대코드 생성 중 충돌이 발생했습니다. 다시 시도해주세요." to "Could not create an invite code. Please try again.",
                "초대코드 입력 시도가 너무 많습니다. 잠시 후 다시 시도해주세요." to "Too many invite code attempts. Please try again later.",
                "방장은 강퇴할 수 없습니다." to "The party owner cannot be removed.",
                "방장만 수행할 수 있습니다." to "Only the party owner can do this.",
                "여행팟 이름을 입력해주세요." to "Please enter a party name.",
                "별점은 1~5 사이여야 합니다." to "Rating must be between 1 and 5.",
                "의견은 공백 제외 1자 이상 5,000자 이하여야 합니다." to "Feedback must contain 1 to 5,000 non-blank characters.",
                "닉네임은 1자 이상 6자 이하로 입력해주세요." to "Nickname must be between 1 and 6 characters.",
                "허용되지 않는 이미지 형식입니다." to "Unsupported image format.",
                "이미 가입이 완료된 소셜 계정입니다." to "This social account is already registered.",
                "이미 사용되었거나 폐기된 refresh token입니다." to "This refresh token has already been used or revoked.",
                "유효하지 않거나 만료된 refresh token입니다." to "This refresh token is invalid or expired.",
                "사용자를 찾을 수 없습니다." to "User not found.",
                "존재하지 않는 이미지입니다." to "Image not found.",
                "유효하지 않거나 만료된 인증 토큰입니다." to "This authentication token is invalid or expired.",
                "인증 토큰의 사용자 정보가 올바르지 않습니다." to "The user information in this token is invalid.",
                "인증 토큰의 필수 정보가 올바르지 않습니다." to "Required token information is invalid.",
                "로그인 코드가 유효하지 않거나 만료되었습니다." to "This login code is invalid or expired.",
            )
    }
}
