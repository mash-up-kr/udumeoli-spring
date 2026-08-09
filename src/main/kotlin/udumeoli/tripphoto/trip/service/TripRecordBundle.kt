package udumeoli.tripphoto.trip.service

import udumeoli.tripphoto.image.entity.Image
import udumeoli.tripphoto.trip.entity.TripImage
import udumeoli.tripphoto.trip.entity.TripRecord
import java.time.LocalDate

/**
 * [TripRecordReader]가 한 번에 읽어 온 기록·사진을 조회 화면이 쓰기 좋게 색인해 둔 묶음.
 *
 * 두 화면이 필요로 하는 각도가 달라서 진입점도 둘이다.
 * - 여행 목록([TripQueryService.trips]): 여행 → 멤버별 기록 → 그 기록의 사진 1장
 * - 지역 카드([TripQueryService.visitedRegions]): 여러 여행 → 팟 전원의 사진을 촬영일 최신순으로
 *
 * 원본 [TripImage]는 밖으로 내보내지 않는다. 정렬 규칙과 "기록 1건에 사진 1장" 정책이
 * 여기 한 군데에만 있도록 [Image]로 바꿔서 돌려준다.
 */
class TripRecordBundle(
    records: List<TripRecord>,
    tripImages: List<TripImage>,
    private val imagesById: Map<Long, Image>,
) {
    private val recordsByTripId = records.groupBy { it.tripId }
    private val tripImagesByRecordId = tripImages.groupBy { it.tripRecordId }
    private val tripIdsByMemberId = records.groupBy({ it.serviceUserId }, { it.tripId })

    /** 기록을 남긴 멤버 id (팟을 떠난 멤버도 포함될 수 있다) */
    val recordedMemberIds: Set<Long> = tripIdsByMemberId.keys

    val uploaderIds: Set<Long> = imagesById.values.mapNotNullTo(mutableSetOf()) { it.uploaderId }

    fun recordsOf(tripId: Long): List<TripRecord> = recordsByTripId[tripId].orEmpty()

    /** 내가 기록을 남긴 여행 id */
    fun recordedTripIdsOf(userId: Long): Set<Long> = tripIdsByMemberId[userId].orEmpty().toSet()

    /** [정책] 기록 1건에 사진 1장. 과거 데이터로 여러 장이 붙어 있으면 가장 최신 것을 쓴다. */
    fun imageOfRecord(recordId: Long): Image? =
        tripImagesByRecordId[recordId]
            .orEmpty()
            .minWithOrNull(LATEST)
            ?.let { imagesById[it.imageId] }

    /** 여행 여러 건에 팟 전원이 올린 사진 — 촬영일 최신순. */
    fun imagesOfTrips(tripIds: Collection<Long>): List<Image> =
        tripIds
            .flatMap(::recordsOf)
            .flatMap { tripImagesByRecordId[requireNotNull(it.id)].orEmpty() }
            .sortedWith(LATEST)
            .mapNotNull { imagesById[it.imageId] }

    companion object {
        val EMPTY = TripRecordBundle(emptyList(), emptyList(), emptyMap())

        // 촬영일 최신순 — 촬영일 미상은 맨 뒤로, 동률이면 나중에 등록된 사진이 앞으로
        private val LATEST: Comparator<TripImage> =
            compareByDescending<TripImage> { it.imageDate ?: LocalDate.MIN }
                .thenByDescending { it.id ?: 0L }
    }
}
