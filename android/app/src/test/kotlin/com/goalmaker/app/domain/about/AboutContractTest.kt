package com.goalmaker.app.domain.about

import com.goalmaker.app.contracts.ContractFiles
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs contracts/vectors/about.json, which the Windows app passes too. */
class AboutContractTest {
    private val vectors = ContractFiles.load("vectors/about.json")

    @Test
    fun `the author is the contract's`() {
        assertEquals(vectors.getValue("author").jsonPrimitive.content, AuthorLinks.NAME)
    }

    @Test
    fun `the links are the contract's, in its order`() {
        val expected = vectors.getValue("links").jsonArray.map { it.jsonObject }.map { link ->
            AuthorLink(
                id = link.getValue("id").jsonPrimitive.content,
                url = link.getValue("url").jsonPrimitive.content,
                handle = link.getValue("handle").jsonPrimitive.content,
            )
        }
        assertEquals(expected, AuthorLinks.all)
    }

    @Test
    fun `every link is https`() {
        AuthorLinks.all.forEach { assertTrue(it.url, it.url.startsWith("https://")) }
    }

    @Test
    fun `every monogram case`() {
        vectors.getValue("monogram").jsonArray.map { it.jsonObject }.forEach { case ->
            val name = case.getValue("name").jsonPrimitive.content
            assertEquals(name, case.getValue("expect").jsonPrimitive.content, Monogram.of(name))
        }
    }
}
