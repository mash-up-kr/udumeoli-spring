package udumeoli.tripphoto.auth.service

import udumeoli.tripphoto.auth.entity.OAuth2Provider

interface SocialTokenValidator {
    fun supports(provider: OAuth2Provider): Boolean

    fun validate(token: String): SocialProfile
}
