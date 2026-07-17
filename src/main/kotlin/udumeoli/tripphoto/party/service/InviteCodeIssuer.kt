package udumeoli.tripphoto.party.service

import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Component
import udumeoli.tripphoto.party.entity.Party
import java.security.SecureRandom

@Component
class InviteCodeIssuer {
    fun saveWithUniqueInviteCode(save: (String) -> Party): Party {
        repeat(INVITE_CODE_GENERATION_ATTEMPTS) {
            try {
                return save(generateInviteCode())
            } catch (e: DuplicateKeyException) {
                if (it == INVITE_CODE_GENERATION_ATTEMPTS - 1) {
                    throw e
                }
            }
        }
        error("초대코드 생성에 실패했습니다.")
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
