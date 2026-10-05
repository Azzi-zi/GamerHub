package com.example.gamerhub

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class SearchGameAdapter(
    private var games: List<Game>,
    private val onGameClick: (Game) -> Unit
) : RecyclerView.Adapter<SearchGameAdapter.SearchGameViewHolder>() {

    class SearchGameViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val image: ImageView = itemView.findViewById(R.id.game_image)
        val title: TextView = itemView.findViewById(R.id.game_title)
        val genre: TextView = itemView.findViewById(R.id.game_genre)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SearchGameViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_game, parent, false)

        return SearchGameViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: SearchGameViewHolder,
        position: Int
    ) {

        val game = games[position]

        if (game.image.startsWith("http")) {

            Glide.with(holder.image)
                .load(game.image)
                .into(holder.image)

        } else {

            val imageId = holder.itemView.context.resources.getIdentifier(
                game.image,
                "drawable",
                holder.itemView.context.packageName
            )

            holder.image.setImageResource(imageId)
        }

        holder.title.text = game.title
        holder.genre.text = game.genre

        holder.itemView.setOnClickListener {
            onGameClick(game)
        }
    }

    override fun getItemCount(): Int {
        return games.size
    }

    fun updateGames(newGames: List<Game>) {
        games = newGames
        notifyDataSetChanged()
    }
}