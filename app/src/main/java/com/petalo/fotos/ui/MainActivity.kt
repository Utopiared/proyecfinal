package com.petalo.fotos.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.petalo.fotos.R
import com.petalo.fotos.data.BaseDatos
import com.petalo.fotos.data.Foto
import com.petalo.fotos.databinding.ActivityMainBinding
import com.petalo.fotos.util.Imagenes
import com.petalo.fotos.util.Sesion
import com.petalo.fotos.util.Ubicacion
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var sesion: Sesion
    private val db by lazy { BaseDatos.obtener(this) }

    // Estado de la captura en curso (se conserva si Android recrea la pantalla)
    private var archivoPendiente: File? = null
    private var ubicacion: Location? = null
    private var cancelarGps: CancellationSignal? = null
    private var tvGpsDialogo: TextView? = null

    private val permisos = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        val camara = r[Manifest.permission.CAMERA] == true || tienePermisoCamara()
        if (!camara) {
            Snackbar.make(b.root, R.string.permiso_camara, Snackbar.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        if (!Ubicacion.tienePermiso(this)) {
            Snackbar.make(b.root, R.string.permiso_ubicacion, Snackbar.LENGTH_LONG).show()
        }
        abrirCamara()
    }

    private val tomarFoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { exito ->
        val archivo = archivoPendiente
        if (exito && archivo != null && archivo.length() > 0) {
            mostrarEnPantalla(archivo.absolutePath, null)
            if (ubicacion == null) pedirUbicacion()
            pedirComentario(archivo)
        } else {
            archivo?.delete()
            archivoPendiente = null
            cancelarGps?.cancel()
            Snackbar.make(b.root, R.string.foto_cancelada, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sesion = Sesion(this)
        val usuario = db.obtenerUsuario(sesion.usuarioId)
        if (usuario == null) { cerrarSesion(); return }

        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.tvSaludo.text = getString(R.string.saludo, usuario.nombre)

        savedInstanceState?.let { s ->
            s.getString(CLAVE_ARCHIVO)?.let { archivoPendiente = File(it) }
            if (s.containsKey(CLAVE_LAT)) {
                ubicacion = Location("guardada").apply {
                    latitude = s.getDouble(CLAVE_LAT); longitude = s.getDouble(CLAVE_LON)
                }
            }
        }

        b.btnTomarFoto.setOnClickListener { solicitarPermisosYCapturar() }
        b.btnVerFotos.setOnClickListener { startActivity(Intent(this, ListaFotosActivity::class.java)) }
        b.btnSalir.setOnClickListener { cerrarSesion() }
        b.cardFoto.setOnClickListener {
            db.ultimaFoto(sesion.usuarioId)?.let { abrirDetalle(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        if (archivoPendiente == null) refrescarVistaPrevia()
    }

    /** Muestra en la pantalla principal la última foto guardada. */
    private fun refrescarVistaPrevia() {
        val ultima = db.ultimaFoto(sesion.usuarioId)
        if (ultima != null) mostrarEnPantalla(ultima.ruta, ultima.comentario) else mostrarVacio()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        archivoPendiente?.let { outState.putString(CLAVE_ARCHIVO, it.absolutePath) }
        ubicacion?.let { outState.putDouble(CLAVE_LAT, it.latitude); outState.putDouble(CLAVE_LON, it.longitude) }
    }

    override fun onDestroy() {
        cancelarGps?.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------ captura

    private fun tienePermisoCamara() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun solicitarPermisosYCapturar() {
        if (tienePermisoCamara() && Ubicacion.tienePermiso(this)) {
            abrirCamara()
        } else {
            permisos.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    /** Crea el archivo dentro de la carpeta de la app y abre la cámara. */
    private fun abrirCamara() {
        val carpeta = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Petalo").apply { mkdirs() }
        val nombre = "PETALO_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        val archivo = File(carpeta, nombre)
        archivoPendiente = archivo
        ubicacion = null
        pedirUbicacion()   // el GPS empieza a buscar mientras se toma la foto

        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", archivo)
        tomarFoto.launch(uri)
    }

    private fun pedirUbicacion() {
        cancelarGps?.cancel()
        cancelarGps = Ubicacion.obtener(this) { loc ->
            if (loc != null) ubicacion = loc
            actualizarTextoGps()
        }
    }

    private fun actualizarTextoGps() {
        val tv = tvGpsDialogo ?: return
        val loc = ubicacion
        tv.text = if (loc != null) {
            String.format(Locale.US, "%.5f, %.5f", loc.latitude, loc.longitude)
        } else if (Ubicacion.tienePermiso(this)) {
            getString(R.string.buscando_ubicacion)
        } else {
            getString(R.string.sin_ubicacion)
        }
    }

    /** Diálogo para escribir el comentario; al guardar, registra todo en la BD. */
    private fun pedirComentario(archivo: File) {
        val vista = layoutInflater.inflate(R.layout.dialog_comentario, null)
        val et = vista.findViewById<TextInputEditText>(R.id.etComentario)
        Imagenes.cargar(archivo.absolutePath, vista.findViewById<ImageView>(R.id.ivMiniatura), 500)
        tvGpsDialogo = vista.findViewById(R.id.tvGps)
        actualizarTextoGps()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.titulo_comentario)
            .setIcon(R.drawable.ic_flor)
            .setView(vista)
            .setCancelable(false)
            .setPositiveButton(R.string.guardar) { _, _ ->
                guardar(archivo, et.text?.toString().orEmpty())
            }
            .setNegativeButton(R.string.cancelar) { _, _ ->
                archivo.delete()
                terminarCaptura()
                refrescarVistaPrevia()
                Snackbar.make(b.root, R.string.foto_cancelada, Snackbar.LENGTH_SHORT).show()
            }
            .create()
            .also { it.setOnDismissListener { tvGpsDialogo = null } }
            .show()
    }

    private fun guardar(archivo: File, comentario: String) {
        val loc = ubicacion
        db.guardarFoto(sesion.usuarioId, archivo.absolutePath, comentario, loc?.latitude, loc?.longitude)
        terminarCaptura()
        mostrarEnPantalla(archivo.absolutePath, comentario)
        Snackbar.make(b.root, R.string.foto_guardada, Snackbar.LENGTH_SHORT).show()
    }

    private fun terminarCaptura() {
        cancelarGps?.cancel()
        cancelarGps = null
        archivoPendiente = null
        ubicacion = null
    }

    // ------------------------------------------------------------ pantalla

    private fun mostrarEnPantalla(ruta: String, comentario: String?) {
        b.vacio.visibility = View.GONE
        b.ivFoto.visibility = View.VISIBLE
        Imagenes.cargar(ruta, b.ivFoto, 1200)
        if (comentario.isNullOrBlank()) {
            b.tvComentarioFoto.visibility = View.GONE
        } else {
            b.tvComentarioFoto.text = "♡ $comentario"
            b.tvComentarioFoto.visibility = View.VISIBLE
        }
    }

    private fun mostrarVacio() {
        b.vacio.visibility = View.VISIBLE
        b.ivFoto.visibility = View.GONE
        b.tvComentarioFoto.visibility = View.GONE
    }

    private fun abrirDetalle(foto: Foto) {
        startActivity(Intent(this, DetalleFotoActivity::class.java).putExtra(DetalleFotoActivity.EXTRA_ID, foto.id))
    }

    private fun cerrarSesion() {
        sesion.cerrar()
        startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    private companion object {
        const val CLAVE_ARCHIVO = "archivo"
        const val CLAVE_LAT = "lat"
        const val CLAVE_LON = "lon"
    }
}
