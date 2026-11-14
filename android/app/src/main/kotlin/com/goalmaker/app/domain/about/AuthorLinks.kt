package com.goalmaker.app.domain.about

/**
 * Who made GoalMaker and where to reach them, shown on the About page (docs/about.md). The one place
 * these are written in the phone app; contracts/vectors/about.json holds the same values for the PC.
 * The links open in the browser, so the app itself sends nothing anywhere.
 */
object AuthorLinks {
    const val NAME: String = "Lukáš Krejčí"

    const val INSTAGRAM_HANDLE: String = "lukas9krejci"
    const val INSTAGRAM_URL: String = "https://www.instagram.com/$INSTAGRAM_HANDLE/"

    const val GITHUB_HANDLE: String = "lukr-99"
    const val GITHUB_URL: String = "https://github.com/$GITHUB_HANDLE"

    /** GoalMaker's own repository, where bugs and ideas go as issues. */
    const val REPOSITORY: String = "$GITHUB_HANDLE/GoalMaker"
    const val ISSUES_URL: String = "https://github.com/$REPOSITORY/issues"

    val instagram = AuthorLink("instagram", INSTAGRAM_URL, "@$INSTAGRAM_HANDLE")
    val github = AuthorLink("github", GITHUB_URL, GITHUB_HANDLE)
    val issues = AuthorLink("issues", ISSUES_URL, REPOSITORY)

    /** In the order the author card shows them. */
    val all: List<AuthorLink> = listOf(instagram, github, issues)
}
