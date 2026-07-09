package udumeoli.tripphoto.image.entity

enum class ImageStatus {
    /** 업로드 URL만 발급된 상태 */
    PENDING,

    /** 업로드 완료, 조회 가능 */
    ACTIVE,

    /** 소프트 삭제 */
    DELETED,
}
