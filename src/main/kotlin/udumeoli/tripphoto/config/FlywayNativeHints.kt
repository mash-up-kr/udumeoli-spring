package udumeoli.tripphoto.config

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportRuntimeHints

/**
 * GraalVM Native Image용 Flyway 리플렉션 힌트.
 *
 * Flyway는 기동 시 설정을 복사(ClassicConfiguration.configure -> PluginRegister.getCopy)하면서
 * 등록된 모든 ConfigurationExtension을 Jackson으로 직렬화/역직렬화한다.
 * native 이미지에는 이 클래스들의 getter/setter/생성자가 리플렉션 등록되어 있지 않아
 * `MissingReflectionRegistrationError: ...OracleConfigurationExtension.getWalletLocation()`으로
 * 기동이 실패한다. (JVM 모드/로컬 bootRun에서는 재현되지 않고 native 빌드에서만 발생)
 *
 * 대상은 flyway-core / flyway-database-oracle 11.7.2의
 * META-INF/services/org.flywaydb.core.extensibility.Plugin 에 등록된 ConfigurationExtension 구현체 전부 +
 * 직렬화 과정에서 재귀로 참조되는 모델(CleanModel -> SchemaModel)이다.
 * flyway 버전 업 시 클래스가 사라져도 registerTypeIfPresent가 조용히 건너뛴다.
 */
class FlywayNativeHints : RuntimeHintsRegistrar {
    override fun registerHints(
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
}

@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(FlywayNativeHints::class)
class FlywayNativeHintsConfig
