package udumeoli.tripphoto.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions
import org.springframework.data.jdbc.core.dialect.JdbcOracleDialect
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration
import org.springframework.data.relational.RelationalManagedTypes
import org.springframework.data.relational.core.dialect.Dialect
import org.springframework.data.relational.core.mapping.NamingStrategy
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations
import java.util.Optional

@Configuration
class JdbcConfig : AbstractJdbcConfiguration() {
    override fun jdbcMappingContext(
        namingStrategy: Optional<NamingStrategy>,
        customConversions: JdbcCustomConversions,
        jdbcManagedTypes: RelationalManagedTypes,
    ): JdbcMappingContext {
        val context = super.jdbcMappingContext(namingStrategy, customConversions, jdbcManagedTypes)
        context.setForceQuote(false)
        return context
    }

    override fun jdbcDialect(operations: NamedParameterJdbcOperations): Dialect = JdbcOracleDialect.INSTANCE
}
