package udumeoli.tripphoto.trip.dto

/** 이미 있는 여행에 내 기록을 남긴다(있으면 덮어쓴다). 아코디언의 [기록하기] CTA. */
data class RecordTripInput(
    val tripId: Long,
    val images: List<TripImageInput>,
    val comment: String? = null,
)
