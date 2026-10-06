package com.goalmaker.app.ui.components

import android.app.Application
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.goalmaker.app.application.planning.ProjectItem
import com.goalmaker.app.application.planning.ProjectRules
import com.goalmaker.app.ui.TestTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A project item's chip shows its id, GM-12, and a screen reader hears it with the project (docs/projects.md, "Item ids"). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProjectChipAccessibilityTest {
    @get:Rule
    val rule = createComposeRule()

    private val project = ProjectItem(id = "p1", name = "GoalMaker", itemKey = "GM")

    @Test
    fun `a numbered item's chip shows and reads its id`() {
        rule.setContent { TestTheme { ProjectChip(project, ProjectRules.BUG, onOpen = null, itemId = "GM-12") } }

        rule.onNodeWithContentDescription("Bug in GoalMaker, GM-12").assertIsDisplayed()
    }

    @Test
    fun `an item the server hasn't numbered yet shows no id`() {
        rule.setContent { TestTheme { ProjectChip(project, ProjectRules.TASK, onOpen = null) } }

        rule.onNodeWithContentDescription("Project GoalMaker").assertContentDescriptionEquals("Project GoalMaker")
    }
}
