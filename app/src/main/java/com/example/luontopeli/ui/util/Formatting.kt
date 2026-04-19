package com.example.luontopeli.ui.util

import java.util.concurrent.TimeUnit

fun formatDistance(distanceMeters: Float): String {
    return if (distanceMeters >= 1000f) {
        String.format("%.2f km", distanceMeters / 1000f)
    } else {
        String.format("%d m", distanceMeters.toInt())
    }
}

fun formatDuration(startTimeMillis: Long): String {
    val duration = System.currentTimeMillis() - startTimeMillis
    val hours = TimeUnit.MILLISECONDS.toHours(duration)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(duration) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(duration) % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%02d:%02d", minutes, seconds)
}

fun Long.toFormattedDate(): String {
    val sdf = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(this))
}
