package com.ogzhngms.sorgez

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.ogzhngms.sorgez.ui.SorGezApp
import com.ogzhngms.sorgez.ui.SorGezTheme

class MainActivity : ComponentActivity() {
    private val viewModel: TripViewModel by viewModels {
        viewModelFactory {
            initializer {
                val gemini = GeminiPlanner(firebaseGemini())
                TripViewModel { answers -> gemini.plan(buildPrompt(answers, promptLanguage(), AppSettings.currency(application))) }
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppSettings.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before any other Firebase call, so every request carries an App Check token.
        Firebase.appCheck.installAppCheckProviderFactory(appCheckFactory())
        AppSettings.signInAndUpload(applicationContext)
        // The app is always dark, so keep the system bar icons light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            SorGezTheme {
                SorGezApp(viewModel, onLanguageChange = { AppSettings.saveLanguage(this, it); recreate() })
            }
        }
    }
}
