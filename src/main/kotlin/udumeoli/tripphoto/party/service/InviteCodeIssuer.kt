package udumeoli.tripphoto.party.service

import org.springframework.stereotype.Component
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.party.repository.PartyRepository
import java.security.SecureRandom

@Component
class InviteCodeIssuer(
    private val partyRepository: PartyRepository,
) {
    fun issue(): String {
        repeat(INVITE_CODE_GENERATION_ATTEMPTS) {
            val inviteCode = generateInviteCode()
            if (!partyRepository.existsByInviteCode(inviteCode)) {
                return inviteCode
            }
        }
        throw GraphQlDomainException(
            GraphQlErrorCode.INVITE_CODE_CONFLICT,
            "초대코드 생성 중 충돌이 발생했습니다. 다시 시도해주세요.",
        )
    }

    private fun generateInviteCode(): String {
        while (true) {
            val code =
                (1..INVITE_CODE_LENGTH)
                    .map { INVITE_CODE_ALPHABET[random.nextInt(INVITE_CODE_ALPHABET.length)] }
                    .joinToString("")
            if (code.any { it.isLetter() } && code.any { it.isDigit() }) {
                return code
            }
        }
    }

    companion object {
        private const val INVITE_CODE_LENGTH = 6
        private const val INVITE_CODE_GENERATION_ATTEMPTS = 10
        private const val INVITE_CODE_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"
        private val random = SecureRandom()
    }
}
