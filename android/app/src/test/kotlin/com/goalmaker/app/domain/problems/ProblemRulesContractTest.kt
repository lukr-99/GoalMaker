package com.goalmaker.app.domain.problems

import com.goalmaker.app.contracts.ContractFiles
import java.time.Instant
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs contracts/vectors/problems.json, which the Windows app passes too. */
class ProblemRulesContractTest {
    private val vectors = ContractFiles.load("vectors/problems.json")

    private fun JsonObject.text(name: String) = this[name]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content

    @Test
    fun `both apps know the same kinds`() {
        val kinds = vectors.getValue("kinds").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(kinds.sorted(), ProblemRules.KINDS.sorted())
    }

    @Test
    fun `every run leaves what the contract says`() {
        vectors.getValue("runs").jsonArray.map { it.jsonObject }.forEach { run ->
            val name = run.text("name")
            var problems = emptyList<Problem>()
            run.getValue("steps").jsonArray.map { it.jsonObject }.forEach { step ->
                problems = when {
                    step["report"] != null -> ProblemRules.report(
                        problems,
                        step.text("report")!!,
                        Instant.parse(step.text("at")!!),
                        step.text("detail"),
                    )
                    step["clear"] != null -> ProblemRules.clear(problems, step.text("clear")!!)
                    else -> ProblemRules.read(problems)
                }
            }

            val expect = run.getValue("expect").jsonObject
            val wanted = expect.getValue("problems").jsonArray.map { it.jsonObject }.map {
                Problem(it.text("kind")!!, Instant.parse(it.text("at")!!), it.text("detail"), it.getValue("unread").jsonPrimitive.boolean)
            }
            assertEquals(name, wanted, problems)
            assertEquals(name, expect.getValue("marked").jsonPrimitive.boolean, ProblemRules.isMarked(problems))
        }
    }
}
