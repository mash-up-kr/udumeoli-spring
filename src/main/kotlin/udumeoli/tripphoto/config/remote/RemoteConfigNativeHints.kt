package udumeoli.tripphoto.config.remote

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportRuntimeHints

/** Google HTTP Client의 @Key 필드와 중첩 응답 DTO는 런타임 리플렉션으로 읽힌다. */
class RemoteConfigNativeHints : RuntimeHintsRegistrar {
    override fun registerHints(
        hints: RuntimeHints,
        classLoader: ClassLoader?,
    ) {
        hints.resources().registerPattern("admin_sdk.properties")
        val types =
            listOf(
                "com.google.firebase.remoteconfig.internal.ServerTemplateResponse",
                "com.google.firebase.remoteconfig.internal.TemplateResponse",
                "com.google.firebase.remoteconfig.internal.RemoteConfigServiceErrorResponse",
                "com.google.api.client.auth.oauth2.TokenResponse",
                "com.google.api.client.auth.oauth2.TokenErrorResponse",
                "com.google.api.client.json.GenericJson",
                "com.google.api.client.http.HttpHeaders",
                "com.google.api.client.json.webtoken.JsonWebToken\$Header",
                "com.google.api.client.json.webtoken.JsonWebToken\$Payload",
                "com.google.api.client.json.webtoken.JsonWebSignature\$Header",
            )
        types.forEach { register(hints, Class.forName(it, false, classLoader)) }
    }

    private fun register(
        hints: RuntimeHints,
        type: Class<*>,
    ) {
        hints.reflection().registerType(
            type,
            MemberCategory.DECLARED_FIELDS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_PUBLIC_METHODS,
        )
        type.declaredClasses.forEach { register(hints, it) }
    }
}

@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(RemoteConfigNativeHints::class)
class RemoteConfigNativeHintsConfig
