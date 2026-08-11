package udumeoli.tripphoto.user.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.image.service.ImageService
import udumeoli.tripphoto.user.dto.UserPayload
import udumeoli.tripphoto.user.dto.toPayload
import udumeoli.tripphoto.user.entity.ServiceUser
import udumeoli.tripphoto.user.repository.ServiceUserRepository

@Service
class UserService(
    private val serviceUserRepository: ServiceUserRepository,
    private val imageService: ImageService,
) {
    @Transactional(readOnly = true)
    fun me(currentUserId: Long): UserPayload {
        val user = getCurrentUser(currentUserId)
        return user.toPayload().copy(profileImageUrl = resolveProfileImageUrl(user.profileImage))
    }

    private fun resolveProfileImageUrl(profileImage: Long): String? =
        runCatching { imageService.getImages(listOf(profileImage)).first() }
            .getOrNull()
            ?.let { it.thumbnailUrl ?: it.originalUrl }

    @Suppress("ForbiddenComment")
    @Transactional
    fun updateProfile(
        currentUserId: Long,
        nickname: String,
        profileImage: Long?,
    ): UserPayload {
        validateNonEmpty(nickname, "닉네임을 입력해주세요.")
        // TODO: 업로드한 프로필 사진(프리셋 아님)으로 바꾼 경우 썸네일 생성 요청 필요
        val user = getCurrentUser(currentUserId)
        return serviceUserRepository
            .save(user.updateProfile(nickname, profileImage))
            .toPayload()
    }

    @Transactional(readOnly = true)
    fun getCurrentUser(currentUserId: Long): ServiceUser =
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
