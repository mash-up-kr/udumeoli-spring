package udumeoli.tripphoto.config

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportRuntimeHints

/**
 * JJWT 0.12.3은 Jwts 팩토리와 알고리즘 레지스트리 구현체를 클래스명으로 로딩한다.
 * 서명 토큰만 검증해도 DefaultJwtParserBuilder가 ENC/KEY/SIG/ZIP 전체를 초기화하므로
 * StandardEncryptionAlgorithms 등이 누락되면 Jwts.ENC 초기화가 실패한다.
 */
class JjwtNativeHints : RuntimeHintsRegistrar {
    override fun registerHints(
        hints: RuntimeHints,
        classLoader: ClassLoader?,
    ) {
        val types =
            listOf(
                "io.jsonwebtoken.impl.DefaultJwtParserBuilder",
                "io.jsonwebtoken.impl.DefaultJwtBuilder",
                "io.jsonwebtoken.impl.DefaultClaimsBuilder",
                "io.jsonwebtoken.impl.DefaultJwtHeaderBuilder",
                "io.jsonwebtoken.impl.security.StandardEncryptionAlgorithms",
                "io.jsonwebtoken.impl.security.StandardKeyAlgorithms",
                "io.jsonwebtoken.impl.security.StandardSecureDigestAlgorithms",
                // ProtectedHeader/JWK 파라미터 변환도 파서 초기화 시 함께 준비된다.
                "io.jsonwebtoken.impl.security.StandardKeyOperations",
                "io.jsonwebtoken.impl.security.StandardCurves",
                "io.jsonwebtoken.impl.security.StandardHashAlgorithms",
                "io.jsonwebtoken.impl.security.DefaultKeyOperationBuilder",
                "io.jsonwebtoken.impl.security.DefaultKeyOperationPolicyBuilder",
                "io.jsonwebtoken.impl.security.DefaultDynamicJwkBuilder",
                "io.jsonwebtoken.impl.security.DefaultJwkParserBuilder",
                "io.jsonwebtoken.impl.security.DefaultJwkSetBuilder",
                "io.jsonwebtoken.impl.security.DefaultJwkSetParserBuilder",
                "io.jsonwebtoken.impl.io.StandardCompressionAlgorithms",
                "io.jsonwebtoken.jackson.io.JacksonSerializer",
                "io.jsonwebtoken.jackson.io.JacksonDeserializer",
            )
        types.forEach { type ->
            hints.reflection().registerType(
                Class.forName(type, false, classLoader),
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
            )
        }
        listOf("io.jsonwebtoken.impl.security.KeysBridge", "io.jsonwebtoken.impl.security.JwksBridge").forEach { type ->
            hints.reflection().registerType(
                Class.forName(type, false, classLoader),
                MemberCategory.INVOKE_PUBLIC_METHODS,
            )
        }
        hints.resources().registerPattern("META-INF/services/io.jsonwebtoken.io.Serializer")
        hints.resources().registerPattern("META-INF/services/io.jsonwebtoken.io.Deserializer")
    }
}

@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(JjwtNativeHints::class)
class JjwtNativeHintsConfig
