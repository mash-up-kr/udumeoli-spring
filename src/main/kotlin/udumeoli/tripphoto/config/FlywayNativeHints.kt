package udumeoli.tripphoto.config

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportRuntimeHints

class FlywayNativeHints : RuntimeHintsRegistrar {
    override fun registerHints(
        hints: RuntimeHints,
        classLoader: ClassLoader?,
    ) {
        val types =
            listOf(
                "org.flywaydb.database.oracle.OracleConfigurationExtension",
                "org.flywaydb.core.internal.command.clean.CleanModeConfigurationExtension",
                "org.flywaydb.core.internal.configuration.extensions.DeployScriptFilenameConfigurationExtension",
                "org.flywaydb.core.internal.configuration.extensions.PrepareScriptFilenameConfigurationExtension",
                "org.flywaydb.core.api.migration.baseline.BaselineMigrationConfigurationExtension",
                "org.flywaydb.core.internal.proprietaryStubs.LicensingConfigurationExtensionStub",
                "org.flywaydb.core.internal.proprietaryStubs.PATTokenConfigurationExtensionStub",
                "org.flywaydb.core.internal.publishing.PublishingConfigurationExtension",
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
