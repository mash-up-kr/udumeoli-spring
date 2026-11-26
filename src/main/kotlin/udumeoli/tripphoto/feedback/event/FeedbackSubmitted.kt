package udumeoli.tripphoto.feedback.event

import udumeoli.tripphoto.feedback.entity.Feedback

data class FeedbackSubmitted(
    val feedback: Feedback,
)
