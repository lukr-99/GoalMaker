package com.goalmaker.app.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.about.AppInfo
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.settings.InfoRow
import com.goalmaker.app.ui.settings.RowDivider
import com.goalmaker.app.ui.theme.AppTheme

/**
 * What GoalMaker is and who made it (docs/about.md): the hero with the version, the author's card
 * with the feedback links as its centrepiece, what the app keeps and where, this build's facts and
 * the credits. Each block rises into place a beat after the one above; with reduce motion the page
 * is simply there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(info: AppInfo, onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { ScreenTitle(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = READING_WIDTH)
                    .fillMaxWidth()
                    .padding(horizontal = AppTheme.density.pagePadding.dp)
                    .padding(top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AboutRise(0) { AboutHero(info) }
                // Who made it comes straight after the opening: it is the part a reader can act on.
                AboutRise(1) { AboutAuthorCard() }
                AboutRise(2) { DataSection(info) }
                AboutRise(3) { BuildSection(info) }
                AboutRise(4) { CreditsSection() }
                AboutRise(5) { Closing() }
            }
        }
    }
}

@Composable
private fun DataSection(info: AppInfo) {
    AboutSection(stringResource(R.string.about_data_heading)) {
        if (info.localOnly) {
            AboutFact(Icons.Outlined.PhoneAndroid, stringResource(R.string.about_data_local_only))
        } else {
            AboutFact(Icons.Outlined.PhoneAndroid, stringResource(R.string.about_data_device))
            AboutFact(Icons.Outlined.CloudSync, stringResource(R.string.about_data_sync))
        }
        AboutFact(Icons.Outlined.Timer, stringResource(R.string.about_data_tally))
        if (!info.localOnly) {
            AboutFact(Icons.Outlined.Forum, stringResource(R.string.about_data_chat))
            AboutFact(Icons.Outlined.Link, stringResource(R.string.about_data_claude))
        }
        AboutFact(Icons.Outlined.Save, stringResource(R.string.about_data_backup))
    }
}

@Composable
private fun BuildSection(info: AppInfo) {
    AboutSection(stringResource(R.string.about_build_heading)) {
        InfoRow(stringResource(R.string.settings_version), info.versionName)
        RowDivider()
        InfoRow(
            stringResource(R.string.about_build_kind),
            stringResource(if (info.isDevBuild) R.string.about_dev_build else R.string.about_build_release),
        )
        RowDivider()
        InfoRow(stringResource(R.string.settings_backend), info.backend.url)
    }
}

@Composable
private fun CreditsSection() {
    AboutSection(stringResource(R.string.about_credits_heading)) {
        Text(
            stringResource(R.string.about_credits_body),
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textMuted,
        )
        AboutCredits.groups.forEach { group ->
            AboutLabel(stringResource(group.title), AppTheme.colors.accent, Modifier.padding(top = 16.dp, bottom = 2.dp))
            group.credits.forEachIndexed { index, credit ->
                if (index > 0) RowDivider()
                AboutCreditRow(credit)
            }
        }
    }
}

/** The page's last line: the trend arrow once more, small, and a thank you. */
@Composable
private fun Closing() {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TrendStroke(AppTheme.colors.accent, Modifier.size(56.dp, 36.dp), width = 3.dp, delayMillis = AppTheme.motion.emphasized * 2)
        Text(
            stringResource(R.string.about_thanks),
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// A comfortable line length on a tablet or a phone on its side.
private val READING_WIDTH = 640.dp
