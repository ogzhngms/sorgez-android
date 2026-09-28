package com.ogzhngms.sorgez.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ogzhngms.sorgez.AppSettings
import com.ogzhngms.sorgez.Budget
import com.ogzhngms.sorgez.BuildConfig
import com.ogzhngms.sorgez.Companions
import com.ogzhngms.sorgez.Day
import com.ogzhngms.sorgez.Interest
import com.ogzhngms.sorgez.Itinerary
import com.ogzhngms.sorgez.Language
import com.ogzhngms.sorgez.MAX_DAYS
import com.ogzhngms.sorgez.QUESTIONS
import com.ogzhngms.sorgez.R
import com.ogzhngms.sorgez.Screen
import com.ogzhngms.sorgez.TripAnswers
import com.ogzhngms.sorgez.TripViewModel
import com.ogzhngms.sorgez.findPlace
import com.ogzhngms.sorgez.normalize
import com.ogzhngms.sorgez.suggestPlaces
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun SorGezApp(vm: TripViewModel, onLanguageChange: (Language) -> Unit = {}) {
    val context = LocalContext.current
    var currency by remember { mutableStateOf(AppSettings.currency(context)) }
    val language = Language.entries.firstOrNull { it.tag == Locale.getDefault().language }
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    // Back closes an open keyboard first; only the next press leaves the step.
    // The keyboard is checked at press time, so a second press during its closing animation still goes back.
    val back = {
        val keyboardOpen = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        if (keyboardOpen) focusManager.clearFocus() else vm.back()
    }
    BackHandler(enabled = vm.screen != Screen.Home, onBack = back)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(vm.screen, transitionSpec = { transition(initialState, targetState) }, label = "screen") { screen ->
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                when (screen) {
                    Screen.Home -> HomeScreen(onStart = vm::start, onProfile = vm::openProfile)
                    Screen.Profile -> ProfileScreen(
                        language,
                        currency,
                        onLanguage = onLanguageChange,
                        onCurrency = { currency = it; AppSettings.saveCurrency(context, it) },
                        onBack = vm::back,
                    )
                    is Screen.Question -> QuestionScreen(screen.step, vm.answers, vm::update, vm::next, back)
                    Screen.Confirm -> ConfirmScreen(vm.answers, onPlan = vm::submit, onEdit = vm::back, onEditStep = vm::edit)
                    Screen.Loading -> LoadingScreen(vm.answers.destination, onCancel = vm::back)
                    is Screen.Result -> ResultScreen(screen, onNewPlan = vm::restart)
                    is Screen.Failed -> FailedScreen(screen, onRetry = vm::submit, onEdit = vm::back)
                }
            }
        }
    }
}

// Start zooms into the Earth, question steps slide sideways, everything else cross-fades.
private fun AnimatedContentTransitionScope<Screen>.transition(from: Screen, to: Screen): ContentTransform = when {
    from == Screen.Home && to is Screen.Question ->
        (fadeIn(tween(400, delayMillis = 250)) + scaleIn(tween(400, delayMillis = 250), initialScale = 0.9f)) togetherWith
            (fadeOut(tween(450)) + scaleOut(tween(550), targetScale = 2.5f))
    from is Screen.Question && to == Screen.Home ->
        (fadeIn(tween(450)) + scaleIn(tween(550), initialScale = 2.5f)) togetherWith
            (fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0.9f))
    from is Screen.Question && to is Screen.Question -> {
        val forward = to.step > from.step
        (slideInHorizontally { if (forward) it else -it } + fadeIn()) togetherWith
            (slideOutHorizontally { if (forward) -it else it } + fadeOut())
    }
    else -> fadeIn(tween(300)) togetherWith fadeOut(tween(300))
}

