package com.goalmaker.app.domain.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignInInputTest {
    @Test
    fun `emails are trimmed and loosely validated`() {
        assertEquals("me@example.com", EmailAddress.parse("  me@example.com ")?.value)
        assertNull(EmailAddress.parse("me@example"))
        assertNull(EmailAddress.parse("not an email"))
        assertNull(EmailAddress.parse(""))
    }

    @Test
    fun `codes are six ascii digits, spaces allowed`() {
        assertEquals("123456", SignInCode.parse("123 456")?.value)
        assertNull(SignInCode.parse("12345"))
        assertNull(SignInCode.parse("1234567"))
        assertNull(SignInCode.parse("12a456"))
        assertNull(SignInCode.parse("١٢٣٤٥٦"))
    }
}
