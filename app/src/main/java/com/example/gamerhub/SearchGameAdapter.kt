package com.example.gamerhub

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

// Адаптер отвечает за отображение списка найденных игр в RecyclerView.
//
// games — список игр, который нужно показать.
// onGameClick — функция, вызываемая при нажатии на игру.
class SearchGameAdapter(
    private var games: List<Game>,
    private val onGameClick: (Game) -> Unit
) : RecyclerView.Adapter<SearchGameAdapter.SearchGameViewHolder>() {

    // ViewHolder хранит ссылки на элементы интерфейса одной карточки игры.
    // Благодаря этому не приходится каждый раз искать элементы через findViewById.
    class SearchGameViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        // Изображение игры.
        val image: ImageView = itemView.findViewById(R.id.game_image)

        // Название игры.
        val title: TextView = itemView.findViewById(R.id.game_title)

        // Жанр игры.
        val genre: TextView = itemView.findViewById(R.id.game_genre)
    }

    // Создаёт новую карточку игры, когда RecyclerView запрашивает её.
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SearchGameViewHolder {

        // Загружаем XML-разметку одной карточки игры.
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_game, parent, false)

        return SearchGameViewHolder(view)
    }

    // Заполняет карточку данными конкретной игры.
    override fun onBindViewHolder(
        holder: SearchGameViewHolder,
        position: Int
    ) {

        // Получаем игру по её позиции в списке.
        val game = games[position]

        // Проверяем, является ли путь к изображению интернет-ссылкой.
        if (game.image.startsWith("http")) {

            // Если это ссылка, загружаем изображение через Glide.
            Glide.with(holder.image)
                .load(game.image)
                .into(holder.image)

        } else {

            // Если это не ссылка, считаем, что изображение находится
            // среди локальных ресурсов проекта в папке res/drawable.

            // getIdentifier() ищет ресурс по его имени.
            val imageId = holder.itemView.context.resources.getIdentifier(
                game.image,
                "drawable",
                holder.itemView.context.packageName
            )

            // Если ресурс найден, устанавливаем его.
            // Если нет, устанавливаем стандартное изображение приложения.
            if (imageId != 0) {
                holder.image.setImageResource(imageId)
            } else {
                holder.image.setImageResource(
                    android.R.drawable.ic_menu_report_image
                )
            }
        }

        // Устанавливаем название игры в TextView.
        holder.title.text = game.title

        // Устанавливаем жанр игры.
        holder.genre.text = game.genre

        // Назначаем обработчик нажатия на карточку.
        // При нажатии передаём выбранную игру в onGameClick.
        holder.itemView.setOnClickListener {
            onGameClick(game)
        }
    }

    // Сообщает RecyclerView, сколько элементов находится в списке.
    override fun getItemCount(): Int {
        return games.size
    }

    // Обновляет список игр, например после выполнения поиска.
    fun updateGames(newGames: List<Game>) {

        // Заменяем старый список новым.
        games = newGames

        // Сообщаем RecyclerView, что данные изменились.
        // Он заново отобразит карточки.
        notifyDataSetChanged()
    }
}