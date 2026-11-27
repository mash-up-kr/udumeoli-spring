package udumeoli.tripphoto.party.service

import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.common.i18n.MessageKeys

@JvmInline
value class InviteCode private constructor(
    val value: String,
) {
    companion object {
        const val LENGTH = 6

        fun parse(raw: String): InviteCode {
            if (raw.length != LENGTH || raw.any { !it.isLetterOrDigit() }) {
                throw GraphQlDomainException(GraphQlErrorCode.INVALID_INVITE_CODE, MessageKeys.Invite.INVALID_FORMAT)
            }
            return InviteCode(raw)
        }
    }
}