@Composable
private fun QuestionScreen(
    step: Int,
    answers: TripAnswers,
    onUpdate: ((TripAnswers) -> TripAnswers) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        LinearProgressIndicator(progress = { (step + 1f) / QUESTIONS.size }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.step_counter, step + 1, QUESTIONS.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(QUESTIONS[step]), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        if (step == 0) {
            DestinationStep(answers.destination, { value -> onUpdate { it.copy(destination = value) } }, onNext, Modifier.weight(1f))
        } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (step) {
                1 -> DaysStep(
                    answers.days,
                    answers.month,
                    onDays = { days -> onUpdate { it.copy(days = days) } },
                    onMonth = { month -> onUpdate { it.copy(month = month) } },
                )
                2 -> IconChoices(Companions.entries, { it == answers.companions }, { it.label }, { it.icon }) { choice ->
                    onUpdate { it.copy(companions = choice) }
                }
                3 -> IconChoices(Budget.entries, { it == answers.budget }, { it.label }, { it.icon }, columns = 3) { choice ->
                    onUpdate { it.copy(budget = choice) }
                }
                4 -> {
                    IconChoices(Interest.entries, { it in answers.interests }, { it.label }, { it.icon }, compact = true) { choice ->
                        onUpdate { it.copy(interests = if (choice in it.interests) it.interests - choice else it.interests + choice) }
                    }
                    Spacer(Modifier.height(20.dp))
                    OutlinedTextField(
                        value = answers.notes,
                        onValueChange = { value -> onUpdate { it.copy(notes = value) } },
                        label = { Text(stringResource(R.string.hint_notes)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_back)) }
            Button(
                onClick = onNext,
                enabled = step != 0 || answers.destination.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.action_next)) }
        }
    }
}

// The length large between − and +, tiles for the usual lengths, then the month of travel.
@Composable
private fun DaysStep(days: Int, month: Int?, onDays: (Int) -> Unit, onMonth: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        DayPicker(days, onDays)
        DayTiles(days, onDays)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.when_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        MonthPicker(month, onMonth)
    }
}

@Composable
private fun DayPicker(days: Int, onChange: (Int) -> Unit) {
    val fewer = stringResource(R.string.cd_fewer_days)
    val more = stringResource(R.string.cd_more_days)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        FilledTonalButton(
            onClick = { onChange(days - 1) },
            enabled = days > 1,
            modifier = Modifier.semantics { contentDescription = fewer },
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(
            pluralStringResource(R.plurals.days, days, days),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        FilledTonalButton(
            onClick = { onChange(days + 1) },
            enabled = days < MAX_DAYS,
            modifier = Modifier.semantics { contentDescription = more },
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

// A length and, for the ones people name, that name: "Weekend" over "2 days".
private class Length(val days: Int, @StringRes val name: Int?)

private val LENGTHS = listOf(
    Length(2, R.string.days_weekend),
    Length(3, null),
    Length(5, null),
    Length(7, R.string.days_week),
    Length(10, null),
    Length(14, R.string.days_two_weeks),
)

@Composable
private fun DayTiles(days: Int, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LENGTHS.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { length ->
                    val count = pluralStringResource(R.plurals.days, length.days, length.days)
                    SelectTile(length.days == days, { onPick(length.days) }, Modifier.weight(1f).height(64.dp)) { color ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(length.name?.let { stringResource(it) } ?: count, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
                            if (length.name != null) {
                                Text(count, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

// An iOS-style wheel: "Not sure yet", then January to December; whatever settles in the middle band is picked.
// A tap on a row turns the wheel to it.
@Composable
private fun MonthPicker(month: Int?, onPick: (Int?) -> Unit) {
    val locale = Locale.getDefault()
    val options = remember { listOf<Int?>(null) + (1..12) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = options.indexOf(month).coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    val centred by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - middle) }?.index ?: 0
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress to centred }
            .filter { (scrolling, _) -> !scrolling }
            .collect { (_, index) -> if (options[index] != month) onPick(options[index]) }
    }
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(WHEEL_ROW * WHEEL_ROWS), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(WHEEL_ROW).background(colors.primary.copy(alpha = 0.14f), MaterialTheme.shapes.medium))
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center),
            contentPadding = PaddingValues(vertical = WHEEL_ROW * (WHEEL_ROWS / 2)),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(options) { index, option ->
                val distance = abs(index - centred)
                Box(
                    Modifier.fillMaxWidth().height(WHEEL_ROW).clickable { scope.launch { state.animateScrollToItem(index) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option?.let { monthName(it, locale) } ?: stringResource(R.string.when_unknown),
                        style = if (distance == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                        fontWeight = if (distance == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (distance == 0) colors.primary else colors.onSurfaceVariant.copy(alpha = if (distance == 1) 0.7f else 0.35f),
                    )
                }
            }
        }
    }
}

private val WHEEL_ROW = 40.dp
private const val WHEEL_ROWS = 5

internal fun monthName(month: Int, locale: Locale): String =
    Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }

// An outlined tile, green-tinted when chosen, like the icon tiles; the content gets the text colour to use.
@Composable
private fun SelectTile(chosen: Boolean, onClick: () -> Unit, modifier: Modifier, content: @Composable (Color) -> Unit) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.semantics { selected = chosen },
        colors = CardDefaults.outlinedCardColors(containerColor = if (chosen) colors.primary.copy(alpha = 0.14f) else colors.surface),
        border = BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) colors.primary else colors.outlineVariant),
    ) {
        Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { content(if (chosen) colors.primary else colors.onSurface) }
        }
    }
}

