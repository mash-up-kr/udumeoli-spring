package udumeoli.tripphoto.user.entity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ServiceUserTest {
    @Test
    fun `프로필 이미지가 전달되지 않으면 기존 이미지를 유지한다`() {
        val user = ServiceUser(id = 1, nickname = "기존", profileImage = 1)

        val updated = user.updateProfile(nickname = "변경", profileImage = null)

        assertThat(updated.nickname).isEqualTo("변경")
        assertThat(updated.profileImage).isEqualTo(1)
    }

    @Test
    fun `프로필 이미지가 전달되면 새 이미지로 변경한다`() {
        val user = ServiceUser(id = 1, nickname = "기존", profileImage = 1)

        val updated = user.updateProfile(nickname = "변경", profileImage = 2)

        assertThat(updated.nickname).isEqualTo("변경")
        assertThat(updated.profileImage).isEqualTo(2)
    }
}
