# SorGez

[![CI](https://github.com/ogzhngms/sorgez-android/actions/workflows/ci.yml/badge.svg)](https://github.com/ogzhngms/sorgez-android/actions/workflows/ci.yml)

An Android trip planner built with Jetpack Compose and Gemini. It opens on a turning Earth with a Start button at its centre, over dashed flight routes. Start zooms into the Earth and the app asks five short questions, one at a time: where you are going, for how long and in which month, who you are travelling with, your budget and what you want to do. When you confirm, it turns the answers into a prompt, asks Gemini for the plan as JSON that follows a fixed schema, and shows it as swipeable day pages.

The name joins two Turkish verbs, *sor* (ask) and *gez* (travel): the app asks, you travel. The logo splits it as Sor|Gez; the store name is Sorgez.

| Home | Questions | Plan | Profile |
|---|---|---|---|
| ![Home screen](docs/screenshots/1-home.png) | ![Question step](docs/screenshots/2-question.png) | ![Plan](docs/screenshots/3-plan.png) | ![Profile](docs/screenshots/4-profile.png) |

## How it works

```
answers ─► buildPrompt() ─► Gemini (JSON schema output) ─► JSON ─► parseItinerary() ─► Compose screens
```

- **Prompt:** `Trip.kt` turns the answers into a short English prompt and asks for the reply in the phone's language.
- **Gemini call:** `GeminiPlanner.kt` calls Gemini through Firebase AI Logic with `responseMimeType: application/json` and `ITINERARY_SCHEMA`, so the reply is JSON with exactly the fields the app reads. The Gemini key stays in the Firebase project and App Check vouches for the app, so the APK carries no key.
- **Free models, in the background:** the app works through eight free-tier models in order: Gemini 3.8, 3.7, 3.6 and 3.5 Flash, Gemini 3.5 and 3.1 Flash-Lite, and Gemini 2.5 Flash and Flash-Lite. A model that is retired, out of free quota, overloaded or closed to the project hands over to the next at once; one that is merely slow gets company after 30 seconds, when the next model starts alongside it and the first answer wins. Users never pick or see a model. With no network, or when App Check turns the app away, it stops at once.
- **Plan cache (debug builds):** while testing, each plan is saved on the device under its answers (`PlanCache.kt`), so asking for the same trip again costs no quota. Release builds always ask Gemini.
- **Parsing and UI:** `Itinerary.kt` parses the JSON with `org.json`. `TripViewModel` moves through the question, confirm, loading, result and error screens.
- **Home, destination and profile:** on Android 13+ the Earth is an AGSL shader (`ui/Earth.kt`) wrapping NASA's Blue Marble imagery (public domain) on a tilted, side-lit sphere with a thin blue rim of air; older phones get a Compose `Canvas` drawing. On the first question the Earth sits under the field and matching places are suggested as you type; once one is typed or picked, the Earth turns to it, zooms in and drops a pin. The other questions are icon tiles, and the month is picked on a wheel. Places are looked up offline in `Places.kt`, 267 cities, regions and countries by their English, Turkish and local names; an unknown place leaves the Earth turning without a pin. The profile screen, opened from the top-right icon, holds a sign-in placeholder, the language, the currency for plan prices (Turkish lira by default) and an about section. The choices are stored on the device (`AppSettings.kt`).
- **Firebase:** each install signs in with an anonymous Firebase account. The language and currency are kept on the device, because the language is needed before the first screen draws, and a copy goes to Cloud Firestore at `users/{uid}`. `firestore.rules` lets a user read and write only their own document and only the known fields and values. When real sign-in arrives, the anonymous account can be linked to Google and keep its uid and settings.

The interface comes in 15 languages: English, Turkish, Spanish, German, French, Italian, Portuguese, Russian, Arabic (right to left), Hindi, Chinese, Japanese, Korean, Indonesian and Azerbaijani. It follows the phone's language until one is picked from the flag menu on the profile screen, and the plan is written in the same language.

## Run it

1. Open the project in Android Studio and run the `app` configuration.
2. Debug builds prove themselves to App Check with a debug token. On the first run it is printed to logcat (`adb logcat | grep -i "debug secret"`); add it in the Firebase console under App Check > Apps > SorGez > Manage debug tokens. Reinstalling the app makes a new token.

> **Note:** release builds need the app registered with Play Integrity in App Check before they can reach Gemini.

## Tests

```bash
./gradlew testDebugUnitTest            # JVM: prompt, schema, parser, ViewModel flow, Gemini request contract
./gradlew connectedDebugAndroidTest    # device: Compose UI walk-through of the wizard
```

- `GeminiPlannerTest` runs the model fallback against a fake of Firebase AI Logic. It checks that models which cannot serve are skipped, that a slow model gets company and the first answer wins, that a lost connection or a rejected App Check token stops at once, and the blocked, truncated, bad-key, out-of-quota and all-busy paths.
- `PlanCacheTest` checks that the same answers find the saved plan however the place is typed, and that a plan that does not parse is not kept.
- `PromptTest` checks that the prompt carries every answer, the month and the language.
- `ItineraryTest` checks that every schema object is closed and fully required, and that the sample plans match the schema.
- `PlacesTest` checks that place names are found whatever their case, accents or extra words, that unknown places find nothing, and that suggestions match the start of a name once per place.
- `TranslationsTest` checks that every language in the picker has every string, since Android silently falls back to English for a missing one.
- `TripViewModelTest` covers Home and Profile navigation, starting over when backing out to Home, editing an answer from the summary, the happy path, a failure followed by a retry, a reply that breaks the schema, and cancelling while the plan is loading.
- `TripFlowTest` taps Start and all five questions on a device with a stub planner and checks the plan on screen. It also checks that with the keyboard open, Back first closes the keyboard, and that the flag menu picks a language.

CI runs the unit tests and a debug build on every push.

## Stack

Kotlin · Jetpack Compose (Material 3) · ViewModel · Coroutines · Firebase AI Logic (Gemini) · App Check · Firebase Auth · Cloud Firestore · JUnit · Compose UI Test
