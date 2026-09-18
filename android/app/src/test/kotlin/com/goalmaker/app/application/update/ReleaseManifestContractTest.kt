package com.goalmaker.app.application.update

import com.goalmaker.app.contracts.ContractFiles
import com.goalmaker.app.data.update.EcdsaSignatureVerifier
import com.goalmaker.app.domain.update.ManifestCheck
import java.util.Base64
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** contracts/vectors/release-manifest.json through the real verifier and parser. */
class ReleaseManifestContractTest {
    private val vectors = ContractFiles.load("vectors/release-manifest.json")
    private val verifier = ReleaseVerifier(EcdsaSignatureVerifier(vectors.getValue("trustedPublicKey").jsonPrimitive.content))

    @Test
    fun `every case has the expected outcome`() {
        val cases = vectors.getValue("cases").jsonArray
        assertTrue(cases.size >= 10)
        for (element in cases) {
            val case = element.jsonObject
            val name = case.getValue("name").jsonPrimitive.content
            val snapshot = ChannelSnapshot(
                manifestBytes = Base64.getDecoder().decode(case.getValue("manifestBase64").jsonPrimitive.content),
                signatureBase64 = case.getValue("signatureBase64").jsonPrimitive.content,
            )
            val result = verifier.check(snapshot)
            when (val outcome = case.getValue("outcome").jsonPrimitive.content) {
                "bad-signature" -> assertEquals(name, ManifestCheck.BadSignature, result)
                "bad-manifest" -> assertTrue("$name: expected bad-manifest, got $result", result is ManifestCheck.BadManifest)
                "valid" -> {
                    val manifest = (result as? ManifestCheck.Valid)?.manifest ?: return fail("$name: expected valid, got $result")
                    assertEquals(name, case.getValue("version").jsonPrimitive.content, manifest.version.toString())
                    val code = case.getValue("androidVersionCode")
                    assertEquals(name, if (code is JsonNull) null else code.jsonPrimitive.intOrNull, manifest.androidVersionCode)
                    val expected = case.getValue("artifacts").jsonArray.map { it.jsonObject }
                    assertEquals(name, expected.size, manifest.artifacts.size)
                    expected.zip(manifest.artifacts).forEach { (want, got) ->
                        assertEquals(name, want.getValue("platform").jsonPrimitive.content, got.platform.wireName)
                        assertEquals(name, want.getValue("path").jsonPrimitive.content, got.path)
                        assertEquals(name, want.getValue("size").jsonPrimitive.long, got.size)
                        assertEquals(name, want.getValue("sha256").jsonPrimitive.content, got.sha256)
                    }
                }
                else -> fail("$name: unknown outcome $outcome")
            }
        }
    }

    @Test
    fun `the vector file declares its schema`() {
        assertEquals(1, vectors.getValue("schema").jsonPrimitive.int)
    }
}
