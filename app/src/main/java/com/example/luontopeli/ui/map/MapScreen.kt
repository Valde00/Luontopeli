// 📁 ui/map/MapScreen.kt
package com.example.luontopeli.ui.map

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.luontopeli.viewmodel.MapViewModel
import com.example.luontopeli.viewmodel.WalkViewModel
import com.example.luontopeli.ui.util.formatDistance
import com.example.luontopeli.ui.util.formatDuration
import com.example.luontopeli.data.local.entity.NatureSpot
import com.example.luontopeli.ui.util.toFormattedDate
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import com.example.luontopeli.sensor.StepCounterManager
import android.content.Context
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    mapViewModel: MapViewModel = viewModel(),
    walkViewModel: WalkViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    // Ensure osmdroid has a user-agent configured so tile servers allow requests.
    // This must be set before creating MapView instances.
    try {
        Configuration.getInstance().setUserAgentValue(context.packageName)
    } catch (e: Exception) {
        // Ignore if set fails; map may still work with defaults
    }

    val isEmulator = remember {
        Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator") || Build.PRODUCT.contains("sdk")
    }

    // Check whether ACTIVITY_RECOGNITION permission has been granted (required for step sensors on Android Q+)
    var activityPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    // StepCounterManager instance remembered across recompositions
    val stepManager = remember { StepCounterManager(context) }

    // Keep a local mutable counter (main-thread state) for steps (updated by sensor callback)
    val stepCounterState = remember { mutableStateOf(0) }

    // Atomic reference to latest route points so sensor callback (non-UI thread) can read the latest route
    val latestPoints = remember { AtomicReference<List<GeoPoint>>(emptyList()) }

    // --- Lupapyynti ---
    val permissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // ACTIVITY_RECOGNITION tarvitaan Android 10+ askelmittarille
    val activityRecognitionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        // update state so composable reacts and can start sensors
        activityPermissionGranted = granted
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Launch only if not already granted
            if (!activityPermissionGranted) activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    // Näytä lupapyyntö-UI jos luvat puuttuu
    if (!permissionState.allPermissionsGranted) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Sijaintilupa tarvitaan karttaa varten")
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
                Text("Myönnä lupa")
            }
        }
        return
    }

    // --- Tila ---
    val isWalking by walkViewModel.isWalking.collectAsState()
    val routePoints by mapViewModel.routePoints.collectAsState()
    val currentLocation by mapViewModel.currentLocation.collectAsState()
    val gpsTotalDistance by mapViewModel.totalDistance.collectAsState()
    val natureSpots by mapViewModel.natureSpots.collectAsState()
    val currentSession by walkViewModel.currentSession.collectAsState()

    // Start/stop sensors and tracking when walking state changes
    LaunchedEffect(isWalking) {
        if (isWalking) {
            // reset counters and route
            stepCounterState.value = currentSession?.stepCount ?: 0
            mapViewModel.resetRoute()

            // Start GPS tracking
            mapViewModel.startTracking()

            // Start step counting — if device has sensor and not running on emulator, use it; otherwise simulate for emulator
            if (stepManager.isStepSensorAvailable() && !isEmulator && activityPermissionGranted) {
                stepManager.startStepCounting {
                    // This callback may be on a non-UI thread — update main-thread safe state via post to main looper
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        val newSteps = stepCounterState.value + 1
                        stepCounterState.value = newSteps

                        // If GPS has computed a distance (>= small epsilon), prefer it
                        val gpsDist = gpsTotalDistance
                        val distanceToWrite = if (gpsDist > 0.001f) gpsDist else newSteps * com.example.luontopeli.sensor.StepCounterManager.STEP_LENGTH_METERS

                        // Update only step count (and provide the best-available distance)
                        walkViewModel.updateSteps(newSteps, distanceToWrite)
                    }
                }
            } else {
                // Emulator or device without step sensor: simulate steps periodically
                // LaunchedEffect scope is active while isWalking==true, so this loop cancels automatically
                val simStepLength = com.example.luontopeli.sensor.StepCounterManager.STEP_LENGTH_METERS
                while (isWalking) {
                    // Wait 2 seconds between simulated steps (adjustable)
                    kotlinx.coroutines.delay(2000L)
                    val newSteps = stepCounterState.value + 1
                    stepCounterState.value = newSteps
                    // Estimate distance from steps
                    val estimatedDistance = newSteps * simStepLength
                    walkViewModel.updateSteps(newSteps, estimatedDistance)
                }
            }
        } else {
            // stop sensors and tracking
            stepManager.stopAll()
            mapViewModel.stopTracking()
        }
    }

    // When routePoints change (GPS-based), update atomic ref and update distance in DB
    LaunchedEffect(routePoints) {
        latestPoints.set(routePoints)
        // GPS-derived distance is authoritative; update DB distance only
        val distance = mapViewModel.totalDistance.value
        if (isWalking) {
            walkViewModel.updateDistance(distance)
        }
    }

    // Oulu oletussijaintina (koordinaatit: lat 65.01, lon 25.47)
    val defaultPosition = GeoPoint(65.0121, 25.4651)

    // When location changes, update CameraViewModel coordinates if present
    val cameraViewModel = hiltViewModel<com.example.luontopeli.viewmodel.CameraViewModel>()

    LaunchedEffect(currentLocation) {
        currentLocation?.let { loc ->
            cameraViewModel?.let { vm ->
                vm.currentLatitude = loc.latitude
                vm.currentLongitude = loc.longitude
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // --- Karttanäkymä ---
        Box(modifier = Modifier.weight(1f)) {

            // remember: MapView-instanssi muistetaan rekompositionien yli
            val mapViewState = remember { MapView(context) }

            DisposableEffect(Unit) {
                // Karttatyyli: MAPNIK = OpenStreetMap-oletustiilet
                mapViewState.setTileSource(TileSourceFactory.MAPNIK)
                // Mahdollista monisormipinch-zoom
                mapViewState.setMultiTouchControls(true)
                mapViewState.controller.setZoom(15.0)
                mapViewState.controller.setCenter(
                    currentLocation?.let { GeoPoint(it.latitude, it.longitude) }
                        ?: defaultPosition
                )

                onDispose {
                    // Vapauta resurssit kun Composable poistuu
                    mapViewState.onDetach()
                }
            }

            AndroidView(
                factory = { mapViewState },
                modifier = Modifier.fillMaxSize(),
                // update kutsutaan kun routePoints, currentLocation tai natureSpots muuttuu
                update = { mapView ->
                    mapView.overlays.clear()

                    // --- Reittiviiiva (Polyline) ---
                    if (routePoints.size >= 2) {
                        val polyline = Polyline().apply {
                            setPoints(routePoints)
                            outlinePaint.color = 0xFF2E7D32.toInt()  // M3-vihreä
                            outlinePaint.strokeWidth = 8f
                        }
                        mapView.overlays.add(polyline)
                    } else if (routePoints.size == 1) {
                        val p = routePoints[0]
                        val startMarker = Marker(mapView).apply {
                            position = p
                            title = "Start"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        mapView.overlays.add(startMarker)
                    }

                    // --- Luontokohteiden markkerit ---
                    natureSpots.forEach { spot ->
                        val lat = spot.latitude
                        val lon = spot.longitude
                        if (lat != null && lon != null) {
                            val marker = Marker(mapView).apply {
                                position = GeoPoint(lat, lon)
                                // Näytä kasvin nimi tai kohteen nimi info-ikkunassa
                                title = spot.plantLabel ?: spot.name
                                snippet = spot.timestamp.toFormattedDate()
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            }
                            mapView.overlays.add(marker)
                        }
                    }

                    // --- Seuraa nykyistä sijaintia ---
                    currentLocation?.let { loc ->
                        mapView.controller.animateTo(GeoPoint(loc.latitude, loc.longitude))
                    }

                    mapView.invalidate()  // Piirretään kartta uudelleen
                }
            )
        }

        // --- Kävelytilasto-kortti alareunassa ---
        WalkStatsCard(walkViewModel)
    }
}
//  ui/map/MapScreen.kt (jatkoa)
@Composable
fun WalkStatsCard(viewModel: WalkViewModel) {
    val session by viewModel.currentSession.collectAsState()
    val isWalking by viewModel.isWalking.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (isWalking) "Kävely käynnissä" else "Kävely pysäytetty",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            // Näytä tilastot vain jos sessio on olemassa
            session?.let { s ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${s.stepCount}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("askelta", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatDistance(s.distanceMeters),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("matka", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatDuration(s.startTime),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("aika", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                if (!isWalking) {
                    Button(
                        onClick = { viewModel.startWalk() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Aloita kävely") }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.stopWalk() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Lopeta") }
                }
            }
        }
    }
}