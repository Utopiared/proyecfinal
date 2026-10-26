package com.petalo.fotos.data

data class Usuario(
    val id: Long,
    val nombre: String,
    val apellidos: String,
    val correo: String
)

data class Foto(
    val id: Long,
    val usuarioId: Long,
    val ruta: String,          // ruta absoluta del archivo dentro de la carpeta de la app
    val comentario: String,
    val latitud: Double?,      // null si no se pudo obtener el GPS
    val longitud: Double?,
    val fecha: String
)
