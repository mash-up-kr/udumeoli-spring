package udumeoli.tripphoto.trip.entity

/**
 * 기록하기 플로우에서 고르는 여행 키워드 5종.
 *
 * 대표 스티커가 동률일 때 기획이 "ㄱㄴㄷ순으로 가장 첫 번째 키워드"를 고른다.
 */
enum class TripKeyword(
    val koreanName: String,
) {
    FOOD("맛집"),
    DESSERT("디저트"),
    HEALING("힐링"),
    ACTIVITY("액티비티"),
    PHOTO("사진"),
}
