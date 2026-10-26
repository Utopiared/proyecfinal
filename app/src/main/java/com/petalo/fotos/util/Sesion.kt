package com.petalo.fotos.util

import android.content.Context

/** Recuerda qué usuaria tiene la sesión abierta. */
class Sesion(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("sesion_petalo", Context.MODE_PRIVATE)

    var usuarioId: Long
        get() = prefs.getLong(CLAVE_ID, -1L)
        set(valor) = prefs.edit().putLong(CLAVE_ID, valor).apply()

    val activa: Boolean get() = usuarioId != -1L

    fun cerrar() = prefs.edit().remove(CLAVE_ID).apply()

    private companion object {
        const val CLAVE_ID = "usuario_id"
    }
}
