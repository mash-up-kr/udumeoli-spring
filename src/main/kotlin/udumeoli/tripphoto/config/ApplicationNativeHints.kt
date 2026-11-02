package udumeoli.tripphoto.config

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportRuntimeHints

/** 외부 라이브러리가 동적으로 사용하는 타입과 리소스를 Native Image에 등록한다. */
class ApplicationNativeHints : RuntimeHintsRegistrar {
    override fun registerHints(
        hints: RuntimeHints,
        classLoader: ClassLoader?,
    ) {
        registerFlywayHints(hints, classLoader)
        registerJjwtHints(hints, classLoader)
        registerGoogleClientHints(hints, classLoader)
    }

    /**
     * Flyway 11.7.2는 ConfigurationExtension과 CleanModel을 Jackson으로 복사한다.
     * getter/setter/생성자 등이 누락되면 OracleConfigurationExtension.getWalletLocation 등에서
     * MissingReflectionRegistrationError가 발생한다. 버전에 따라 없는 타입은 건너뛴다.
     */
    private fun registerFlywayHints(
        hints: RuntimeHints,
        classLoader: ClassLoader?,
    ) {
        val types =
            listOf(
                // flyway-database-oracle
                "org.flywaydb.database.oracle.OracleConfigurationExtension",
                // flyway-core ConfigurationExtension 구현체
                "org.flywaydb.core.internal.command.clean.CleanModeConfigurationExtension",
                "org.flywaydb.core.internal.configuration.extensions.DeployScriptFilenameConfigurationExtension",
                "org.flywaydb.core.internal.configuration.extensions.PrepareScriptFilenameConfigurationExtension",
                "org.flywaydb.core.api.migration.baseline.BaselineMigrationConfigurationExtension",
                "org.flywaydb.core.internal.proprietaryStubs.LicensingConfigurationExtensionStub",
                "org.flywaydb.core.internal.proprietaryStubs.PATTokenConfigurationExtensionStub",
                "org.flywaydb.core.internal.publishing.PublishingConfigurationExtension",
                // 위 확장이 Jackson 직렬화 시 재귀 참조하는 모델
                "org.flywaydb.core.internal.command.clean.CleanModel",
                "org.flywaydb.core.internal.command.clean.SchemaModel",
            )
        val categories =
            arrayOf(
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.DECLARED_FIELDS,
            )
        types.forEach { type ->
            hints.reflection().registerTypeIfPresent(classLoader, type, *categories)
        }
    }

    /** JJWT 0.12.3은 파서와 ENC/KEY/SIG/ZIP 및 JWK 레지스트리를 클래스명으로 로딩한다. */
    private fun registerJjwtHints(
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

    /** Google 로그인과 Firebase 인증/응답 파싱에 쓰는 @Key 필드, 생성자와 SDK 리소스. */
    private fun registerGoogleClientHints(
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
                // Google ID 토큰은 별도 Payload 타입과 상위 타입의 @Key 필드도 파싱한다.
                "com.google.api.client.googleapis.auth.oauth2.GoogleIdToken\$Payload",
                "com.google.api.client.auth.openidconnect.IdToken\$Payload",
            )
        types.forEach { registerGoogleJsonType(hints, Class.forName(it, false, classLoader)) }
    }

    private fun registerGoogleJsonType(
        hints: RuntimeHints,
        type: Class<*>,
    ) {
        hints.reflection().registerType(
            type,
            MemberCategory.DECLARED_FIELDS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_PUBLIC_METHODS,
        )
        type.declaredClasses.forEach { registerGoogleJsonType(hints, it) }
    }
}

@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(ApplicationNativeHints::class)
class ApplicationNativeHintsConfig
