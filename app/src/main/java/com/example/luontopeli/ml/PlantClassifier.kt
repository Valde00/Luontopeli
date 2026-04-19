package com.example.luontopeli.ml

// ml/PlantClassifier.kt

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit -pohjainen kasvin tunnistaja (on-device Image Labeling).
 * Käyttää Google ML Kit:n paikallista kuvatunnistusta.
 * ML Kit -malli toimii täysin laitteella (on-device).
 */
class PlantClassifier {

    /** ML Kit Image Labeler -instanssi. Konfiguroitu 50% minimivarmuuskynnyksellä. */
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.5f)
            .build()
    )

    /**
     * Luontoon liittyvät avainsanat, joilla suodatetaan ML Kit:n tuloksia.
     */
    private val natureKeywords = setOf(
        "plant", "flower", "tree", "shrub", "leaf", "fern", "moss",
        "mushroom", "fungus", "grass", "herb", "bush", "berry",
        "pine", "birch", "spruce", "algae", "lichen", "bark",
        "nature", "forest", "woodland", "botanical", "flora"
    )

    /**
     * Analysoi kuvan ja tunnistaa siitä luontokohteet.
     */
    suspend fun classify(imageUri: Uri, context: Context): ClassificationResult {
        return suspendCancellableCoroutine { continuation ->
            try {
                val inputImage = InputImage.fromFilePath(context, imageUri)

                labeler.process(inputImage)
                    .addOnSuccessListener { labels ->
                        val natureLabels = labels.filter { label ->
                            natureKeywords.any { keyword ->
                                label.text.contains(keyword, ignoreCase = true)
                            }
                        }

                        val result = if (natureLabels.isNotEmpty()) {
                            val best = natureLabels.maxByOrNull { it.confidence }!!
                            // Käytetään jaettua ClassificationResult-tyyppiä (ClassificationResult.kt:stä)
                            ClassificationResult.Success(
                                label = best.text,
                                confidence = best.confidence,
                                allLabels = labels.take(5)  // Top 5 kaikista tunnistuksista
                            )
                        } else {
                            ClassificationResult.Error("Kuva ei sisältänyt tunnistettuja luontokohteita")
                        }

                        continuation.resume(result)
                    }
                    .addOnFailureListener { exception ->
                        continuation.resumeWithException(exception)
                    }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    /** Vapauttaa ML Kit -resurssit. Kutsutaan kun CameraViewModel tuhotaan. */
    fun close() {
        labeler.close()
    }
}
