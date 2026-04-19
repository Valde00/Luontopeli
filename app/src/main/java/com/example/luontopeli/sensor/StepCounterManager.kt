package com.example.luontopeli.sensor

// sensor/StepCounterManager.kt

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

/**
 * Sensorien hallintapalvelu askelmittarille ja gyroskoopille.
 */
class StepCounterManager(context: Context) {
    private val TAG = "StepCounterManager"

    /** Android-järjestelmän SensorManager sensorien rekisteröimiseen */
    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    /** Askeltunnistin-sensori (null jos laite ei tue sitä) */
    private val stepDetectorSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val stepCounterSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    /** Gyroskooppi-sensori (null jos laite ei tue sitä) */
    private val gyroSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** Askelmittarin SensorEventListener-kuuntelija */
    private var stepListener: SensorEventListener? = null
    /** Gyroskoopin SensorEventListener-kuuntelija */
    private var gyroListener: SensorEventListener? = null
    private var accelListener: SensorEventListener? = null

    private var lastStepCounterValue: Float? = null
    private var registeredSensorType: String = "NONE"
    private var lastAccelStepTime: Long = 0L

    /**
     * Käynnistää askelten laskemisen.
     * @param onStep Callback-funktio joka kutsutaan jokaisen askeleen kohdalla
     */
    fun startStepCounting(onStep: () -> Unit) {
        if (stepListener != null) {
            Log.d(TAG, "startStepCounting called but listener already registered; resetting")
            stopStepCounting()
        }

        lastStepCounterValue = null

        stepListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_STEP_DETECTOR -> {
                        Log.d(TAG, "STEP_DETECTOR event")
                        onStep()
                    }
                    Sensor.TYPE_STEP_COUNTER -> {
                        val value = event.values.getOrNull(0) ?: return
                        val last = lastStepCounterValue
                        if (last == null) {
                            lastStepCounterValue = value
                            Log.d(TAG, "STEP_COUNTER baseline initialized: $value")
                        } else {
                            val diff = value - last
                            if (diff >= 1f) {
                                val stepsToReport = diff.toInt()
                                Log.d(TAG, "STEP_COUNTER diff=$diff, reporting $stepsToReport steps")
                                repeat(stepsToReport) { onStep() }
                                lastStepCounterValue = value
                            }
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

        registeredSensorType = "NONE"
        var anyRegistered = false
        stepDetectorSensor?.let {
            sensorManager.registerListener(
                stepListener,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            registeredSensorType = "DETECTOR"
            Log.d(TAG, "Registered STEP_DETECTOR sensor")
            anyRegistered = true
        }
        stepCounterSensor?.let {
            sensorManager.registerListener(
                stepListener,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            if (registeredSensorType == "NONE") registeredSensorType = "COUNTER"
            Log.d(TAG, "Registered STEP_COUNTER sensor")
            anyRegistered = true
        }

        if (!anyRegistered && accelSensor != null) {
            accelListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                        val x = event.values.getOrNull(0) ?: 0f
                        val y = event.values.getOrNull(1) ?: 0f
                        val z = event.values.getOrNull(2) ?: 0f
                        val mag = kotlin.math.sqrt(x * x + y * y + z * z)
                        val g = 9.81f
                        val accel = kotlin.math.abs(mag - g)
                        val now = System.currentTimeMillis()
                        val THRESH = 1.2f
                        val COOLDOWN_MS = 350L
                        if (accel > THRESH && now - lastAccelStepTime > COOLDOWN_MS) {
                            lastAccelStepTime = now
                            Log.d(TAG, "ACCEL fallback detected step (accel=$accel)")
                            onStep()
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
            }
            sensorManager.registerListener(
                accelListener,
                accelSensor,
                SensorManager.SENSOR_DELAY_GAME
            )
            registeredSensorType = "ACCEL"
            Log.d(TAG, "Registered ACCELEROMETER fallback sensor")
        }
    }

    /** Pysäyttää askelten laskemisen. */
    fun stopStepCounting() {
        stepListener?.let {
            sensorManager.unregisterListener(it)
            Log.d(TAG, "Unregistered step listener")
        }
        stepListener = null
        lastStepCounterValue = null
        accelListener?.let {
            sensorManager.unregisterListener(it)
            Log.d(TAG, "Unregistered accel listener")
        }
        accelListener = null
        lastAccelStepTime = 0L
        registeredSensorType = "NONE"
    }

    /**
     * Käynnistää gyroskoopin lukemisen.
     * @param onRotation Callback joka saa parametreina x, y, z -kiertonopeudet (rad/s)
     */
    fun startGyroscope(onRotation: (Float, Float, Float) -> Unit) {
        gyroListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    // values[0]=x, values[1]=y, values[2]=z kiertonopeudet rad/s
                    onRotation(event.values[0], event.values[1], event.values[2])
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

        gyroSensor?.let {
            sensorManager.registerListener(
                gyroListener,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    /** Pysäyttää gyroskoopin lukemisen. */
    fun stopGyroscope() {
        gyroListener?.let { sensorManager.unregisterListener(it) }
        gyroListener = null
    }

    /** Pysäyttää kaikki sensorit. */
    fun stopAll() {
        stopStepCounting()
        stopGyroscope()
    }

    /** Tarkistaa tukeeko laite askeltunnistinta. */
    fun isStepSensorAvailable(): Boolean = (stepDetectorSensor != null) || (stepCounterSensor != null)

    /** Palauttaa tyypin rekisteröidystä sensoreista: DETECTOR, COUNTER tai NONE */
    fun getRegisteredSensorType(): String = registeredSensorType

    companion object {
        /** Keskimääräinen askelpituus metreinä matkan laskemiseen */
        const val STEP_LENGTH_METERS = 0.74f
    }
}