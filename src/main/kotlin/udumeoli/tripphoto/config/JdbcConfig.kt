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
    /**
     * 식별자 따옴표(quoting) 비활성화.
     * 켜져 있으면 @Table("image")가 "image"(소문자, 대소문자 구분)로 쿼리되는데,
     * Oracle/H2는 스키마를 대문자(IMAGE)로 저장하므로 테이블을 찾지 못한다.
     */
    override fun jdbcMappingContext(
        namingStrategy: Optional<NamingStrategy>,
        customConversions: JdbcCustomConversions,
        jdbcManagedTypes: RelationalManagedTypes,
    ): JdbcMappingContext {
        val context = super.jdbcMappingContext(namingStrategy, customConversions, jdbcManagedTypes)
        context.setForceQuote(false)
        return context
    }

    /**
     * Dialect를 Oracle로 고정한다.
     * local 프로파일은 H2를 MODE=Oracle로 띄우는데, 자동 감지는 H2Dialect(LIMIT 문법)를 골라
     * Oracle 모드 H2에서 문법 오류가 난다. 표준 FETCH FIRST를 쓰는 OracleDialect는 양쪽 모두에서 동작한다.
     */
    override fun jdbcDialect(operations: NamedParameterJdbcOperations): Dialect = JdbcOracleDialect.INSTANCE
}