// The Earth spins under the field; typing offers matching places below it, and the Earth flies to
// whatever is written and drops a pin. Picking a suggestion fills the field and closes the list.
@Composable
private fun DestinationStep(destination: String, onChange: (String) -> Unit, onNext: () -> Unit, modifier: Modifier) {
    val focusManager = LocalFocusManager.current
    val place = remember(destination) { findPlace(destination) }
    val suggestions = remember(destination) {
        suggestPlaces(destination).takeUnless { names -> names.any { normalize(it) == normalize(destination) } }.orEmpty()
    }
    Column(modifier) {
        OutlinedTextField(
            value = destination,
            onValueChange = onChange,
            placeholder = { Text(stringResource(R.string.hint_destination)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onNext() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            DestinationEarth(place, Modifier.align(Alignment.Center).aspectRatio(1f, matchHeightConstraintsFirst = true))
            if (suggestions.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    suggestions.forEach { name ->
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onChange(name); focusManager.clearFocus() }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    }
                }
            }
        }
    }
}

// Large tiles, two to a row unless told otherwise, each an icon over its label; the chosen one is outlined and tinted green.
// Compact tiles put the icon beside the label, for longer lists.
@Composable
private fun <T> IconChoices(
    options: List<T>,
    selected: (T) -> Boolean,
    @StringRes label: (T) -> Int,
    @DrawableRes icon: (T) -> Int,
    columns: Int = 2,
    compact: Boolean = false,
    onClick: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        options.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { option ->
                    val chosen = selected(option)
                    val colors = MaterialTheme.colorScheme
                    OutlinedCard(
                        onClick = { onClick(option) },
                        modifier = Modifier.weight(1f).height(if (compact) 60.dp else 128.dp).semantics { this.selected = chosen },
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (chosen) colors.primary.copy(alpha = 0.14f) else colors.surface,
                        ),
                        border = BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) colors.primary else colors.outlineVariant),
                    ) {
                        val tint = if (chosen) colors.primary else colors.onSurfaceVariant
                        val text = @Composable {
                            Text(
                                stringResource(label(option)),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                                color = if (chosen) colors.primary else colors.onSurface,
                                textAlign = TextAlign.Center,
                            )
                        }
                        if (compact) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(icon(option)), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                text()
                            }
                        } else {
                            Column(
                                Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                            ) {
                                Icon(painterResource(icon(option)), contentDescription = null, tint = tint, modifier = Modifier.size(40.dp))
                                text()
                            }
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
// The space under the summary is left free on purpose: it is kept for an ad.
private fun ConfirmScreen(answers: TripAnswers, onPlan: () -> Unit, onEdit: () -> Unit, onEditStep: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.confirm_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            // Each row opens its question; Next there comes straight back here.
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    Summary(R.string.label_destination, answers.destination.trim()) { onEditStep(0) }
                    Summary(R.string.label_days, pluralStringResource(R.plurals.days, answers.days, answers.days)) { onEditStep(1) }
                    answers.month?.let { Summary(R.string.label_when, monthName(it, Locale.getDefault())) { onEditStep(1) } }
                    Summary(R.string.label_companions, stringResource(answers.companions.label)) { onEditStep(2) }
                    Summary(R.string.label_budget, stringResource(answers.budget.label)) { onEditStep(3) }
                    Summary(
                        R.string.label_interests,
                        answers.interests.sorted().map { stringResource(it.label) }.joinToString()
                            .ifEmpty { stringResource(R.string.interests_none) },
                    ) { onEditStep(4) }
                    if (answers.notes.isNotBlank()) Summary(R.string.label_notes, answers.notes.trim()) { onEditStep(4) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_back)) }
            Button(onClick = onPlan, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_plan)) }
        }
    }
}

