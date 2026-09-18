package com.goalmaker.app.domain.composer

/** What a recognized part of a composer line is (docs/composer.md). */
enum class SpanKind {
    DATE,
    TIME,
    REPEAT,
    TAG,
    AREA,
    PROJECT,
    PRIORITY,
    IDEA,
    COMMAND,
}
