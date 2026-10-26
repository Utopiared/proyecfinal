package com.petalo.fotos.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.petalo.fotos.R
import com.petalo.fotos.data.BaseDatos
import com.petalo.fotos.data.Foto
import com.petalo.fotos.databinding.ActivityListaFotosBinding
import com.petalo.fotos.databinding.ItemFotoBinding
import com.petalo.fotos.util.Imagenes
import com.petalo.fotos.util.Sesion

/** Consulta en la base de datos las fotos de la usuaria y las muestra en cuadrícula. */
class ListaFotosActivity : AppCompatActivity() {

    private lateinit var b: ActivityListaFotosBinding
    private val adaptador = FotosAdapter { foto ->
        startActivity(Intent(this, DetalleFotoActivity::class.java).putExtra(DetalleFotoActivity.EXTRA_ID, foto.id))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityListaFotosBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnAtras.setOnClickListener { finish() }
        b.rvFotos.layoutManager = GridLayoutManager(this, 2)
        b.rvFotos.adapter = adaptador
    }

    override fun onResume() {
        super.onResume()
        val fotos = BaseDatos.obtener(this).listarFotos(Sesion(this).usuarioId)
        adaptador.actualizar(fotos)
        b.tvTotal.text = fotos.size.toString()
        b.vacio.visibility = if (fotos.isEmpty()) View.VISIBLE else View.GONE
        b.rvFotos.visibility = if (fotos.isEmpty()) View.GONE else View.VISIBLE
    }
}

class FotosAdapter(private val alTocar: (Foto) -> Unit) : RecyclerView.Adapter<FotosAdapter.VH>() {

    private var fotos: List<Foto> = emptyList()

    fun actualizar(nuevas: List<Foto>) {
        fotos = nuevas
        notifyDataSetChanged()
    }

    class VH(val b: ItemFotoBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemFotoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = fotos.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val f = fotos[pos]
        val ctx = h.itemView.context
        Imagenes.cargar(f.ruta, h.b.ivFoto, 400)
        h.b.tvComentario.text = f.comentario.ifBlank { ctx.getString(R.string.sin_comentario) }
        h.b.tvFecha.text = f.fecha.take(16)
        h.itemView.setOnClickListener { alTocar(f) }
    }
}
