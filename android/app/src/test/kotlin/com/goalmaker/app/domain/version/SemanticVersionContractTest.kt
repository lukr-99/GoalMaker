package com.goalmaker.app.domain.version

import com.goalmaker.app.contracts.ContractFiles
import com.goalmaker.app.domain.update.UpdatePolicy
import kotlin.math.sign
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** contracts/vectors/semantic-version.json, the same file the Windows tests read. */
class SemanticVersionContractTest {
    private val vectors = ContractFiles.load("vectors/semantic-version.json")

    @Test
    fun `parses exactly the valid versions`() {
        val cases = vectors.getValue("parse").jsonArray
        assertTrue(cases.isNotEmpty())
        for (element in cases) {
            val case = element.jsonObject
            val input = case.getValue("input").jsonPrimitive.content
            val parsed = SemanticVersion.parse(input)
            if (!case.getValue("valid").jsonPrimitive.boolean) {
                assertNull("'$input' must be rejected", parsed)
                continue
            }
            assertNotNull("'$input' must parse", parsed)
            parsed!!
            assertEquals(input, case.getValue("major").jsonPrimitive.long, parsed.major)
            assertEquals(input, case.getValue("minor").jsonPrimitive.long, parsed.minor)
            assertEquals(input, case.getValue("patch").jsonPrimitive.long, parsed.patch)
            assertEquals(input, case.getValue("prerelease").jsonArray.map { it.jsonPrimitive.content }, parsed.prerelease)
        }
    }

    @Test
    fun `orders versions by semantic precedence`() {
        for (element in vectors.getValue("compare").jsonArray) {
            val case = element.jsonObject
            val a = SemanticVersion.parse(case.getValue("a").jsonPrimitive.content)!!
            val b = SemanticVersion.parse(case.getValue("b").jsonPrimitive.content)!!
            val expected = case.getValue("result").jsonPrimitive.int
            assertEquals("$a vs $b", expected, a.compareTo(b).sign)
            assertEquals("$b vs $a", -expected, b.compareTo(a).sign)
        }
    }

    @Test
    fun `offers only newer stable releases to release builds`() {
        for (element in vectors.getValue("offerUpdate").jsonArray) {
            val case = element.jsonObject
            val installed = case.getValue("installed").jsonPrimitive.content
            val available = case.getValue("available").jsonPrimitive.content
            assertEquals(
                "$installed -> $available",
                case.getValue("offer").jsonPrimitive.boolean,
                UpdatePolicy.shouldOffer(installed, available),
            )
        }
    }
}
