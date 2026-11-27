package udumeoli.tripphoto.party.service

import org.springframework.stereotype.Component
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.common.i18n.MessageKeys
import udumeoli.tripphoto.party.repository.PartyRepository
import java.security.SecureRandom

@Component
class InviteCodeIssuer(
    private val partyRepository: PartyRepository,
) {
    fun issue(): InviteCode {
        repeat(INVITE_CODE_GENERATION_ATTEMPTS) {
            val inviteCode = generateInviteCode()
            if (!partyRepository.existsByInviteCode(inviteCode.value)) {
                return inviteCode
            }
        }
        throw GraphQlDomainException(
            GraphQlErrorCode.INVITE_CODE_CONFLICT,
            MessageKeys.Invite.CONFLICT,
        )
    }

    private fun generateInviteCode(): InviteCode {
        while (true) {
            val code =
                (1..InviteCode.LENGTH)
                    .map { INVITE_CODE_ALPHABET[random.nextInt(INVITE_CODE_ALPHABET.length)] }
                    .joinToString("")
            if (code.any { it.isLetter() } && code.any { it.isDigit() }) {
                return InviteCode.parse(code)
            }
        }
    }

    companion object {
        private const val INVITE_CODE_GENERATION_ATTEMPTS = 10
        private const val INVITE_CODE_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"
        private val random = SecureRandom()
    }
}
