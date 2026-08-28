package com.eafc26.discordstats.ea.mapping

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UnknownFieldCaptureTest {

    private val factory = JsonNodeFactory.instance
    private val mapper = ObjectMapper()

    @Test
    fun `empty map produces EMPTY status`() {
        val result = UnknownFieldCapture.capture("player", emptyMap())
        assertThat(result.fields).isNotNull.isEmpty()
        assertThat(result.scope).isEqualTo("player")
    }

    @Test
    fun `captures string field preserving type`() {
        val fields = mapOf("newField" to factory.textNode("hello"))
        val result = UnknownFieldCapture.capture("player", fields)
        assertThat(result.fields).hasSize(1)
        val f = result.fields!!.first()
        assertThat(f.name).isEqualTo("newField")
        assertThat(f.jsonType).isEqualTo("string")
        assertThat(f.value).isEqualTo("\"hello\"")
        assertThat(f.truncated).isFalse()
    }

    @Test
    fun `captures number field preserving type`() {
        val fields = mapOf("score" to factory.numberNode(42))
        val result = UnknownFieldCapture.capture("player", fields)
        val f = result.fields!!.first()
        assertThat(f.jsonType).isEqualTo("number")
        assertThat(f.value).isEqualTo("42")
    }

    @Test
    fun `captures boolean field preserving type`() {
        val fields = mapOf("active" to factory.booleanNode(true))
        val result = UnknownFieldCapture.capture("player", fields)
        val f = result.fields!!.first()
        assertThat(f.jsonType).isEqualTo("boolean")
        assertThat(f.value).isEqualTo("true")
    }

    @Test
    fun `captures object field preserving type`() {
        val obj = mapper.readTree("""{"nested":"value"}""")
        val fields = mapOf("extra" to obj)
        val result = UnknownFieldCapture.capture("player", fields)
        val f = result.fields!!.first()
        assertThat(f.jsonType).isEqualTo("object")
        assertThat(f.value).contains("nested")
    }

    @Test
    fun `captures array field preserving type`() {
        val arr = mapper.readTree("""[1,2,3]""")
        val fields = mapOf("list" to arr)
        val result = UnknownFieldCapture.capture("player", fields)
        val f = result.fields!!.first()
        assertThat(f.jsonType).isEqualTo("array")
    }

    @Test
    fun `captures null field preserving type`() {
        val fields = mapOf("empty" to factory.nullNode())
        val result = UnknownFieldCapture.capture("player", fields)
        val f = result.fields!!.first()
        assertThat(f.jsonType).isEqualTo("null")
    }

    @Test
    fun `filters sensitive field names`() {
        val fields = mapOf(
            "authToken" to factory.textNode("secret123"),
            "password" to factory.textNode("pass"),
            "sessionId" to factory.textNode("abc"),
            "safeField" to factory.numberNode(1),
        )
        val result = UnknownFieldCapture.capture("player", fields)
        assertThat(result.fields).hasSize(1)
        assertThat(result.fields!!.first().name).isEqualTo("safeField")
    }

    @Test
    fun `truncates large values`() {
        val bigValue = "x".repeat(5000)
        val fields = mapOf("big" to factory.textNode(bigValue))
        val result = UnknownFieldCapture.capture("player", fields)
        val f = result.fields!!.first()
        assertThat(f.truncated).isTrue()
        assertThat(f.originalSize).isGreaterThan(4096)
        assertThat(f.value.length).isLessThanOrEqualTo(4096 + 2) // quotes
    }

    @Test
    fun `limits total number of fields to 50`() {
        val fields = (1..60).associate { "field_$it" to factory.numberNode(it) }
        val result = UnknownFieldCapture.capture("player", fields)
        assertThat(result.fields!!.size).isLessThanOrEqualTo(50)
    }

    @Test
    fun `scope is preserved in output`() {
        val result = UnknownFieldCapture.capture("match", mapOf("x" to factory.numberNode(1)))
        assertThat(result.scope).isEqualTo("match")
    }

    @Test
    fun `sensitive pattern matching is case insensitive`() {
        val fields = mapOf(
            "AuthToken" to factory.textNode("v1"),
            "JWT_CLAIM" to factory.textNode("v2"),
            "normalField" to factory.numberNode(1),
        )
        val result = UnknownFieldCapture.capture("player", fields)
        assertThat(result.fields).hasSize(1)
        assertThat(result.fields!!.first().name).isEqualTo("normalField")
    }

    @Test
    fun `multiple fields are all captured in order`() {
        val fields = linkedMapOf(
            "alpha" to factory.textNode("a"),
            "beta" to factory.numberNode(2),
            "gamma" to factory.booleanNode(false),
        )
        val result = UnknownFieldCapture.capture("player", fields)
        assertThat(result.fields).hasSize(3)
        assertThat(result.fields!!.map { it.name }).containsExactly("alpha", "beta", "gamma")
    }
}
