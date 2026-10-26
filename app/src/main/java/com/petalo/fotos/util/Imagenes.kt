package com.petalo.fotos.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.concurrent.Executors

/**
 * Carga fotos reducidas (para no llenar la memoria) y corrige la rotación
 * que la cámara guarda en los datos EXIF.
 */
object Imagenes {

    private val hilo = Executors.newFixedThreadPool(2)
    private val principal = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    /** Carga en segundo plano y la coloca en el ImageView (seguro para listas recicladas). */
    fun cargar(ruta: String, destino: ImageView, ladoMax: Int = 600) {
        val clave = "$ruta@$ladoMax"
        destino.tag = clave
        cache.get(clave)?.let { destino.setImageBitmap(it); return }
        destino.setImageDrawable(null)
        hilo.execute {
            val bmp = decodificar(ruta, ladoMax) ?: return@execute
            cache.put(clave, bmp)
            principal.post { if (destino.tag == clave) destino.setImageBitmap(bmp) }
        }
    }

    fun decodificar(ruta: String, ladoMax: Int): Bitmap? {
        if (!File(ruta).exists()) return null
        val opciones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(ruta, opciones)
        var muestra = 1
        while (opciones.outWidth / (muestra * 2) >= ladoMax && opciones.outHeight / (muestra * 2) >= ladoMax) {
            muestra *= 2
        }
        val bmp = BitmapFactory.decodeFile(ruta, BitmapFactory.Options().apply { inSampleSize = muestra })
            ?: return null
        val grados = try {
            when (ExifInterface(ruta).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (e: Exception) { 0f }
        if (grados == 0f) return bmp
        val m = Matrix().apply { postRotate(grados) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }
}
