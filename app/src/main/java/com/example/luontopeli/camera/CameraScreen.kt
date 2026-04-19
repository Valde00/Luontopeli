// camera/CameraScreen.kt
package com.example.luontopeli.camera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import java.io.File
import com.example.luontopeli.viewmodel.CameraViewModel
import com.example.luontopeli.ml.ClassificationResult
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape

@Composable
fun CameraScreen(viewModel: CameraViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ImageCapture use case – tallennetaan muuttujaan jotta nappia painaessa voidaan käyttää
    val imageCapture = remember { ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()
    }

    // Lupatarkistus
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val capturedImagePath by viewModel.capturedImagePath.collectAsState(initial = null)
    val isLoading by viewModel.isLoading.collectAsState(initial = false)

    if (!hasCameraPermission) {
        // Lupanäkymä
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CameraAlt, contentDescription = null,
                    modifier = Modifier.size(64.dp), tint = Color.Gray)
                Text("Kameran lupa tarvitaan", modifier = Modifier.padding(8.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Myönnä lupa")
                }
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Kameran esikatselu (tai otettu kuva)
        if (capturedImagePath == null) {
            // CameraX Preview – AndroidView koska PreviewView ei ole Composable
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        // Preview use case – näyttää kamerakuvan
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        // Sido kamera lifecycle-omistajaan ja use caseihin
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture  // Molemmat use caset samaan aikaan
                            )
                        } catch (e: Exception) {
                            // Kameran sidonta epäonnistui
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Kuvanappi
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                FloatingActionButton(
                    onClick = { viewModel.takePhoto(context, imageCapture) },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.CameraAlt, "Ota kuva", tint = Color.White)
                    }
                }
            }
        } else {
            // Näytetään otettu kuva + toimintopainikkeet
            CapturedImageView(
                imagePath = capturedImagePath!!,
                onRetake = { viewModel.clearCapturedImage() },
                onSave = { viewModel.saveCurrentSpot(context) }
            )
        }
    }
}

@Composable
fun CapturedImageView(
    imagePath: String,
    onRetake: () -> Unit,
    onSave: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Otettu kuva
        AsyncImage(
            model = File(imagePath),
            contentDescription = "Otettu kuva",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black)
        )

        // Toimintopainikkeet
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(onClick = onRetake) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text("Ota uudelleen")
            }
            Button(onClick = onSave) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text("Tallenna löytö")
            }
        }
    }
}
@Composable
fun ClassificationResultCard(result: ClassificationResult) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (result) {
                is ClassificationResult.Success ->
                    if (result.confidence > 0.8f)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (result) {
                is ClassificationResult.Success -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Tunnistettu:",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.width(8.dp))
                        Badge(
                            containerColor = when {
                                result.confidence > 0.8f -> Color(0xFF2E7D32)
                                result.confidence > 0.6f -> Color(0xFFF57C00)
                                else -> Color(0xFFD32F2F)
                            }
                        ) {
                            Text("${"%.0f".format(result.confidence * 100)}%")
                        }
                    }

                    Text(
                        text = result.label,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    LinearProgressIndicator(
                        progress = result.confidence,
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = when {
                            result.confidence > 0.8f -> Color(0xFF2E7D32)
                            result.confidence > 0.6f -> Color(0xFFF57C00)
                            else -> Color(0xFFD32F2F)
                        }
                    )
                }

                is com.example.luontopeli.ml.ClassificationResult.NotNature -> {
                    Text("Ei luontokohde", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Kuvassa tunnistettiin: ${result.allLabels.joinToString { it.text }}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                is com.example.luontopeli.ml.ClassificationResult.Error -> {
                    Text("Tunnistus epäonnistui: ${result.message}",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
