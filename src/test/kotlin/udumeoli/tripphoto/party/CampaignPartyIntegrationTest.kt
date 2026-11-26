package udumeoli.tripphoto.party

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.image.entity.Image
import udumeoli.tripphoto.image.repository.ImageRepository
import udumeoli.tripphoto.party.entity.CampaignPartyTemplate
import udumeoli.tripphoto.party.entity.Party
import udumeoli.tripphoto.party.entity.PartyMember
import udumeoli.tripphoto.party.repository.CampaignPartyInstanceRepository
import udumeoli.tripphoto.party.repository.CampaignPartyTemplateRepository
import udumeoli.tripphoto.party.repository.CampaignPlaceRepository
import udumeoli.tripphoto.party.repository.PartyMemberRepository
import udumeoli.tripphoto.party.repository.PartyRepository
import udumeoli.tripphoto.party.service.PartyCommandService
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
class CampaignPartyIntegrationTest {
    @Autowired lateinit var partyJoinService: PartyJoinService

    @Autowired lateinit var partyCommandService: PartyCommandService

    @Autowired lateinit var userRepository: ServiceUserRepository

    @Autowired lateinit var partyRepository: PartyRepository

    @Autowired lateinit var partyMemberRepository: PartyMemberRepository

    @Autowired lateinit var templateRepository: CampaignPartyTemplateRepository

    @Autowired lateinit var instanceRepository: CampaignPartyInstanceRepository

    @Autowired lateinit var placeRepository: CampaignPlaceRepository

    @Autowired lateinit var tripRepository: TripRepository

    @Autowired lateinit var recordRepository: TripRecordRepository

    @Autowired lateinit var tripImageRepository: TripImageRepository

    @Autowired lateinit var imageRepository: ImageRepository

    @BeforeEach
    fun clearData() {
        placeRepository.deleteAll()
        instanceRepository.deleteAll()
        templateRepository.deleteAll()
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
    fun `same campaign invite creates one separate party per fan`() {
        val (template, _) = createTemplate()
        val firstFan = createUser("팬하나")
        val secondFan = createUser("팬둘")

        val preview = partyJoinService.partyPreview(requireNotNull(firstFan.id), template.inviteCode)
        assertThat(preview.memberCount).isEqualTo(5)

        val first = partyJoinService.joinParty(requireNotNull(firstFan.id), template.inviteCode)
        val second = partyJoinService.joinParty(requireNotNull(secondFan.id), template.inviteCode)

        assertThat(first.id).isNotEqualTo(second.id)
        assertThat(first.members).hasSize(6)
        assertThat(second.members).hasSize(6)
        assertThat(partyMemberRepository.countByPartyId(requireNotNull(template.id))).isEqualTo(5)
        assertThat(instanceRepository.findByPartyId(requireNotNull(first.id))).isNotNull()
        assertThat(instanceRepository.findByPartyId(requireNotNull(second.id))).isNotNull()

        assertThatThrownBy { partyJoinService.joinParty(requireNotNull(firstFan.id), template.inviteCode) }
            .isInstanceOf(GraphQlDomainException::class.java)
            .extracting { (it as GraphQlDomainException).code }
            .isEqualTo(GraphQlErrorCode.ALREADY_JOINED_PARTY)

        partyCommandService.leaveParty(requireNotNull(firstFan.id), requireNotNull(first.id))
        assertThat(partyRepository.findById(requireNotNull(first.id))).isEmpty()
        assertThat(instanceRepository.findByPartyId(requireNotNull(first.id))).isNull()
        assertThat(partyRepository.findById(requireNotNull(second.id))).isPresent()
    }

    @Test
    fun `shared campaign image survives when a fan leaves a cloned party`() {
        val (template, personas) = createTemplate()
        val sourceImage =
            imageRepository.save(
                Image(
                    objectKey = "original/campaign.jpg",
                    originalUrl = "https://example.com/original/campaign.jpg",
                    thumbnailUrl = "https://example.com/thumbnail/campaign.jpg",
                    uploaderId = requireNotNull(personas.first().id),
                    encryptedKey = "encrypted-key",
                ),
            )
        val sourceTrip = tripRepository.save(Trip(partyId = requireNotNull(template.id), regionCode = "38110"))
        val sourceRecord =
            recordRepository.save(
                TripRecord(
                    tripId = requireNotNull(sourceTrip.id),
                    serviceUserId = requireNotNull(personas.first().id),
                    keyword = TripKeyword.FOOD,
                    comment = "창원역",
                ),
            )
        tripImageRepository.save(
            TripImage(tripRecordId = requireNotNull(sourceRecord.id), imageId = requireNotNull(sourceImage.id)),
        )

        val fan = createUser("팬하나")
        val joined = partyJoinService.joinParty(requireNotNull(fan.id), template.inviteCode)
        val clonedTrip = tripRepository.findAllByPartyId(requireNotNull(joined.id)).single()
        val clonedRecord = recordRepository.findAllByTripId(requireNotNull(clonedTrip.id)).single()
        val clonedLink = tripImageRepository.findAllByTripRecordId(requireNotNull(clonedRecord.id)).single()
        assertThat(clonedLink.imageId).isEqualTo(sourceImage.id)
        assertThat(clonedRecord.comment).isEqualTo("창원역")

        partyCommandService.leaveParty(requireNotNull(fan.id), requireNotNull(joined.id))
        assertThat(imageRepository.findById(requireNotNull(sourceImage.id))).isPresent()
    }

    private fun createTemplate(): Pair<Party, List<ServiceUser>> {
        val personas = (1..5).map { createUser("가상$it") }
        val template =
            partyRepository.save(
                Party(
                    partyName = "팬 코스",
                    inviteCode = "fan123",
                    ownerId = requireNotNull(personas.first().id),
                ),
            )
        personas.forEach { persona ->
            partyMemberRepository.save(
                PartyMember(partyId = requireNotNull(template.id), serviceUserId = requireNotNull(persona.id)),
            )
        }
        templateRepository.save(
            CampaignPartyTemplate(
                partyId = requireNotNull(template.id),
                campaignKey = "test-campaign",
                artistName = "테스트",
                regionCode = "38110",
            ),
        )
        return template to personas
    }

    private fun createUser(nickname: String) = userRepository.save(ServiceUser(nickname = nickname, profileImage = 1))
}
