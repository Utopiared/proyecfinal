package com.petalo.fotos.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.petalo.fotos.R
import com.petalo.fotos.data.BaseDatos
import com.petalo.fotos.databinding.ActivityLoginBinding
import com.petalo.fotos.util.Sesion

class LoginActivity : AppCompatActivity() {

    private lateinit var b: ActivityLoginBinding
    private lateinit var sesion: Sesion

    // Al volver del registro, rellena el correo recién creado
    private val registro = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringExtra(RegistroActivity.EXTRA_CORREO)?.let { b.etCorreo.setText(it) }
            b.etContrasena.requestFocus()
            Snackbar.make(b.root, R.string.registro_ok, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sesion = Sesion(this)

        // Si ya había iniciado sesión, entra directo
        if (sesion.activa && BaseDatos.obtener(this).obtenerUsuario(sesion.usuarioId) != null) {
            irAPrincipal(); return
        }

        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnEntrar.setOnClickListener { iniciarSesion() }
        b.etContrasena.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_DONE) { iniciarSesion(); true } else false
        }
        b.tvIrRegistro.setOnClickListener {
            registro.launch(Intent(this, RegistroActivity::class.java))
        }
    }

    private fun iniciarSesion() {
        val correo = b.etCorreo.text?.toString()?.trim().orEmpty()
        val contrasena = b.etContrasena.text?.toString().orEmpty()

        b.tilCorreo.error = null
        b.tilContrasena.error = null
        var ok = true
        if (correo.isEmpty()) { b.tilCorreo.error = getString(R.string.err_campo_vacio); ok = false }
        else if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) { b.tilCorreo.error = getString(R.string.err_correo); ok = false }
        if (contrasena.isEmpty()) { b.tilContrasena.error = getString(R.string.err_campo_vacio); ok = false }
        if (!ok) return

        // Compara correo y contraseña contra la base de datos local
        val usuario = BaseDatos.obtener(this).iniciarSesion(correo, contrasena)
        if (usuario == null) {
            b.tilContrasena.error = getString(R.string.err_login)
            return
        }
        sesion.usuarioId = usuario.id
        irAPrincipal()
    }

    private fun irAPrincipal() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
