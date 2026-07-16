package com.tingxia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingxia.app.data.repo.UserPreferencesRepository
import com.tingxia.app.ui.navigation.TingXiaNavHost
import com.tingxia.app.ui.theme.TingXiaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkPref by userPreferencesRepository.darkTheme.collectAsStateWithLifecycle(initialValue = true)
            val useDark = when (darkPref) {
                true -> true
                false -> false
                null -> isSystemInDarkTheme()
            }
            TingXiaTheme(darkTheme = useDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                ) {
                    TingXiaNavHost()
                }
            }
        }
    }
}
