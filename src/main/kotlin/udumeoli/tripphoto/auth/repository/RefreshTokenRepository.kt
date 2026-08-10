package udumeoli.tripphoto.auth.repository

import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.ListCrudRepository
import udumeoli.tripphoto.auth.entity.RefreshToken

interface RefreshTokenRepository : ListCrudRepository<RefreshToken, Long> {
    fun findByTokenHash(tokenHash: String): RefreshToken?

    @Modifying
    @Query("DELETE FROM refresh_token WHERE id = :id AND token_hash = :tokenHash")
    fun consume(
        id: Long,
        tokenHash: String,
    ): Int

    fun deleteAllByServiceUserId(serviceUserId: Long)
}
