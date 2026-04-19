// MainActivity.kt
package com.example.luontopeli

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.example.luontopeli.ui.navigation.LuontopeliBottomBar
import com.example.luontopeli.ui.navigation.LuontopeliNavHost
import com.example.luontopeli.ui.theme.LuontopeliTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Sovelluksen pääaktiviteetti ja ainoa Activity (Single Activity -arkkitehtuuri).
 *
 * @AndroidEntryPoint mahdollistaa Hilt-riippuvuusinjektion tässä aktiviteetissa.
 * Kaikki näkymät toteutetaan Jetpack Compose -komponentteina ja navigointi
 * hoidetaan Navigation Compose -kirjastolla.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Aktiviteetin luontimetodi.
     * - installSplashScreen() näyttää käynnistysruudun (splash screen) sovelluksen avautuessa
     * - setContent asettaa Compose-sisällön, joka käyttää LuontopeliTheme-teemaa
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            LuontopeliTheme {
                StartupHost()
            }
        }
    }
}

/**
 * Komposiitti, joka hoitaa sovelluksen käynnistyslogiikan.
 *
 * Suorittaa tarvittavat alustusprosessit sovelluksen käynnistyessä ja käsittelee mahdolliset virheet.
 * Näyttää virheilmoitukset tai siirtyy sovelluksen pääkomponenttiin (LuontopeliApp) aloituksen onnistuttua.
 */
@Composable
fun StartupHost() {
    var startupError by remember { mutableStateOf<Throwable?>(null) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            initialized = true
        } catch (t: Throwable) {
            Log.e("Luontopeli", "Startup init error", t)
            startupError = t
        }
    }

    if (startupError != null) {
        val stack = remember(startupError) {
            val sw = java.io.StringWriter()
            val pw = java.io.PrintWriter(sw)
            startupError?.printStackTrace(pw)
            sw.toString()
        }
        Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text(text = "App startup error: ${startupError?.message}")
            Text(text = stack)
        }
    } else if (initialized) {
        LuontopeliApp()
    } else {
        Text("Starting...")
    }
}

/**
 * Sovelluksen juurikomponentti (root composable).
 *
 * Rakentaa sovelluksen perusrakenteen:
 * - Scaffold tarjoaa Material3-pohjarakenteen (bottomBar, content area)
 * - LuontopeliBottomBar näyttää alanäkymäpalkin navigointia varten
 * - LuontopeliNavHost hallinnoi näkymien välistä navigointia NavController:n avulla
 */
@Composable
fun LuontopeliApp() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            LuontopeliBottomBar(navController = navController)
        }
    ) { innerPadding ->
        LuontopeliNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}