package com.goalmaker.app.ui.lifegoals

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.LifeGoalDraft
import com.goalmaker.app.application.planning.LifeGoalItem
import com.goalmaker.app.application.planning.LifeGoalPicture
import com.goalmaker.app.application.planning.LifeGoalRules
import com.goalmaker.app.ui.components.ChoiceChip
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.theme.AppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Adds a life goal or edits one (docs/life-goals.md): the title and the why are required; the by date
 * is In 5, 10 or 20 years, a picked day or none. Pictures come from the system photo picker, are
 * shrunk as they are picked, and are kept, with the ones removed, only when the sheet saves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LifeGoalSheet(
    viewModel: LifeGoalsViewModel,
    initial: LifeGoalItem?,
    pictures: List<LifeGoalPicture>,
    pictureVersion: Int,
    onDismiss: () -> Unit,
    onPictureFailed: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val resolver = LocalContext.current.contentResolver
    val today = remember { viewModel.today() }
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var why by rememberSaveable { mutableStateOf(initial?.why.orEmpty()) }
    var by by rememberSaveable { mutableStateOf(initial?.by?.toString()) }
    var picking by remember { mutableStateOf(false) }
    val added = remember { mutableStateListOf<ShrunkPicture>() }
    val removed = remember { mutableStateListOf<String>() }
    val canSave = title.isNotBlank() && why.isNotBlank()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) { uris ->
        scope.launch {
            uris.forEach { uri ->
                val shrunk = withContext(Dispatchers.IO) { PictureShrinker.shrink(resolver, uri) }
                if (shrunk != null) added += shrunk else onPictureFailed()
            }
        }
    }

    fun save() {
        if (!canSave) return
        val draft = LifeGoalDraft(title = title, why = why, by = by?.let(LocalDate::parse), areaId = initial?.areaId)
        scope.launch {
            if (viewModel.save(initial, draft, added.toList(), removed.toSet()) != null) sheet.hide()
            onDismiss()
        }
    }

    if (picking) {
        ByDayPicker(today, by?.let(LocalDate::parse), onPick = { by = it.toString(); picking = false }, onDismiss = { picking = false })
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.density.pagePadding.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
        ) {
            ScreenTitle(stringResource(if (initial == null) R.string.life_goals_add else R.string.life_goals_edit))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(200) },
                label = { Text(stringResource(R.string.life_goals_title_field)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = why,
                onValueChange = { why = it.take(2000) },
                label = { Text(stringResource(R.string.life_goals_why_field)) },
                supportingText = { Text(stringResource(R.string.life_goals_why_hint)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Label(stringResource(R.string.life_goals_by))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                ChoiceChip(selected = by == null, onClick = { by = null }, label = stringResource(R.string.life_goals_by_none))
                LifeGoalRules.BY_YEARS.forEach { years ->
                    val day = today.plusYears(years.toLong()).toString()
                    ChoiceChip(
                        selected = by == day,
                        onClick = { by = day },
                        label = pluralStringResource(R.plurals.life_goals_in_years, years, years),
                    )
                }
                val custom = by?.takeIf { day -> LifeGoalRules.BY_YEARS.none { today.plusYears(it.toLong()).toString() == day } }
                ChoiceChip(selected = custom != null, onClick = { picking = true }, label = custom ?: stringResource(R.string.life_goals_pick_day))
            }
            Label(stringResource(R.string.life_goals_pictures))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                pictures.filter { it.id !in removed }.forEach { picture ->
                    Thumb(onRemove = { removed += picture.id }) {
                        PictureImage(
                            id = picture.id,
                            version = pictureVersion,
                            description = stringResource(R.string.life_goals_pictures),
                            load = viewModel::picture,
                            modifier = Modifier.size(THUMB.dp),
                        )
                    }
                }
                added.forEach { picture ->
                    Thumb(onRemove = { added -= picture }) {
                        val bitmap = remember(picture) { BitmapFactory.decodeByteArray(picture.jpeg, 0, picture.jpeg.size)?.asImageBitmap() }
                        bitmap?.let {
                            Image(it, contentDescription = stringResource(R.string.life_goals_pictures), contentScale = ContentScale.Crop, modifier = Modifier.size(THUMB.dp))
                        }
                    }
                }
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.size(THUMB.dp),
                ) {
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = stringResource(R.string.life_goals_add_picture))
                }
            }
            Row {
                Spacer(Modifier.weight(1f))
                Button(onClick = ::save, enabled = canSave) { Text(stringResource(R.string.life_goals_save)) }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.accent, fontWeight = FontWeight.Bold)
}

/** A small picture in the editor with its remove button on the corner. */
@Composable
private fun Thumb(onRemove: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(THUMB.dp).clip(AppTheme.shapes.row)) {
        content()
        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).size(32.dp).padding(4.dp).background(AppTheme.colors.surface.copy(alpha = 0.85f), CircleShape),
        ) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.life_goals_remove_picture), modifier = Modifier.size(16.dp))
        }
    }
}

/** The calendar for a picked by date: any day after today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ByDayPicker(today: LocalDate, current: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = current?.let(::toMillis),
        initialDisplayedMonthMillis = toMillis(current ?: today.plusYears(1)),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = toDate(utcTimeMillis).isAfter(today)

            override fun isSelectableYear(year: Int) = year >= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(toDate(it)) } }, enabled = state.selectedDateMillis != null) {
                Text(stringResource(R.string.life_goals_pick_day))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.life_goals_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

private fun toMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun toDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

private const val THUMB = 88
private const val MAX_PICK = 6
