package com.example.gamerhub

// Используется для записи ошибок и отладочных сообщений в Logcat.
import android.util.Log

// Позволяет создавать View из XML-разметки.
import android.view.LayoutInflater

// Базовый класс для элементов интерфейса.
import android.view.View

// Контейнер для элементов интерфейса.
import android.view.ViewGroup

// Виджет для отображения изображений.
import android.widget.ImageView

// Виджет для отображения текста.
import android.widget.TextView

// Используется для показа коротких и длинных сообщений пользователю.
import android.widget.Toast

// Интерфейс жизненного цикла Activity или Fragment.
import androidx.lifecycle.LifecycleOwner

// Позволяет запускать корутины с учётом жизненного цикла компонента.
import androidx.lifecycle.lifecycleScope

// Базовый адаптер для отображения списков и сеток.
import androidx.recyclerview.widget.RecyclerView

// Библиотека для загрузки изображений.
import com.bumptech.glide.Glide

// Используется для запуска корутин.
import kotlinx.coroutines.launch

// Адаптер отвечает за отображение списка игр в RecyclerView.
class GameAdapter(

    // Список игр, которые необходимо отобразить.
    private var games: List<Game>,

    // Репозиторий для взаимодействия с данными игр и Firebase.
    private val repository: GameRepository,

    // Компонент, жизненным циклом которого управляется запуск корутин.
    private val lifecycleOwner: LifecycleOwner,

    // Функция, которая вызывается при нажатии на карточку игры.
    private val onGameClick: (Game) -> Unit

) : RecyclerView.Adapter<GameAdapter.GameViewHolder>() {

    // ViewHolder хранит ссылки на элементы интерфейса одной карточки игры.
    // Благодаря этому не приходится каждый раз искать элементы через findViewById.
    class GameViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        // Изображение игры.
        val image: ImageView = itemView.findViewById(R.id.game_image)

        // Название игры.
        val title: TextView = itemView.findViewById(R.id.game_title)

        // Жанр игры.
        val genre: TextView = itemView.findViewById(R.id.game_genre)

        // Текст с количеством лайков и значком сердечка.
        val likes: TextView = itemView.findViewById(R.id.game_likes)
    }

    // Создаёт новую карточку игры на основе XML-разметки.
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): GameViewHolder {

        // Получаем LayoutInflater и загружаем разметку item_game.xml.
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_game, parent, false)

        // Создаём и возвращаем ViewHolder для загруженной разметки.
        return GameViewHolder(view)
    }

    // Заполняет карточку данными конкретной игры.
    override fun onBindViewHolder(
        holder: GameViewHolder,
        position: Int
    ) {

        // Получаем игру из списка по текущей позиции.
        val game = games[position]

        // Проверяем, является ли изображение ссылкой на интернет-ресурс.
        if (game.image.startsWith("http")) {

            // Если это URL, загружаем изображение через Glide.
            Glide.with(holder.image)
                .load(game.image)
                .into(holder.image)

        } else {

            // Если это не URL, ищем изображение среди ресурсов drawable.
            val imageId = holder.itemView.context.resources.getIdentifier(
                game.image,                         // Имя изображения.
                "drawable",                         // Папка ресурсов.
                holder.itemView.context.packageName // Имя пакета приложения.
            )

            // Проверяем, удалось ли найти изображение.
            if (imageId != 0) {

                // Если изображение найдено, устанавливаем его в ImageView.
                holder.image.setImageResource(imageId)

            } else {

                // Если изображение не найдено, очищаем ImageView.
                holder.image.setImageDrawable(null)
            }
        }

        // Устанавливаем название игры.
        holder.title.text = game.title

        // Устанавливаем жанр игры.
        holder.genre.text = game.genre

        // Обновляем отображение количества лайков и состояния сердечка.
        showLikes(holder, game)

        // Обрабатываем нажатие на область с лайками.
        holder.likes.setOnClickListener {

            // Проверяем, есть ли у игры ID документа в Firestore.
            if (game.firestoreId.isBlank()) {

                // Если ID отсутствует, показываем сообщение об ошибке.
                Toast.makeText(
                    holder.itemView.context,
                    "Не найден ID игры в Firebase",
                    Toast.LENGTH_SHORT
                ).show()

                // Прерываем обработчик нажатия.
                return@setOnClickListener
            }

            // Сохраняем предыдущее состояние лайка,
            // чтобы можно было восстановить его при ошибке.
            val previousLiked = game.isLikedByCurrentUser

            // Сохраняем предыдущее количество лайков.
            val previousCount = game.likesCount

            // Меняем состояние лайка на противоположное.
            // Если лайк был поставлен, снимаем его, и наоборот.
            val newLiked = !previousLiked

            // Оптимистично обновляем интерфейс до ответа Firebase.
            // Пользователь сразу видит результат нажатия.
            game.isLikedByCurrentUser = newLiked

            // Увеличиваем или уменьшаем количество лайков.
            // coerceAtLeast(0) не позволяет количеству стать отрицательным.
            game.likesCount = (previousCount + if (newLiked) 1 else -1)
                .coerceAtLeast(0)

            // Показываем обновлённое количество лайков.
            showLikes(holder, game)

            // Временно блокируем кнопку, чтобы избежать повторных нажатий.
            holder.likes.isEnabled = false

            // Запускаем корутину с учётом жизненного цикла компонента.
            lifecycleOwner.lifecycleScope.launch {

                try {

                    // Отправляем новое состояние лайка в репозиторий.
                    // Репозиторий отвечает за сохранение изменения в Firebase.
                    repository.setGameLike(
                        firestoreId = game.firestoreId,
                        shouldLike = newLiked
                    )

                } catch (e: Exception) {

                    // Если Firebase не сохранил изменение,
                    // восстанавливаем предыдущее состояние лайка.
                    game.isLikedByCurrentUser = previousLiked

                    // Возвращаем прежнее количество лайков.
                    game.likesCount = previousCount

                    // Записываем подробную информацию об ошибке в Logcat.
                    Log.e(
                        "GameAdapter",
                        "Не удалось сохранить лайк игры ${game.title}",
                        e
                    )

                    // Показываем пользователю сообщение об ошибке.
                    Toast.makeText(
                        holder.itemView.context,
                        "Не удалось сохранить лайк: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()

                } finally {

                    // В любом случае снова разрешаем нажимать на кнопку лайка.
                    holder.likes.isEnabled = true

                    // Получаем актуальную позицию карточки в адаптере.
                    // Позиция могла измениться, пока выполнялся запрос Firebase.
                    val currentPosition = holder.bindingAdapterPosition

                    // Проверяем, что карточка всё ещё находится в списке
                    // и на этой позиции находится именно та же игра.
                    if (currentPosition != RecyclerView.NO_POSITION &&
                        games.getOrNull(currentPosition) === game
                    ) {

                        // Обновляем отображение лайков карточки.
                        showLikes(holder, game)
                    }
                }
            }
        }

        // Обрабатываем нажатие на саму карточку игры.
        holder.itemView.setOnClickListener {

            // Передаём выбранную игру во внешнюю функцию.
            // Например, Activity сможет открыть страницу игры.
            onGameClick(game)
        }
    }

    // Обновляет текст с сердечком и количеством лайков.
    private fun showLikes(holder: GameViewHolder, game: Game) {

        // Если текущий пользователь поставил лайк,
        // отображаем красное сердечко.
        // В противном случае отображаем пустое сердечко.
        val heart = if (game.isLikedByCurrentUser) "❤️" else "🤍"

        // Устанавливаем сердечко и количество лайков в TextView.
        holder.likes.text = "$heart ${game.likesCount}"
    }

    // Возвращает количество элементов в списке игр.
    // RecyclerView использует это значение, чтобы понять,
    // сколько карточек необходимо отобразить.
    override fun getItemCount(): Int = games.size

    // Обновляет список игр, который использует адаптер.
    fun updateGames(newGames: List<Game>) {

        // Заменяем старый список игр новым.
        games = newGames

        // Сообщаем RecyclerView, что данные изменились.
        // После этого список будет перерисован.
        notifyDataSetChanged()
    }
}