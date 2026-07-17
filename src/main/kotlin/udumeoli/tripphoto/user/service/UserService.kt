package udumeoli.tripphoto.user.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.user.dto.UserPayload
import udumeoli.tripphoto.user.dto.toPayload
import udumeoli.tripphoto.user.entity.ServiceUser
import udumeoli.tripphoto.user.repository.ServiceUserRepository

@Service
class UserService(
    private val serviceUserRepository: ServiceUserRepository,
) {
    @Transactional(readOnly = true)
    fun me(currentUserId: Long): UserPayload = currentUser(currentUserId).toPayload()

    @Transactional
    fun updateProfile(
        currentUserId: Long,
        nickname: String,
        profileImageUrl: String?,
    ): UserPayload {
        validateNonEmpty(nickname, "닉네임을 입력해주세요.")
        val user = currentUser(currentUserId)
        return serviceUserRepository
            .save(user.updateProfile(nickname, profileImageUrl))
            .toPayload()
    }

    @Transactional(readOnly = true)
    fun currentUser(currentUserId: Long): ServiceUser =
        serviceUserRepository.findById(currentUserId).orElseThrow {
            GraphQlDomainException(GraphQlErrorCode.UNAUTHENTICATED, "로그인이 필요합니다.")
        }

    @Transactional(readOnly = true)
    fun findAllById(userIds: Iterable<Long>): List<ServiceUser> = serviceUserRepository.findAllById(userIds).toList()

    private fun validateNonEmpty(
        value: String,
        message: String,
    ) {
        if (value.isEmpty()) {
            throw GraphQlDomainException(GraphQlErrorCode.VALIDATION_ERROR, message)
        }
    }
}
