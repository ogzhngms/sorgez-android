package com.ogzhngms.sorgez

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

// Wizard order; TripViewModel and the question screen both follow this list.
val QUESTIONS = listOf(
    R.string.q_destination,
    R.string.q_days,
    R.string.q_companions,
    R.string.q_budget,
    R.string.q_interests,
)

const val MAX_DAYS = 14

enum class Companions(@StringRes val label: Int, val prompt: String, @DrawableRes val icon: Int) {
    SOLO(R.string.companions_solo, "solo", R.drawable.ic_person),
    PARTNER(R.string.companions_partner, "as a couple", R.drawable.ic_favorite),
    FAMILY(R.string.companions_family, "with family", R.drawable.ic_family),
    FRIENDS(R.string.companions_friends, "with friends", R.drawable.ic_group),
}

enum class Budget(@StringRes val label: Int, val prompt: String, @DrawableRes val icon: Int) {
    LOW(R.string.budget_low, "low, keep costs down", R.drawable.ic_money_low),
    MEDIUM(R.string.budget_medium, "mid-range", R.drawable.ic_money_mid),
    HIGH(R.string.budget_high, "luxury", R.drawable.ic_money_high),
}

enum class Interest(@StringRes val label: Int, val prompt: String, @DrawableRes val icon: Int) {
    CULTURE(R.string.interest_culture, "history and culture", R.drawable.ic_culture),
    FOOD(R.string.interest_food, "food and drink", R.drawable.ic_food),
    NATURE(R.string.interest_nature, "nature", R.drawable.ic_nature),
    ART(R.string.interest_art, "museums and art", R.drawable.ic_art),
    SHOPPING(R.string.interest_shopping, "shopping", R.drawable.ic_shopping),
    NIGHTLIFE(R.string.interest_nightlife, "nightlife", R.drawable.ic_nightlife),
    BEACH(R.string.interest_beach, "beaches", R.drawable.ic_beach),
    ADVENTURE(R.string.interest_adventure, "adventure", R.drawable.ic_adventure),
}

data class TripAnswers(
    val destination: String = "",
    val days: Int = 3,
    // 1 for January to 12 for December; null when the traveller does not know yet.
    val month: Int? = null,
    val companions: Companions = Companions.SOLO,
    val budget: Budget = Budget.MEDIUM,
    val interests: Set<Interest> = emptySet(),
    val notes: String = "",
)

const val SYSTEM_PROMPT =
    "You are a travel planner. Turn the traveller's answers into a realistic day-by-day itinerary. " +
        "Keep each day in one area of the destination, give every activity a start time in 24-hour HH:MM format, " +
        "estimate costs per person in the currency asked for, and keep each description to one or two sentences."

fun buildPrompt(answers: TripAnswers, language: String, currency: Currency = Currency.TRY): String = buildString {
    appendLine("Destination: ${answers.destination.trim()}")
    appendLine("Length: exactly ${answers.days} days")
    answers.month?.let { appendLine("Month: ${Month.of(it).getDisplayName(TextStyle.FULL, Locale.ENGLISH)}, so suit the plan to that season and its weather") }
    appendLine("Travelling: ${answers.companions.prompt}")
    appendLine("Budget: ${answers.budget.prompt}")
    appendLine("Currency: ${currency.name}")
    appendLine("Interests: " + answers.interests.sorted().joinToString { it.prompt }.ifEmpty { "no preference" })
    // There is no pace question: a balanced day suits most trips, and the notes can ask for calmer or fuller days.
    appendLine("Pace: balanced, four or five activities a day, unless the other wishes ask for something else")
    if (answers.notes.isNotBlank()) appendLine("Other wishes: ${answers.notes.trim()}")
    append("Write every text value in $language.")
}

// The model answers in the app's language, e.g. "Turkish"; a phone language the app lacks falls back to English.
fun promptLanguage(): String {
    val locale = Locale.getDefault().takeIf { current -> Language.entries.any { it.tag == current.language } } ?: Locale.ENGLISH
    return locale.getDisplayLanguage(Locale.ENGLISH)
}
