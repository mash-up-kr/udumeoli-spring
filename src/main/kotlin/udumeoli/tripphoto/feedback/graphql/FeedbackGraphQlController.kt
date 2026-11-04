package udumeoli.tripphoto.feedback.graphql

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.stereotype.Controller
import udumeoli.tripphoto.auth.annotation.LoginUser
import udumeoli.tripphoto.feedback.dto.SubmitFeedbackInput
import udumeoli.tripphoto.feedback.service.FeedbackService

@Controller
class FeedbackGraphQlController(
    private val feedbackService: FeedbackService,
) {
    @MutationMapping
    fun submitFeedback(
        @LoginUser currentUserId: Long,
        @Argument input: SubmitFeedbackInput,
    ): Boolean {
        feedbackService.submit(currentUserId, input)
        return true
    }
}
