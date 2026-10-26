package com.petalo.fotos.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import com.petalo.fotos.R
import com.petalo.fotos.data.BaseDatos
import com.petalo.fotos.databinding.ActivityRegistroBinding

class RegistroActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CORREO = "correo"
    }

    private lateinit var b: ActivityRegistroBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRegistroBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnAtras.setOnClickListener { finish() }
        b.btnTengoCuenta.setOnClickListener { finish() }
        b.btnRegistrar.setOnClickListener { registrar() }
    }

    private fun registrar() {
        val nombre = b.etNombre.text?.toString()?.trim().orEmpty()
        val apellidos = b.etApellidos.text?.toString()?.trim().orEmpty()
        val correo = b.etCorreo.text?.toString()?.trim().orEmpty()
        val contrasena = b.etContrasena.text?.toString().orEmpty()
        val confirmar = b.etConfirmar.text?.toString().orEmpty()

        listOf(b.tilNombre, b.tilApellidos, b.tilCorreo, b.tilConfirmar).forEach { it.error = null }
        b.tilContrasena.error = null

        var ok = true
        val vacio = getString(R.string.err_campo_vacio)
        if (nombre.isEmpty()) { b.tilNombre.error = vacio; ok = false }
        if (apellidos.isEmpty()) { b.tilApellidos.error = vacio; ok = false }
        if (correo.isEmpty()) { b.tilCorreo.error = vacio; ok = false }
        else if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            b.tilCorreo.error = getString(R.string.err_correo); ok = false
        }
        if (contrasena.length < 6) { b.tilContrasena.error = getString(R.string.err_contrasena_corta); ok = false }
        if (confirmar != contrasena) { b.tilConfirmar.error = getString(R.string.err_no_coinciden); ok = false }
        if (!ok) return

        val db = BaseDatos.obtener(this)
        if (db.existeCorreo(correo)) {
            b.tilCorreo.error = getString(R.string.err_correo_existe)
            return
        }

        // Guarda el usuario en la base de datos local
        val id = db.registrarUsuario(nombre, apellidos, correo, contrasena)
        if (id == -1L) {
            b.tilCorreo.error = getString(R.string.err_correo_existe)
            return
        }

        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_CORREO, correo))
        finish()
    }
}
