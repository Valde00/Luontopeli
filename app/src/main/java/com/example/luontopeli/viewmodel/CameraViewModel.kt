// viewmodel/CameraViewModel.kt
package com.example.luontopeli.viewmodel

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.luontopeli.data.local.entity.NatureSpot
import com.example.luontopeli.data.repository.NatureSpotRepository
import com.example.luontopeli.ml.ClassificationResult
import com.example.luontopeli.ml.PlantClassifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * ViewModel kameranäkymälle (CameraScreen).
 * Käytetään Hilt-riippuvuusinjektiota tarjoamaan repository ja classifier.
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    private val repository: NatureSpotRepository,
    private val classifier: PlantClassifier
) : ViewModel() {

    /** Otetun kuvan paikallinen tiedostopolku (null = ei kuvaa) */
    private val _capturedImagePath = MutableStateFlow<String?>(null)
    val capturedImagePath: StateFlow<String?> = _capturedImagePath.asStateFlow()

    /** Latausilmaisin (true = kuva otetaan tai tunnistus käynnissä) */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** ML Kit -tunnistuksen tulos (null = tunnistusta ei ole suoritettu) */
    private val _classificationResult = MutableStateFlow<ClassificationResult?>(null)
    val classificationResult: StateFlow<ClassificationResult?> = _classificationResult.asStateFlow()

    /** Nykyinen GPS-sijainti (asetetaan MapViewModelista) löydön sijaintitiedoiksi */
    var currentLatitude: Double = 0.0
    var currentLongitude: Double = 0.0

    /**
     * Ottaa kuvan CameraX:n ImageCapture-objektilla ja tunnistaa kasvin.
     *
     * Prosessi:
     * 1. Luo aikaleimalla nimetyn kuvatiedoston laitteen sisäiseen tallennustilaan
     * 2. Ottaa kuvan CameraX:lla ja tallentaa sen tiedostoon
     * 3. Onnistuneen kuvan jälkeen käynnistää ML Kit -tunnistuksen taustasäikeessä
     * 4. Päivittää UI-tilan tunnistuksen tuloksella
     */
    // takePhoto()-metodi päivittyy – ML Kit -tunnistus lisätään onImageSaved-callbackiin:
    fun takePhoto(context: Context, imageCapture: ImageCapture) {
        _isLoading.value = true

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date())
        val outputDir = File(context.filesDir, "nature_photos").also { it.mkdirs() }
        val outputFile = File(outputDir, "IMG_${timestamp}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {

                /** Kuva tallennettu onnistuneesti – käynnistä ML Kit -tunnistus */
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    _capturedImagePath.value = outputFile.absolutePath

                    // Tunnista kasvi kuvasta ML Kit:n avulla
                    viewModelScope.launch {
                        try {
                            val uri = Uri.fromFile(outputFile)
                            val result = classifier.classify(uri, context)
                            _classificationResult.value = result
                        } catch (e: Exception) {
                            _classificationResult.value =
                                ClassificationResult.Error(e.message ?: "Tuntematon virhe")
                        }
                        _isLoading.value = false
                    }
                }

                /** Kuvan otto epäonnistui (esim. kameravirhe) */
                override fun onError(exception: ImageCaptureException) {
                    _isLoading.value = false
                }
            }
        )
    }

    /**
     * Tallentaa nykyisen luontolöydön Room-tietokantaan.
     * Luo NatureSpot-entiteetin tunnistustuloksen perusteella.
     */
    fun saveCurrentSpot(context: Context) {
        val imagePath = _capturedImagePath.value ?: return
        viewModelScope.launch {
            val result = _classificationResult.value

            val file = File(imagePath)
            val finalPath: String = try {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Luontopeli")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

                val uri = resolver.insert(collection, values)
                if (uri != null) {
                    resolver.openOutputStream(uri).use { out ->
                        file.inputStream().use { input -> input.copyTo(out!!) }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                    }
                    uri.toString()
                } else imagePath
            } catch (e: Exception) {
                imagePath
            }

            // Luodaan NatureSpot tunnistustuloksen perusteella
            val spot = NatureSpot(
                name = when (result) {
                    is ClassificationResult.Success -> result.label
                    else -> "Luontolöytö"
                },
                latitude = currentLatitude,
                longitude = currentLongitude,
                imageLocalPath = finalPath,
                plantLabel = (result as? ClassificationResult.Success)?.label,
                confidence = (result as? ClassificationResult.Success)?.confidence
            )
            repository.insertSpot(spot)
            clearCapturedImage()
        }
    }

    /**
     * Vapauttaa ML Kit -resurssit ViewModelin tuhoutuessa.
     */
    override fun onCleared() {
        super.onCleared()
        classifier.close()
    }

    /**
     * Tyhjentää otetun kuvan ja tunnistustuloksen.
     * Palauttaa UI:n kameran esikatselunäkymään.
     */
    fun clearCapturedImage() {
        _capturedImagePath.value = null
        _classificationResult.value = null
    }

}