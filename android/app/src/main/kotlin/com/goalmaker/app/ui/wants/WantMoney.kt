package com.goalmaker.app.ui.wants

import java.text.NumberFormat
import java.util.Locale

/** A price as the Wants place writes it: grouped digits, cents only when there are any, the currency code. */
object WantMoney {
    fun format(amount: Double, currency: String, locale: Locale): String {
        val number = NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
            minimumFractionDigits = maximumFractionDigits
        }
        return "${number.format(amount)} $currency"
    }

    /** A price typed into a field, with a comma or a point; null for nothing or something that isn't one. */
    fun parse(text: String): Double? = text.trim().replace(" ", "").replace(' '.toString(), "").replace(',', '.')
        .takeIf(String::isNotEmpty)?.toDoubleOrNull()?.takeIf { it >= 0 }
}
