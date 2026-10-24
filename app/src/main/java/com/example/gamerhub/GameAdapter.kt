
package com.example.gamerhub

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

class GameAdapter(
    private var games: List<Game>,
    private val repository: GameRepository,
    private val lifecycleOwner: LifecycleOwner,
    private val onGameClick: (Game) -> Unit
) : RecyclerView.Adapter<GameAdapter.GameViewHolder>() {

    class GameViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

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
        showLikes(holder, game)

        holder.likes.setOnClickListener {
            if (game.firestoreId.isBlank()) {
                Toast.makeText(
                    holder.itemView.context,
                    "Не найден ID игры в Firebase",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            // Запоминаем состояние, чтобы восстановить его при ошибке.
            val previousLiked = game.isLikedByCurrentUser
            val previousCount = game.likesCount
            val newLiked = !previousLiked

            // Оптимистично обновляем интерфейс.
            game.isLikedByCurrentUser = newLiked
            game.likesCount = (previousCount + if (newLiked) 1 else -1)
                .coerceAtLeast(0)

            showLikes(holder, game)
            holder.likes.isEnabled = false

            lifecycleOwner.lifecycleScope.launch {
                try {
                    repository.setGameLike(
                        firestoreId = game.firestoreId,
                        shouldLike = newLiked
                    )
                } catch (e: Exception) {
                    // Firebase не сохранил изменение — возвращаем прежние данные.
                    game.isLikedByCurrentUser = previousLiked
                    game.likesCount = previousCount

                    Log.e(
                        "GameAdapter",
                        "Не удалось сохранить лайк игры ${game.title}",
                        e
                    )

                    Toast.makeText(
                        holder.itemView.context,
                        "Не удалось сохранить лайк: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                } finally {
                    holder.likes.isEnabled = true

                    // Обновляем карточку, если она всё ещё отображается.
                    val currentPosition = holder.bindingAdapterPosition
                    if (currentPosition != RecyclerView.NO_POSITION &&
                        games.getOrNull(currentPosition) === game
                    ) {
                        showLikes(holder, game)
                    }
                }
            }
        }

        holder.itemView.setOnClickListener {
            onGameClick(game)
        }
    }

    private fun showLikes(holder: GameViewHolder, game: Game) {
        val heart = if (game.isLikedByCurrentUser) "❤️" else "🤍"
        holder.likes.text = "$heart ${game.likesCount}"
    }

    override fun getItemCount(): Int = games.size

    fun updateGames(newGames: List<Game>) {
        games = newGames
        notifyDataSetChanged()
    }
}