@Composable
private fun Summary(@StringRes label: Int, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.action_edit), onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
        Text(value, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Icon(
            painterResource(R.drawable.ic_edit),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
// While the plan is written, the Earth flies to the destination and zooms in, as on the first question.
private fun LoadingScreen(destination: String, onCancel: () -> Unit) {
    val place = remember(destination) { findPlace(destination) }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DestinationEarth(place, Modifier.fillMaxWidth(0.8f))
        LinearProgressIndicator(Modifier.fillMaxWidth(0.5f))
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.loading_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        // What is being worked on, a new line every few seconds; an infinite transition so UI tests can settle.
        val step by rememberInfiniteTransition(label = "steps").animateFloat(
            initialValue = 0f,
            targetValue = LOADING_STEPS.size.toFloat(),
            animationSpec = infiniteRepeatable(tween(LOADING_STEPS.size * 2_800, easing = LinearEasing)),
            label = "steps",
        )
        Crossfade(step.toInt().coerceAtMost(LOADING_STEPS.lastIndex), label = "step") { index ->
            Text(
                stringResource(LOADING_STEPS[index]),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
    }
}

private val LOADING_STEPS = listOf(R.string.loading_step_1, R.string.loading_step_2, R.string.loading_step_3, R.string.loading_step_4)

@Composable
private fun ResultScreen(result: Screen.Result, onNewPlan: () -> Unit) {
    val itinerary = result.itinerary
    val context = LocalContext.current
    var showJson by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(itinerary.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(itinerary.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.budget_estimate, itinerary.estimatedBudget),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        items(itinerary.days) { day -> DayCard(day) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.tips_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    itinerary.tips.forEach { Text("• $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        if (BuildConfig.DEBUG) item {
            Column {
                TextButton(onClick = { showJson = !showJson }) {
                    Text(stringResource(if (showJson) R.string.hide_json else R.string.show_json))
                }
                if (showJson) Code(remember(result.json) { JSONObject(result.json).toString(2) })
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { share(context, itinerary) }, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_share), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_share))
                }
                Button(onClick = onNewPlan, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_new_plan)) }
            }
        }
    }
}

// The plan as plain text for the share sheet, laid out like the screen: title, budget, days, tips.
private fun share(context: Context, itinerary: Itinerary) {
    val text = buildString {
        appendLine(itinerary.title)
        appendLine(itinerary.summary)
        appendLine(context.getString(R.string.budget_estimate, itinerary.estimatedBudget))
        itinerary.days.forEach { day ->
            appendLine()
            appendLine(context.getString(R.string.day_title, day.day, day.title))
            day.activities.forEach { appendLine("${it.time}  ${it.title} (${it.cost})") }
        }
        if (itinerary.tips.isNotEmpty()) {
            appendLine()
            appendLine(context.getString(R.string.tips_title))
            itinerary.tips.forEach { appendLine("• $it") }
        }
    }.trim()
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, itinerary.title).putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.action_share)))
}

@Composable
private fun DayCard(day: Day) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                stringResource(R.string.day_title, day.day, day.title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            day.activities.forEach { activity ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        activity.time,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(48.dp),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(activity.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(activity.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(activity.cost, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun FailedScreen(failed: Screen.Failed, onRetry: () -> Unit, onEdit: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.error_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(failed.message))
        failed.detail?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 6)
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_retry)) }
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_edit)) }
    }
}

@Composable
private fun Code(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
        Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
    }
}
