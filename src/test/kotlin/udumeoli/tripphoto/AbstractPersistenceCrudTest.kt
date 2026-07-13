package udumeoli.tripphoto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import udumeoli.tripphoto.image.entity.Image
import udumeoli.tripphoto.image.repository.ImageRepository
import udumeoli.tripphoto.party.entity.Party
import udumeoli.tripphoto.party.entity.PartyMember
import udumeoli.tripphoto.party.repository.PartyMemberRepository
import udumeoli.tripphoto.party.repository.PartyRepository
import udumeoli.tripphoto.region.entity.Region
import udumeoli.tripphoto.region.repository.RegionRepository
import udumeoli.tripphoto.trip.entity.Trip
import udumeoli.tripphoto.trip.entity.TripImage
import udumeoli.tripphoto.trip.repository.TripImageRepository
import udumeoli.tripphoto.trip.repository.TripRepository
import udumeoli.tripphoto.user.entity.ServiceUser
import udumeoli.tripphoto.user.entity.SocialAccount
import udumeoli.tripphoto.user.repository.ServiceUserRepository
import udumeoli.tripphoto.user.repository.SocialAccountRepository
import java.time.LocalDate

/**
 * 엔티티 8개 CRUD 공통 검증 (M0-4 완료 조건).
 * FK 의존 순서대로 생성하고 역순으로 삭제하므로 테스트 순서에 의미가 있다.
 * 데이터소스는 서브클래스가 결정한다:
 * - [PersistenceSmokeTest] H2 MODE=Oracle (빠른 로컬 피드백)
 * - [OraclePersistenceIntegrationTest] 실제 Oracle Free 컨테이너 (M0-5)
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
abstract class AbstractPersistenceCrudTest {
    @Autowired lateinit var serviceUserRepository: ServiceUserRepository

    @Autowired lateinit var socialAccountRepository: SocialAccountRepository

    @Autowired lateinit var partyRepository: PartyRepository

    @Autowired lateinit var partyMemberRepository: PartyMemberRepository

    @Autowired lateinit var regionRepository: RegionRepository

    @Autowired lateinit var imageRepository: ImageRepository

    @Autowired lateinit var tripRepository: TripRepository

    @Autowired lateinit var tripImageRepository: TripImageRepository

    private lateinit var user: ServiceUser
    private lateinit var party: Party
    private lateinit var image: Image
    private lateinit var trip: Trip

    private val regionCode = "11110"

    @Test
    @Order(1)
    fun `ServiceUser CRUD`() {
        user = serviceUserRepository.save(ServiceUser(nickname = "테스트유저"))
        assertThat(user.id).isNotNull()
        assertThat(user.auditMetadata.createdAt).isNotNull()

        val renamed = serviceUserRepository.save(user.copy(nickname = "바뀐닉네임"))
        assertThat(serviceUserRepository.findByNickname("바뀐닉네임")?.id).isEqualTo(user.id)
        user = renamed
    }

    @Test
    @Order(2)
    fun `SocialAccount CRUD`() {
        val saved =
            socialAccountRepository.save(
                SocialAccount(
                    serviceUserId = user.id!!,
                    provider = "KAKAO",
                    providerUserId = "kakao-12345",
                    providerEmail = "test@example.com",
                ),
            )
        assertThat(saved.id).isNotNull()

        val found = socialAccountRepository.findByProviderAndProviderUserId("KAKAO", "kakao-12345")
        assertThat(found?.serviceUserId).isEqualTo(user.id)

        socialAccountRepository.save(saved.copy(providerEmail = "changed@example.com"))
        assertThat(socialAccountRepository.findById(saved.id!!).get().providerEmail)
            .isEqualTo("changed@example.com")
    }

    @Test
    @Order(3)
    fun `Party CRUD (ownerId 포함)`() {
        party = partyRepository.save(Party(partyName = "우두머리팟", inviteCode = "ABC123", ownerId = user.id!!))
        assertThat(party.id).isNotNull()

        val found = partyRepository.findByInviteCode("ABC123")
        assertThat(found?.ownerId).isEqualTo(user.id)

        party = partyRepository.save(party.copy(partyName = "이름변경팟"))
        assertThat(partyRepository.findById(party.id!!).get().partyName).isEqualTo("이름변경팟")
    }

    @Test
    @Order(4)
    fun `PartyMember CRUD`() {
        val member = partyMemberRepository.save(PartyMember(partyId = party.id!!, serviceUserId = user.id!!))
        assertThat(member.id).isNotNull()
        assertThat(partyMemberRepository.existsByPartyIdAndServiceUserId(party.id!!, user.id!!)).isTrue()
        assertThat(partyMemberRepository.findAllByPartyId(party.id!!)).hasSize(1)
    }

    @Test
    @Order(5)
    fun `Region CRUD (자연키 - Persistable isNew)`() {
        // 자연키 엔티티: 신규 행은 Region.of()로 만들어야 save()가 INSERT를 수행한다 (docs/kanban.md 함정 참고)
        val inserted = regionRepository.save(Region.of(regionCode, "서울특별시 종로구"))
        assertThat(inserted.auditMetadata.createdAt).isNotNull()

        // copy()나 DB에서 읽어온 인스턴스는 isNew=false → save()가 UPDATE로 동작한다
        regionRepository.save(inserted.copy(regionName = "서울 종로구"))
        assertThat(regionRepository.findById(regionCode).get().regionName).isEqualTo("서울 종로구")
        assertThat(regionRepository.findByRegionName("서울 종로구")).isNotNull()
    }

    @Test
    @Order(6)
    fun `Image CRUD (nullable thumbnail-uploader)`() {
        image =
            imageRepository.save(
                Image(originalUrl = "https://storage.example.com/o/1.jpg"),
            )
        assertThat(image.id).isNotNull()
        assertThat(image.thumbnailUrl).isNull()
        assertThat(image.uploaderId).isNull()
        assertThat(image.createdAt).isNotNull()

        image =
            imageRepository.save(
                image.copy(
                    thumbnailUrl = "https://storage.example.com/t/1.jpg",
                    uploaderId = user.id,
                ),
            )
        val found = imageRepository.findById(image.id!!).get()
        assertThat(found.thumbnailUrl).isEqualTo("https://storage.example.com/t/1.jpg")
        assertThat(imageRepository.findAllByUploaderId(user.id!!)).hasSize(1)
    }

    @Test
    @Order(7)
    fun `Trip CRUD (createdBy nullable)`() {
        trip =
            tripRepository.save(
                Trip(
                    partyId = party.id!!,
                    regionCode = regionCode,
                    color = "#FF5733",
                    startDate = LocalDate.of(2026, 7, 1),
                    endDate = LocalDate.of(2026, 7, 3),
                    createdBy = user.id,
                ),
            )
        assertThat(trip.id).isNotNull()
        assertThat(trip.auditMetadata.createdAt).isNotNull()
        assertThat(tripRepository.findAllByPartyId(party.id!!)).hasSize(1)

        // createdBy 없이도 저장 가능해야 한다 (탈퇴 사용자 대비)
        val anonymous =
            tripRepository.save(
                Trip(
                    partyId = party.id!!,
                    regionCode = regionCode,
                    startDate = LocalDate.of(2026, 7, 5),
                    endDate = LocalDate.of(2026, 7, 6),
                ),
            )
        assertThat(anonymous.createdBy).isNull()
        tripRepository.deleteById(anonymous.id!!)
    }

    @Test
    @Order(8)
    fun `TripImage CRUD`() {
        val tripImage =
            tripImageRepository.save(
                TripImage(tripId = trip.id!!, imageId = image.id!!, imageDate = LocalDate.of(2026, 7, 2)),
            )
        assertThat(tripImage.id).isNotNull()
        assertThat(tripImage.createdAt).isNotNull()
        assertThat(tripImageRepository.existsByTripIdAndImageId(trip.id!!, image.id!!)).isTrue()
        assertThat(tripImageRepository.findAllByTripId(trip.id!!)).hasSize(1)
    }

    @Test
    @Order(9)
    fun `FK 역순 전체 삭제`() {
        tripImageRepository.deleteAll()
        tripRepository.deleteAll()
        imageRepository.deleteAll()
        regionRepository.deleteAll()
        partyMemberRepository.deleteAll()
        partyRepository.deleteAll()
        socialAccountRepository.deleteAll()
        serviceUserRepository.deleteAll()

        assertThat(tripRepository.count()).isZero()
        assertThat(partyRepository.count()).isZero()
        assertThat(serviceUserRepository.count()).isZero()
    }
}
