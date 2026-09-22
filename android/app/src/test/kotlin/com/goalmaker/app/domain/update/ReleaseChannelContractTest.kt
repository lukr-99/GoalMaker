package com.goalmaker.app.domain.update

import com.goalmaker.app.contracts.ContractFiles
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** contracts/vectors/release-channel.json through [ReleaseChannelAddress]. */
class ReleaseChannelContractTest {
    private val vectors = ContractFiles.load("vectors/release-channel.json")

    @Test
    fun `every channel has the expected addresses`() {
        val channels = vectors.getValue("channels").jsonArray
        assertTrue(channels.isNotEmpty())
        for (element in channels) {
            val case = element.jsonObject
            val address = ReleaseChannelAddress(case.getValue("updateUrl").jsonPrimitive.content)
            assertEquals(case.getValue("manifest").jsonPrimitive.content, address.manifest)
            assertEquals(case.getValue("signature").jsonPrimitive.content, address.signature)
            assertEquals(case.getValue("releasesPage").jsonPrimitive.content, address.releasesPage)
        }
    }

    @Test
    fun `every artifact path maps to its address or to none`() {
        val address = ReleaseChannelAddress(vectors.getValue("artifactUpdateUrl").jsonPrimitive.content)
        val artifacts = vectors.getValue("artifacts").jsonArray
        assertTrue(artifacts.size >= 10)
        for (element in artifacts) {
            val case = element.jsonObject
            val path = case.getValue("path").jsonPrimitive.content
            val expected = case.getValue("url").takeUnless { it is JsonNull }?.jsonPrimitive?.content
            assertEquals(path, expected, address.artifact(path))
        }
    }
}
