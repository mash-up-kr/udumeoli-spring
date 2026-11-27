package udumeoli.tripphoto.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.TypeReference
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates

class KotlinCollectionsNativeHintsTest {
    @Test
    fun `Kotlin 빈 컬렉션 싱글톤과 메타데이터 리소스를 네이티브에 등록한다`() {
        val hints = RuntimeHints()
        ApplicationNativeHints().registerHints(hints, javaClass.classLoader)

        val reflection = RuntimeHintsPredicates.reflection()
        assertThat(reflection.onType(TypeReference.of("kotlin.collections.EmptyList"))).accepts(hints)
        assertThat(reflection.onType(TypeReference.of("kotlin.collections.EmptySet"))).accepts(hints)
        assertThat(reflection.onType(TypeReference.of("kotlin.collections.EmptyMap"))).accepts(hints)
        assertThat(reflection.onType(TypeReference.of("kotlin.Metadata"))).accepts(hints)

        val resource = RuntimeHintsPredicates.resource()
        assertThat(resource.forResource("META-INF/kotlin-stdlib.kotlin_module")).accepts(hints)
        assertThat(resource.forResource("kotlin/collections/collections.kotlin_builtins")).accepts(hints)
    }
}
