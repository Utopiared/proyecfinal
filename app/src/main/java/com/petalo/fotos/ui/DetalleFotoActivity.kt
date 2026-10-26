package com.petalo.fotos.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.petalo.fotos.R
import com.petalo.fotos.data.BaseDatos
import com.petalo.fotos.databinding.ActivityDetalleFotoBinding
import com.petalo.fotos.util.Imagenes
import com.petalo.fotos.util.Sesion
import java.util.Locale

/** Muestra la foto seleccionada con el comentario y la ubicación GPS registrados. */
class DetalleFotoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "foto_id"
    }

    private lateinit var b: ActivityDetalleFotoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDetalleFotoBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnAtras.setOnClickListener { finish() }

        val foto = BaseDatos.obtener(this).obtenerFoto(intent.getLongExtra(EXTRA_ID, -1))
        // Solo la dueña puede ver su foto
        if (foto == null || foto.usuarioId != Sesion(this).usuarioId) { finish(); return }

        Imagenes.cargar(foto.ruta, b.ivFoto, 1400)
        b.tvComentario.text = foto.comentario.ifBlank { getString(R.string.sin_comentario) }
        b.tvFecha.text = foto.fecha

        val lat = foto.latitud
        val lon = foto.longitud
        if (lat != null && lon != null) {
            b.tvUbicacion.text = String.format(Locale.US, "Lat: %.6f\nLon: %.6f", lat, lon)
            b.btnMapa.visibility = View.VISIBLE
            b.btnMapa.setOnClickListener {
                val geo = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(getString(R.string.app_name))})")
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, geo))
                } catch (e: ActivityNotFoundException) {
                    Snackbar.make(b.root, R.string.sin_app_mapas, Snackbar.LENGTH_SHORT).show()
                }
            }
        } else {
            b.tvUbicacion.text = getString(R.string.sin_ubicacion)
            b.btnMapa.visibility = View.GONE
        }
    }
}
