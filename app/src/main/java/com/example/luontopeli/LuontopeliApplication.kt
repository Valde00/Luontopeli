//  LuontopeliApplication.kt
package com.example.luontopeli

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import java.io.File

/**
 * Sovelluksen Application-luokka.
 *
 * @HiltAndroidApp-annotaatio tekee tästä Hilt-riippuvuusinjektion juurikomponentin.
 * Hilt generoi automaattisesti DI-komponentit ja injektoi riippuvuudet
 * kaikkiin @AndroidEntryPoint-annotoiduilla merkittyihin luokkiin (Activity, Fragment jne.).
 *
 * Tämä luokka on rekisteröity AndroidManifest.xml:ssä android:name-attribuutilla.
 */
@HiltAndroidApp
class LuontopeliApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashFile = File(filesDir, "last_crash.txt")
                crashFile.writeText("Thread: ${thread.name}\n")
                val sw = java.io.StringWriter()
                val pw = java.io.PrintWriter(sw)
                throwable.printStackTrace(pw)
                crashFile.appendText(sw.toString())
            } catch (t: Throwable) {
                Log.e("LuontopeliApp", "Failed to write crash file", t)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
