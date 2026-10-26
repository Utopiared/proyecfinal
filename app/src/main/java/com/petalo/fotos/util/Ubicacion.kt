package com.petalo.fotos.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import android.os.CancellationSignal

/**
 * Obtiene la ubicación GPS actual con el LocationManager de Android
 * (no requiere Google Play Services).
 */
object Ubicacion {

    fun tienePermiso(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Pide una lectura fresca de GPS (o red si el GPS está apagado).
     * Mientras llega, entrega la última ubicación conocida si existe.
     * El callback siempre se llama en el hilo principal; puede recibir null.
     */
    @SuppressLint("MissingPermission")
    fun obtener(context: Context, alRecibir: (Location?) -> Unit): CancellationSignal? {
        if (!tienePermiso(context)) { alRecibir(null); return null }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val proveedor = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }

        val ultima = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

        if (proveedor == null) { alRecibir(ultima); return null }
        if (ultima != null) alRecibir(ultima)   // valor provisional

        val cancelar = CancellationSignal()
        LocationManagerCompat.getCurrentLocation(
            lm, proveedor, cancelar, ContextCompat.getMainExecutor(context)
        ) { nueva -> if (nueva != null) alRecibir(nueva) else if (ultima == null) alRecibir(null) }
        return cancelar
    }
}
