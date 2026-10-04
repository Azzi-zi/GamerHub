package com.example.gamerhub

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class GameAdapter(
    private val games: List<Game>
) : RecyclerView.Adapter<GameAdapter.GameViewHolder>() {

    class GameViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val image: ImageView = itemView.findViewById(R.id.game_image)
        val title: TextView = itemView.findViewById(R.id.game_title)
        val genre: TextView = itemView.findViewById(R.id.game_genre)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): GameViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_game, parent, false)

        return GameViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: GameViewHolder,
        position: Int
    ) {

        val game = games[position]

        val imageId = holder.itemView.context.resources.getIdentifier(
            game.image,
            "drawable",
            holder.itemView.context.packageName
        )

        holder.image.setImageResource(imageId)

        holder.title.text = game.title
        holder.genre.text = game.genre
    }

    override fun getItemCount(): Int {
        return games.size
    }
}