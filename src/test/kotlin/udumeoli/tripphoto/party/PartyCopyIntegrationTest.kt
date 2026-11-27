package udumeoli.tripphoto.party

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import udumeoli.tripphoto.image.entity.Image
import udumeoli.tripphoto.image.repository.ImageRepository
import udumeoli.tripphoto.party.entity.Party
import udumeoli.tripphoto.party.entity.PartyMember
import udumeoli.tripphoto.party.repository.PartyMemberRepository
import udumeoli.tripphoto.party.repository.PartyRepository
import udumeoli.tripphoto.party.service.PartyCommandService
import udumeoli.tripphoto.party.service.PartyCopyService
import udumeoli.tripphoto.party.service.PartyJoinService
import udumeoli.tripphoto.trip.entity.Trip
import udumeoli.tripphoto.trip.entity.TripImage
import udumeoli.tripphoto.trip.entity.TripKeyword
import udumeoli.tripphoto.trip.entity.TripRecord
import udumeoli.tripphoto.trip.repository.TripImageRepository
import udumeoli.tripphoto.trip.repository.TripRecordRepository
import udumeoli.tripphoto.trip.repository.TripRepository
import udumeoli.tripphoto.user.entity.ServiceUser
import udumeoli.tripphoto.user.repository.ServiceUserRepository

@SpringBootTest
@ActiveProfiles("local")
class PartyCopyIntegrationTest {
    @Autowired lateinit var partyJoinService: PartyJoinService

    @Autowired lateinit var partyCopyService: PartyCopyService

    @Autowired lateinit var partyCommandService: PartyCommandService

    @Autowired lateinit var userRepository: ServiceUserRepository

    @Autowired lateinit var partyRepository: PartyRepository

    @Autowired lateinit var partyMemberRepository: PartyMemberRepository

    @Autowired lateinit var tripRepository: TripRepository

    @Autowired lateinit var recordRepository: TripRecordRepository

    @Autowired lateinit var tripImageRepository: TripImageRepository

    @Autowired lateinit var imageRepository: ImageRepository

    @BeforeEach
    fun clearData() {
        tripImageRepository.deleteAll()
        recordRepository.deleteAll()
        tripRepository.deleteAll()
        imageRepository.deleteAll()
        partyMemberRepository.deleteAll()
        partyRepository.deleteAll()
        userRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() = clearData()

    @Test
    fun `joinParty keeps joining the source while copyParty creates an independent party per user`() {
        val (source, _) = createSource(memberCount = 4)
        val joinedUser = createUser("일반 참여자")
        val firstCopyUser = createUser("첫 복사 참여자")
        val secondCopyUser = createUser("둘째 복사 참여자")

        val joined = partyJoinService.joinParty(requireNotNull(joinedUser.id), source.inviteCode)
        val first = partyCopyService.copyParty(requireNotNull(firstCopyUser.id), source.inviteCode)
        val second = partyCopyService.copyParty(requireNotNull(secondCopyUser.id), source.inviteCode)

        assertThat(joined.id).isEqualTo(source.id)
        assertThat(first.id).isNotEqualTo(source.id).isNotEqualTo(second.id)
        assertThat(first.inviteCode).isNotEqualTo(source.inviteCode)
        assertThat(first.members).hasSize(6)
        assertThat(second.members).hasSize(6)
        assertThat(partyMemberRepository.countByPartyId(requireNotNull(source.id))).isEqualTo(5)
        val repeated = partyCopyService.copyParty(requireNotNull(firstCopyUser.id), source.inviteCode)
        assertThat(repeated.id).isNotEqualTo(first.id).isNotEqualTo(second.id)
        assertThat(partyMemberRepository.countByPartyId(requireNotNull(source.id))).isEqualTo(5)
    }

    @Test
    fun `a full source and a copied party can both be copied without a member limit`() {
        val (source, _) = createSource(memberCount = 6)
        val firstUser = createUser("첫 복사")
        val secondUser = createUser("둘째 복사")

        val first = partyCopyService.copyParty(requireNotNull(firstUser.id), source.inviteCode)
        val second = partyCopyService.copyParty(requireNotNull(secondUser.id), first.inviteCode)

        assertThat(first.members).hasSize(7)
        assertThat(second.members).hasSize(8)
        assertThat(partyMemberRepository.countByPartyId(requireNotNull(source.id))).isEqualTo(6)
    }

    @Test
    fun `copied photos survive source deletion and leaving a copy does not delete its other members`() {
        val (source, members) = createSource(memberCount = 5)
        val image =
            imageRepository.save(
                Image(
                    objectKey = "original/copy-test.jpg",
                    originalUrl = "https://example.com/copy-test.jpg",
                    uploaderId = requireNotNull(members.first().id),
                    encryptedKey = "encrypted-key",
                ),
            )
        val trip = tripRepository.save(Trip(partyId = requireNotNull(source.id), regionCode = "38110"))
        val record =
            recordRepository.save(
                TripRecord(
                    tripId = requireNotNull(trip.id),
                    serviceUserId = requireNotNull(members.first().id),
                    keyword = TripKeyword.FOOD,
                    comment = "창원역",
                ),
            )
        tripImageRepository.save(
            TripImage(tripRecordId = requireNotNull(record.id), imageId = requireNotNull(image.id)),
        )

        val copyUser = createUser("복사 참여자")
        val copy = partyCopyService.copyParty(requireNotNull(copyUser.id), source.inviteCode)
        val copiedTrip = tripRepository.findAllByPartyId(copy.id).single()
        val copiedRecord = recordRepository.findAllByTripId(requireNotNull(copiedTrip.id)).single()
        assertThat(copiedRecord.comment).isEqualTo("창원역")
        val copiedImage = tripImageRepository.findAllByTripRecordId(requireNotNull(copiedRecord.id)).single()
        assertThat(copiedImage.imageId).isEqualTo(image.id)

        partyCommandService.deleteParty(requireNotNull(members.first().id), requireNotNull(source.id))
        assertThat(imageRepository.findById(requireNotNull(image.id))).isPresent()

        partyCommandService.leaveParty(requireNotNull(copyUser.id), copy.id)
        assertThat(partyRepository.findById(copy.id)).isPresent()
        assertThat(partyMemberRepository.countByPartyId(copy.id)).isEqualTo(5)
        assertThat(imageRepository.findById(requireNotNull(image.id))).isPresent()
    }

    private fun createSource(memberCount: Int): Pair<Party, List<ServiceUser>> {
        val members = (1..memberCount).map { createUser("멤버$it") }
        val source =
            partyRepository.save(
                Party(
                    partyName = "일반 팟",
                    inviteCode = "abc123",
                    ownerId = requireNotNull(members.first().id),
                ),
            )
        members.forEach { member ->
            partyMemberRepository.save(
                PartyMember(partyId = requireNotNull(source.id), serviceUserId = requireNotNull(member.id)),
            )
        }
        return source to members
    }

    private fun createUser(nickname: String) = userRepository.save(ServiceUser(nickname = nickname, profileImage = 1))
}
