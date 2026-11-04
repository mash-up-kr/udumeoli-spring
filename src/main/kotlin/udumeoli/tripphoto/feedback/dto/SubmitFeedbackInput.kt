package udumeoli.tripphoto.feedback.dto

data class SubmitFeedbackInput(
    val rating: Int,
    val content: String,
    val appVersion: String,
    val deviceInfo: String,
)
