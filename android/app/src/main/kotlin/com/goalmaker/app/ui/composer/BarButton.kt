package com.goalmaker.app.ui.composer

/**
 * What the round button at the end of the bottom bar is (docs/composer.md, "The bottom bar on every
 * list"): a plus that opens the page's full form while the line is empty, the send arrow once
 * something is typed. In the quick chat it is always the send arrow.
 */
enum class BarButton {
    PLUS,
    SEND,
    ;

    companion object {
        /** The button for [line]; [hasForm] is false where the bar only sends (the quick-add box, Plan tomorrow). */
        fun of(line: String, chatting: Boolean, hasForm: Boolean): BarButton =
            if (hasForm && !chatting && line.isBlank()) PLUS else SEND
    }
}
