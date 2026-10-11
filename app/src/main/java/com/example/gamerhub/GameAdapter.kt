
package com.example.gamerhub

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GameAdapter(
    private var games: List<Game>,
    private val repository: GameRepository,
    private val lifecycleOwner: LifecycleOwner,
    private val onGameClick: (Game) -> Unit
) : RecyclerView.Adapter<GameAdapter.GameViewHolder>() {

    class GameViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.game_image)
        val title: TextView = itemView.findViewById(R.id.game_title)
        val genre: TextView = itemView.findViewById(R.id.game_genre)
        val likes: TextView = itemView.findViewById(R.id.game_likes)
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

            if (imageId != 0) {
                holder.image.setImageResource(imageId)
            } else {
                holder.image.setImageDrawable(null)
            }
        }

        holder.title.text = game.title
        holder.genre.text = game.genre

        // Показываем сохранённое состояние лайка
        holder.likes.text = if (game.liked) "❤️ 1" else "🤍 0"

        holder.likes.setOnClickListener {
            val newLikedState = !game.liked

            // Сразу обновляем интерфейс
            game.liked = newLikedState
            holder.likes.text = if (newLikedState) "❤️ 1" else "🤍 0"

            // Сохраняем состояние в Room
            lifecycleOwner.lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        repository.updateLiked(game.id, newLikedState)
                    }
                } catch (e: Exception) {
                    // Если сохранение не удалось, возвращаем прежнее состояние
                    game.liked = !newLikedState
                    holder.likes.text = if (game.liked) "❤️ 1" else "🤍 0"
                }
            }
        }

        holder.itemView.setOnClickListener {
            onGameClick(game)
        }
    }

    override fun getItemCount(): Int = games.size

    fun updateGames(newGames: List<Game>) {
        games = newGames
        notifyDataSetChanged()
    }
}
