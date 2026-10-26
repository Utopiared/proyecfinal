package com.petalo.fotos.util

import java.security.MessageDigest
import java.security.SecureRandom

/** Nunca se guarda la contraseña en texto plano: se guarda SHA-256(sal + contraseña). */
object Seguridad {

    fun generarSal(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.aHex()
    }

    fun hash(contrasena: String, sal: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest((sal + contrasena).toByteArray(Charsets.UTF_8)).aHex()
    }

    private fun ByteArray.aHex(): String = joinToString("") { "%02x".format(it) }
}
