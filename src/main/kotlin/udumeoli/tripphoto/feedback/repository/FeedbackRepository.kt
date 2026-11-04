package udumeoli.tripphoto.feedback.repository

import org.springframework.data.repository.ListCrudRepository
import udumeoli.tripphoto.feedback.entity.Feedback

interface FeedbackRepository : ListCrudRepository<Feedback, Long>
