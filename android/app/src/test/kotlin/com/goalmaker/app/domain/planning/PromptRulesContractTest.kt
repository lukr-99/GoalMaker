package com.goalmaker.app.domain.planning

import com.goalmaker.app.contracts.ContractFiles
import java.io.File
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rotation and the reactive prompts of contracts/vectors/reviews.json, over the shipped library. */
class PromptRulesContractTest {
    private val vectors = ContractFiles.load("vectors/reviews.json")
    private val library = PromptLibrary.parse(
        File(System.getProperty("goalmaker.contracts")!!).resolve("content/prompts.json").readText(),
    )

    private fun JsonObject.text(name: String) = this[name]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content
    private fun JsonObject.cases(name: String) = getValue(name).jsonArray.map { it.jsonObject }
    private fun JsonObject.ids(name: String) = getValue(name).jsonArray.map { it.jsonPrimitive.content }

    @Test
    fun `the library has every prompt the vectors name`() {
        val named = vectors.cases("rotation").flatMap { it.ids("expect") } +
            vectors.cases("promptTexts").mapNotNull { it.text("prompt") } +
            vectors.cases("reactive").flatMap { case -> case.getValue("expect").jsonArray.map { it.jsonObject.text("prompt")!! } }
        named.forEach { id -> assertTrue("$id is missing from prompts.json", library[id] != null) }
        assertTrue("the library should hold about a hundred prompts", library.prompts.size >= 100)
    }

    @Test
    fun `every rotation`() {
        vectors.cases("rotation").forEach { case ->
            val kind = case.text("kind")!!
            val shown = case.ids("shown")
            val chosen = case.text("category")?.let { category ->
                listOfNotNull(PromptRules.next(library, kind, category, shown))
            } ?: PromptRules.rotation(library, kind, shown, case.getValue("count").jsonPrimitive.int)
            assertEquals(case.text("name"), case.ids("expect"), chosen.map(ReviewPrompt::id))
        }
    }

    @Test
    fun `every reactive prompt`() {
        vectors.cases("reactive").forEach { case ->
            val facts = case.getValue("facts").jsonObject
            val questions = PromptRules.reactive(
                library,
                case.text("kind")!!,
                PeriodFacts(
                    doneTasks = facts.getValue("doneTasks").jsonPrimitive.int,
                    averageDone = facts.getValue("averageDone").jsonPrimitive.double,
                    goals = facts.getValue("goals").jsonArray.map { element ->
                        val goal = element.jsonObject
                        PeriodFacts.GoalFact(
                            goal.text("title")!!,
                            goal.getValue("fraction").jsonPrimitive.double,
                            goal.getValue("expected").jsonPrimitive.double,
                        )
                    },
                    habits = facts.getValue("habits").jsonArray.map { element ->
                        val habit = element.jsonObject
                        PeriodFacts.HabitFact(
                            habit.text("name")!!,
                            habit.getValue("missed").jsonPrimitive.int,
                            habit.getValue("periods").jsonPrimitive.int,
                            habit.getValue("streak").jsonPrimitive.int,
                        )
                    },
                    tasks = facts.getValue("tasks").jsonArray.map { element ->
                        val task = element.jsonObject
                        PeriodFacts.TaskFact(task.text("title")!!, task.getValue("moves").jsonPrimitive.int)
                    },
                ),
            )
            val expected = case.getValue("expect").jsonArray.map { element ->
                val question = element.jsonObject
                question.text("prompt") to question.text("subject")
            }
            assertEquals(case.text("name"), expected, questions.map { it.promptId to it.subject })
        }
    }

    @Test
    fun `every prompt text`() {
        vectors.cases("promptTexts").forEach { case ->
            val prompt = library[case.text("prompt")!!]!!
            assertEquals(
                case.text("prompt"),
                case.text("expect"),
                PromptRules.text(prompt, case.text("kind")!!, case.text("subject")),
            )
        }
    }
}
