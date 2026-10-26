package com.petalo.fotos.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.petalo.fotos.util.Seguridad
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Base de datos local SQLite con dos tablas:
 *  - usuarios: datos de registro (la contraseña se guarda cifrada con SHA-256 + sal)
 *  - fotos: ruta del archivo, comentario y ubicación GPS de cada foto
 */
class BaseDatos(context: Context) :
    SQLiteOpenHelper(context.applicationContext, NOMBRE_BD, null, VERSION) {

    companion object {
        private const val NOMBRE_BD = "petalo.db"
        private const val VERSION = 1

        @Volatile private var instancia: BaseDatos? = null
        fun obtener(context: Context): BaseDatos =
            instancia ?: synchronized(this) {
                instancia ?: BaseDatos(context).also { instancia = it }
            }

        private fun ahora(): String =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE usuarios (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre          TEXT NOT NULL,
                apellidos       TEXT NOT NULL,
                correo          TEXT NOT NULL UNIQUE COLLATE NOCASE,
                contrasena_hash TEXT NOT NULL,
                sal             TEXT NOT NULL,
                fecha_registro  TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE fotos (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                usuario_id  INTEGER NOT NULL,
                ruta        TEXT NOT NULL,
                comentario  TEXT NOT NULL DEFAULT '',
                latitud     REAL,
                longitud    REAL,
                fecha       TEXT NOT NULL,
                FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_fotos_usuario ON fotos(usuario_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS fotos")
        db.execSQL("DROP TABLE IF EXISTS usuarios")
        onCreate(db)
    }

    // ---------------------------------------------------------------- usuarios

    /** Registra un usuario. Devuelve su id, o -1 si el correo ya existe. */
    fun registrarUsuario(nombre: String, apellidos: String, correo: String, contrasena: String): Long {
        val sal = Seguridad.generarSal()
        val valores = ContentValues().apply {
            put("nombre", nombre.trim())
            put("apellidos", apellidos.trim())
            put("correo", correo.trim().lowercase(Locale.ROOT))
            put("contrasena_hash", Seguridad.hash(contrasena, sal))
            put("sal", sal)
            put("fecha_registro", ahora())
        }
        return try {
            writableDatabase.insertOrThrow("usuarios", null, valores)
        } catch (e: SQLiteConstraintException) {
            -1L
        }
    }

    fun existeCorreo(correo: String): Boolean =
        readableDatabase.rawQuery(
            "SELECT 1 FROM usuarios WHERE correo = ? LIMIT 1",
            arrayOf(correo.trim().lowercase(Locale.ROOT))
        ).use { it.moveToFirst() }

    /** Compara correo y contraseña con lo guardado. Devuelve el usuario o null. */
    fun iniciarSesion(correo: String, contrasena: String): Usuario? {
        readableDatabase.rawQuery(
            "SELECT id, nombre, apellidos, correo, contrasena_hash, sal FROM usuarios WHERE correo = ?",
            arrayOf(correo.trim().lowercase(Locale.ROOT))
        ).use { c ->
            if (!c.moveToFirst()) return null
            val hashGuardado = c.getString(4)
            val sal = c.getString(5)
            return if (Seguridad.hash(contrasena, sal) == hashGuardado) {
                Usuario(c.getLong(0), c.getString(1), c.getString(2), c.getString(3))
            } else null
        }
    }

    fun obtenerUsuario(id: Long): Usuario? =
        readableDatabase.rawQuery(
            "SELECT id, nombre, apellidos, correo FROM usuarios WHERE id = ?",
            arrayOf(id.toString())
        ).use { c ->
            if (c.moveToFirst()) Usuario(c.getLong(0), c.getString(1), c.getString(2), c.getString(3)) else null
        }

    // ------------------------------------------------------------------- fotos

    fun guardarFoto(usuarioId: Long, ruta: String, comentario: String, latitud: Double?, longitud: Double?): Long {
        val valores = ContentValues().apply {
            put("usuario_id", usuarioId)
            put("ruta", ruta)
            put("comentario", comentario.trim())
            if (latitud != null) put("latitud", latitud) else putNull("latitud")
            if (longitud != null) put("longitud", longitud) else putNull("longitud")
            put("fecha", ahora())
        }
        return writableDatabase.insert("fotos", null, valores)
    }

    /** Lista de fotos del usuario, de la más reciente a la más antigua. */
    fun listarFotos(usuarioId: Long): List<Foto> {
        val lista = mutableListOf<Foto>()
        readableDatabase.rawQuery(
            "SELECT id, usuario_id, ruta, comentario, latitud, longitud, fecha " +
                "FROM fotos WHERE usuario_id = ? ORDER BY id DESC",
            arrayOf(usuarioId.toString())
        ).use { c -> while (c.moveToNext()) lista.add(c.aFoto()) }
        return lista
    }

    fun obtenerFoto(id: Long): Foto? =
        readableDatabase.rawQuery(
            "SELECT id, usuario_id, ruta, comentario, latitud, longitud, fecha FROM fotos WHERE id = ?",
            arrayOf(id.toString())
        ).use { c -> if (c.moveToFirst()) c.aFoto() else null }

    fun ultimaFoto(usuarioId: Long): Foto? =
        readableDatabase.rawQuery(
            "SELECT id, usuario_id, ruta, comentario, latitud, longitud, fecha " +
                "FROM fotos WHERE usuario_id = ? ORDER BY id DESC LIMIT 1",
            arrayOf(usuarioId.toString())
        ).use { c -> if (c.moveToFirst()) c.aFoto() else null }

    private fun Cursor.aFoto() = Foto(
        id = getLong(0),
        usuarioId = getLong(1),
        ruta = getString(2),
        comentario = getString(3) ?: "",
        latitud = if (isNull(4)) null else getDouble(4),
        longitud = if (isNull(5)) null else getDouble(5),
        fecha = getString(6)
    )
}
