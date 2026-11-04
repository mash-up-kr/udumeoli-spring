package udumeoli.tripphoto.feedback.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.feedback.dto.SubmitFeedbackInput
import udumeoli.tripphoto.feedback.entity.Feedback
import udumeoli.tripphoto.feedback.repository.FeedbackRepository
import udumeoli.tripphoto.user.service.UserService

@Service
class FeedbackService(
    private val feedbackRepository: FeedbackRepository,
    private val userService: UserService,
) {
    /**
     * 별점 + 의견을 저장한다. 유저는 토큰으로, 전송 시각은 서버가 정한다.
     *
     * 의견은 앞뒤 공백을 털어낸 값으로 검증·저장한다(FE도 trim 후 보내지만 서버가 다시 맞춘다).
     * 앱 버전·기기 정보는 진단용이라 길이 때문에 거절하지 않고 컬럼 한도에서 자른다.
     */
    @Transactional
    fun submit(
        currentUserId: Long,
        input: SubmitFeedbackInput,
    ) {
        userService.getCurrentUser(currentUserId)

        if (input.rating !in Feedback.RATING_RANGE) {
            throw GraphQlDomainException(GraphQlErrorCode.VALIDATION_ERROR, "별점은 1~5 사이여야 합니다.")
        }
        val content = input.content.trim()
        if (content.isEmpty() || content.length > Feedback.CONTENT_MAX_LENGTH) {
            throw GraphQlDomainException(GraphQlErrorCode.VALIDATION_ERROR, "의견은 공백 제외 1자 이상 5,000자 이하여야 합니다.")
        }

        feedbackRepository.save(
            Feedback(
                serviceUserId = currentUserId,
                rating = input.rating,
                content = content,
                appVersion = input.appVersion.take(Feedback.APP_VERSION_MAX_LENGTH),
                deviceInfo = input.deviceInfo.take(Feedback.DEVICE_INFO_MAX_LENGTH),
            ),
        )
    }
}
