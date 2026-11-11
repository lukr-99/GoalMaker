package com.goalmaker.app.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.ui.components.GoalMakerCheckbox
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.nav.PlaceNavigationIcon
import com.goalmaker.app.ui.theme.AppTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Focus Board: a short, manually-ordered watchlist of tasks or upcoming events. Items are
 * dragged (long-press the handle) to reprioritise, swiped off to remove from the board without
 * deleting, and tapped on the checkbox to complete. The FAB opens the add sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    viewModel: FocusViewModel,
    onBack: (() -> Unit)?,
    actions: @Composable () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }

    if (state.showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissAddSheet,
            sheetState = sheetState,
        ) {
            AddSheet(
                candidates = state.candidates,
                areas = state.areas,
                dateFormat = dateFormat,
                onAdd = viewModel::addToBoard,
                onDismiss = viewModel::dismissAddSheet,
            )
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { ScreenTitle(stringResource(R.string.focus_title)) },
                navigationIcon = { PlaceNavigationIcon(onBack) },
                actions = { actions() },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openAddSheet) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.focus_add))
            }
        },
    ) { padding ->
        if (state.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = AppTheme.density.pagePadding.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.focus_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppTheme.colors.textMuted,
                )
            }
            return@Scaffold
        }

        DraggableBoard(
            items = state.items,
            areas = state.areas,
            today = LocalDate.now(),
            dateFormat = dateFormat,
            onComplete = viewModel::complete,
            onRemove = viewModel::removeFromBoard,
            onMove = viewModel::move,
            contentPadding = padding,
        )
    }
}

/** A LazyColumn whose rows reorder on drag. Long-press the handle at the row's right end. */
@Composable
private fun DraggableBoard(
    items: List<TaskItem>,
    areas: List<AreaItem>,
    today: LocalDate,
    dateFormat: DateTimeFormatter,
    onComplete: (String) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current

    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val rowTops = remember { mutableMapOf<Int, Float>() }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 88.dp,
            start = AppTheme.density.pagePadding.dp,
            end = AppTheme.density.pagePadding.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(AppTheme.density.rowGap.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, task ->
            val area = areas.find { it.id == task.areaId }
            val isDragging = dragIndex == index

            FocusRow(
                task = task,
                area = area,
                today = today,
                dateFormat = dateFormat,
                onComplete = { onComplete(task.id) },
                onRemove = { onRemove(task.id) },
                onDragStart = {
                    dragIndex = index
                    dragOffsetY = 0f
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onDrag = { deltaY ->
                    dragOffsetY += deltaY
                    val draggedTop = rowTops[dragIndex] ?: return@FocusRow
                    val current = draggedTop + dragOffsetY
                    val target = rowTops.entries
                        .minByOrNull { (_, top) -> abs(top - current) }
                        ?.key ?: return@FocusRow
                    if (target != dragIndex && target in items.indices) {
                        onMove(dragIndex, target)
                        val newTop = rowTops[target] ?: current
                        dragOffsetY = current - newTop
                        dragIndex = target
                    }
                },
                onDragEnd = {
                    dragIndex = -1
                    dragOffsetY = 0f
                },
                dragOffsetY = if (isDragging) dragOffsetY else 0f,
                isDragging = isDragging,
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        rowTops[index] = coords.positionInParent().y
                    },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FocusRow(
    task: TaskItem,
    area: AreaItem?,
    today: LocalDate,
    dateFormat: DateTimeFormatter,
    onComplete: () -> Unit,
    onRemove: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    dragOffsetY: Float,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val dismiss = rememberSwipeToDismissBoxState()

    val date = task.deadline ?: task.plannedDate
    val isLate = date != null && date < today
    val dateLabel = date?.let { if (isLate) "!! ${it.format(dateFormat)}" else it.format(dateFormat) }

    val areaColor = area?.let { a ->
        AppTheme.areaColors.firstOrNull { it.id == a.colorId }
    }

    Surface(
        shape = AppTheme.shapes.row,
        color = AppTheme.colors.surface,
        shadowElevation = if (isDragging) 4.dp else 0.dp,
        modifier = modifier
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .graphicsLayer { alpha = if (isDragging) 0.9f else 1f },
    ) {
        SwipeToDismissBox(
            state = dismiss,
            enableDismissFromStartToEnd = false,
            onDismiss = {
                onRemove()
                scope.run {}
            },
            backgroundContent = {
                if (dismiss.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.errorContainer, AppTheme.shapes.row)
                            .padding(end = 20.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = AppTheme.density.rowMinHeight.dp)
                    .padding(end = 12.dp),
            ) {
                // Area colour stripe.
                if (areaColor != null) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(AppTheme.density.rowMinHeight.dp)
                            .background(
                                color = AppTheme.colors.areaContainer(areaColor),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp),
                            ),
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Spacer(Modifier.width(4.dp))
                }

                GoalMakerCheckbox(
                    checked = false,
                    onCheckedChange = { if (it) onComplete() },
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp),
                ) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (dateLabel != null || area != null) {
                        Text(
                            listOfNotNull(dateLabel, area?.name).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isLate) AppTheme.colors.danger else AppTheme.colors.textMuted,
                        )
                    }
                }

                Icon(
                    Icons.Outlined.DragHandle,
                    contentDescription = stringResource(R.string.focus_drag),
                    tint = AppTheme.colors.textMuted,
                    modifier = Modifier
                        .size(24.dp)
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { onDragStart() },
                                onDrag = { _, amount -> onDrag(amount.y) },
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragEnd,
                            )
                        },
                )
            }
        }
    }
}

/** Bottom sheet showing all open tasks not on the board. Tap one to add it. */
@Composable
private fun AddSheet(
    candidates: List<TaskItem>,
    areas: List<AreaItem>,
    dateFormat: DateTimeFormatter,
    onAdd: (TaskItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
    ) {
        Text(
            stringResource(R.string.focus_add_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        if (candidates.isEmpty()) {
            Text(
                stringResource(R.string.focus_add_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = AppTheme.colors.textMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        } else {
            LazyColumn {
                itemsIndexed(candidates, key = { _, item -> item.id }) { _, task ->
                    val area = areas.find { it.id == task.areaId }
                    val date = task.deadline ?: task.plannedDate
                    val isLate = date != null && date < today
                    val dateLabel = date?.let { if (isLate) "!! ${it.format(dateFormat)}" else it.format(dateFormat) }

                    TextButton(
                        onClick = { onAdd(task) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                task.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = AppTheme.colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (dateLabel != null || area != null) {
                                Text(
                                    listOfNotNull(dateLabel, area?.name).joinToString(" · "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isLate) AppTheme.colors.danger else AppTheme.colors.textMuted,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.End)
                .padding(horizontal = 12.dp),
        ) {
            Text(stringResource(R.string.focus_add_cancel))
        }
    }
}
