package udumeoli.tripphoto.config

import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.StringValue
import graphql.language.Value
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLScalarType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.execution.RuntimeWiringConfigurer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * schema.graphqls의 커스텀 스칼라(Date, DateTime) 런타임 바인딩.
 * graphql-java-extended-scalars 의존성 없이 직접 정의해 Native Image 호환성을 확보한다.
 */
@Configuration
class GraphQlConfig {
    @Bean
    fun runtimeWiringConfigurer(): RuntimeWiringConfigurer =
        RuntimeWiringConfigurer { builder ->
            builder.scalar(DATE).scalar(DATE_TIME)
        }

    companion object {
        val DATE: GraphQLScalarType =
            GraphQLScalarType
                .newScalar()
                .name("Date")
                .description("ISO-8601 날짜 (예: 2026-07-09)")
                .coercing(LocalDateCoercing)
                .build()

        val DATE_TIME: GraphQLScalarType =
            GraphQLScalarType
                .newScalar()
                .name("DateTime")
                .description("ISO-8601 날짜+시간 (예: 2026-07-09T12:34:56)")
                .coercing(LocalDateTimeCoercing)
                .build()
    }
}

private object LocalDateCoercing : Coercing<LocalDate, String> {
    override fun serialize(
        dataFetcherResult: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): String =
        (dataFetcherResult as? LocalDate)?.toString()
            ?: throw CoercingSerializeException(
                localized(locale, "Date로 직렬화할 수 없는 값", "Cannot serialize value as Date", dataFetcherResult),
            )

    override fun parseValue(
        input: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): LocalDate =
        try {
            LocalDate.parse(input.toString())
        } catch (e: DateTimeParseException) {
            throw CoercingParseValueException(
                localized(locale, "Date 형식이 아닙니다 (yyyy-MM-dd)", "Invalid Date format (yyyy-MM-dd)", input),
                e,
            )
        }

    override fun parseLiteral(
        input: Value<*>,
        variables: CoercedVariables,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): LocalDate {
        val raw =
            (input as? StringValue)?.value
                ?: throw CoercingParseLiteralException(
                    localized(locale, "Date는 문자열 리터럴이어야 합니다", "Date must be a string literal", input),
                )
        return try {
            LocalDate.parse(raw)
        } catch (e: DateTimeParseException) {
            throw CoercingParseLiteralException(
                localized(locale, "Date 형식이 아닙니다 (yyyy-MM-dd)", "Invalid Date format (yyyy-MM-dd)", raw),
                e,
            )
        }
    }
}

private object LocalDateTimeCoercing : Coercing<LocalDateTime, String> {
    override fun serialize(
        dataFetcherResult: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): String =
        (dataFetcherResult as? LocalDateTime)?.toString()
            ?: throw CoercingSerializeException(
                localized(locale, "DateTime으로 직렬화할 수 없는 값", "Cannot serialize value as DateTime", dataFetcherResult),
            )

    override fun parseValue(
        input: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): LocalDateTime =
        try {
            LocalDateTime.parse(input.toString())
        } catch (e: DateTimeParseException) {
            throw CoercingParseValueException(
                localized(locale, "DateTime 형식이 아닙니다 (ISO-8601)", "Invalid DateTime format (ISO-8601)", input),
                e,
            )
        }

    override fun parseLiteral(
        input: Value<*>,
        variables: CoercedVariables,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): LocalDateTime {
        val raw =
            (input as? StringValue)?.value
                ?: throw CoercingParseLiteralException(
                    localized(locale, "DateTime은 문자열 리터럴이어야 합니다", "DateTime must be a string literal", input),
                )
        return try {
            LocalDateTime.parse(raw)
        } catch (e: DateTimeParseException) {
            throw CoercingParseLiteralException(
                localized(locale, "DateTime 형식이 아닙니다 (ISO-8601)", "Invalid DateTime format (ISO-8601)", raw),
                e,
            )
        }
    }
}

private fun localized(
    locale: Locale,
    korean: String,
    english: String,
    detail: Any,
): String = "${if (locale.language == Locale.ENGLISH.language) english else korean}: $detail"
