package com.goalmaker.app.domain.notes

import com.goalmaker.app.contracts.ContractFiles
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs contracts/vectors/markdown.json, which the Windows app passes too. */
class LightMarkdownContractTest {
    private val vectors = ContractFiles.load("vectors/markdown.json")

    @Test
    fun `every note`() {
        vectors.getValue("cases").jsonArray.map { it.jsonObject }.forEach { case ->
            val expected = case.getValue("blocks").jsonArray.map { it.jsonObject }.map { block ->
                MarkdownBlock(
                    bullet = block.getValue("bullet").jsonPrimitive.boolean,
                    spans = block.getValue("spans").jsonArray.map { it.jsonObject }.map { span ->
                        MarkdownSpan(
                            text = span.getValue("text").jsonPrimitive.content,
                            bold = span["bold"]?.jsonPrimitive?.boolean ?: false,
                            italic = span["italic"]?.jsonPrimitive?.boolean ?: false,
                            link = span["link"]?.jsonPrimitive?.content,
                        )
                    },
                )
            }
            assertEquals(case.getValue("name").jsonPrimitive.content, expected, LightMarkdown.parse(case.getValue("text").jsonPrimitive.content))
        }
    }
}
