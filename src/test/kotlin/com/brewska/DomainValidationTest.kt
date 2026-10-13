package com.brewska

import com.brewska.catalog.*
import com.brewska.collection.*
import com.brewska.domain.InvalidInput
import org.junit.jupiter.api.Test
import kotlin.test.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class DomainValidationTest {
    private val roaster = UUID.randomUUID()
    @Test fun `optional catalog values remain unknown and text trims`() {
        assertEquals(LotInput(roaster), LotInput(roaster).normalized())
        assertEquals("Coffee", LotInput(roaster, name = " Coffee ").normalized().name)
    }
    @Test fun `blank and oversized text are invalid even when optional`() {
        listOf(" ", "x".repeat(201)).forEach {
            assertFailsWith<InvalidInput> { LotInput(roaster, name = it).normalized() }
        }
        assertFailsWith<InvalidInput> { LotInput(roaster, description = "x".repeat(4001)).normalized() }
        assertFailsWith<InvalidInput> { LotInput(roaster, processingMethod = "").normalized() }
        assertFailsWith<InvalidInput> { LotInput(roaster, originComponents = listOf(OriginComponent(country = " "))).normalized() }
    }
    @Test fun `all lists have independent limits and valid labels`() {
        assertFailsWith<InvalidInput> { LotInput(roaster, varieties = List(51) { "Bourbon" }).normalized() }
        assertFailsWith<InvalidInput> { LotInput(roaster, declaredDescriptors = listOf("")).normalized() }
        assertFailsWith<InvalidInput> { LotInput(roaster, originComponents = List(51) { OriginComponent() }).normalized() }
        assertFailsWith<InvalidInput> { LotInput(roaster, originComponents = listOf(OriginComponent(varieties = List(51) { "x" }))).normalized() }
        assertEquals(50, LotInput(roaster, varieties = List(50) { "Bourbon" }).normalized().varieties.size)
    }
    @Test fun `price is exact bounded and normalized`() {
        assertEquals(BigDecimal("1.00"), Price(BigDecimal("1.0"), "EUR").normalized().amount)
        assertEquals(BigDecimal("0.00"), Price(BigDecimal.ZERO, "EUR").normalized().amount)
        assertEquals(BigDecimal("9999999999.99"), Price(BigDecimal("9999999999.99"), "USD").normalized().amount)
        listOf("-0.01", "0.001", "10000000000", "1.000").forEach {
            assertFailsWith<InvalidInput> { Price(BigDecimal(it), "EUR").normalized() }
        }
        listOf("eur", "EU", "EURO", "123").forEach {
            assertFailsWith<InvalidInput> { Price(BigDecimal.ONE, it).normalized() }
        }
    }
    @Test fun `weight must be positive and preorder dates are accepted`() {
        val day = LocalDate.of(2026, 10, 1)
        assertFailsWith<InvalidInput> { PurchaseInput(day, weightGrams = 0).normalized() }
        assertFailsWith<InvalidInput> { PurchaseInput(day, weightGrams = -1).normalized() }
        assertEquals(day.plusDays(2), PurchaseInput(day, day.plusDays(2), Int.MAX_VALUE).normalized().roastedOn)
    }
    @Test fun `NUL is rejected consistently in plain and nested text`() {
        val nul = "a" + 0.toChar() + "b"
        listOf(
            LotInput(roaster, name = nul), LotInput(roaster, description = nul),
            LotInput(roaster, varieties = listOf(nul)),
            LotInput(roaster, originComponents = listOf(OriginComponent(country = nul))),
            LotInput(roaster, originComponents = listOf(OriginComponent(varieties = listOf(nul))))
        ).forEach { assertFailsWith<InvalidInput> { it.normalized() } }
    }

}
