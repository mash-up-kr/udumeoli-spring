package udumeoli.tripphoto.user.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.image.service.ImageService
import udumeoli.tripphoto.user.dto.UserPayload
import udumeoli.tripphoto.user.dto.toPayload
import udumeoli.tripphoto.user.entity.Nickname
import udumeoli.tripphoto.user.entity.ServiceUser
import udumeoli.tripphoto.user.repository.ServiceUserRepository

@Service
class UserService(
    private val serviceUserRepository: ServiceUserRepository,
    private val imageService: ImageService,
    @org.springframework.beans.factory.annotation.Value("\${app.api-base-url}") private val apiBaseUrl: String,
) {
    @Transactional(readOnly = true)
    fun me(currentUserId: Long): UserPayload {
        val user = getCurrentUser(currentUserId)
        return user.toPayload().copy(profileImageUrl = resolveProfileImageUrl(user.profileImage))
    }

    private fun resolveProfileImageUrl(profileImage: Long): String? {
        if (ServiceUser.isPresetProfileImage(profileImage)) return null
        // 썸네일/원본 여부에 상관없이 브라우저는 무조건 프록시를 통과해야 이미지를 스트리밍 받을 수 있습니다.
        return imageService.findImageOrNull(profileImage)?.let { "${apiBaseUrl.trimEnd('/')}/api/images/${it.id}" }
    }

    @Suppress("ForbiddenComment")
    @Transactional
    fun updateProfile(
        currentUserId: Long,
        nickname: String,
        profileImage: Long?,
    ): UserPayload {
        val normalizedNickname =
            Nickname.normalizeOrNull(nickname)
                ?: throw GraphQlDomainException(GraphQlErrorCode.VALIDATION_ERROR, Nickname.RULE_MESSAGE)
        // TODO: 업로드한 프로필 사진(프리셋 아님)으로 바꾼 경우 썸네일 생성 요청 필요
        val user = getCurrentUser(currentUserId)
        return serviceUserRepository
            .save(user.updateProfile(normalizedNickname, profileImage))
            .toPayload()
    }

    @Transactional(readOnly = true)
    fun getCurrentUser(currentUserId: Long): ServiceUser =
        serviceUserRepository.findById(currentUserId).orElseThrow {
            GraphQlDomainException(GraphQlErrorCode.UNAUTHENTICATED, "로그인이 필요합니다.")
        }

    @Transactional(readOnly = true)
    fun findAllById(userIds: Iterable<Long>): List<ServiceUser> = serviceUserRepository.findAllById(userIds).toList()
}
