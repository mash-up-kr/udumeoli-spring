package udumeoli.tripphoto.region.service

import org.springframework.stereotype.Service
import udumeoli.tripphoto.region.entity.Region
import udumeoli.tripphoto.region.repository.RegionRepository
import java.util.Locale

/** Static migration data is read once, so a map request never issues one query per region. */
@Service
class RegionNameService(
    private val regionRepository: RegionRepository,
) {
    private val regionsByCode: Map<String, Region> by lazy {
        regionRepository.findAll().associateBy { it.regionCode }
    }

    fun all(locale: Locale): List<RegionNamePayload> =
        regionsByCode.values
            .sortedBy { it.regionCode }
            .map { region ->
                RegionNamePayload(
                    regionCode = region.regionCode,
                    name = localizedName(region, locale),
                    nameKo = region.regionName,
                    nameEn = region.nameEng ?: region.regionName,
                )
            }

    fun name(
        regionCode: String,
        locale: Locale,
    ): String =
        if (regionCode == COUNTRY_CODE) {
            if (locale.language == Locale.ENGLISH.language) "South Korea" else "대한민국"
        } else {
            regionsByCode[regionCode]?.let { localizedName(it, locale) } ?: regionCode
        }

    private fun localizedName(
        region: Region,
        locale: Locale,
    ): String =
        if (locale.language == Locale.ENGLISH.language) {
            region.nameEng ?: region.regionName
        } else {
            region.regionName
        }

    private companion object {
        const val COUNTRY_CODE = "KR"
    }
}

data class RegionNamePayload(
    val regionCode: String,
    val name: String,
    val nameKo: String,
    val nameEn: String,
)
