package com.example.gamerhub

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Класс отвечает за отображение и обработку уведомлений.
//
// Он получает данные из Firestore, создаёт карточки уведомлений,
// загружает аватары пользователей и обрабатывает нажатия.
class NotificationsManager(

    // Функция, которая вызывается при нажатии на уведомление о публикации.
    // В качестве параметра передаётся ID публикации.
    private val onNotificationClick: (String) -> Unit = {},

    // Функция, которая вызывается при нажатии на уведомление о подписке.
    // В качестве параметра передаётся UID пользователя.
    private val onProfileClick: (String) -> Unit = {}

) {

    // Получаем экземпляр Firestore для работы с базой данных.
    private val db = FirebaseFirestore.getInstance()

    // Слушатель изменений коллекции уведомлений.
    //
    // Благодаря ListenerRegistration мы можем остановить прослушивание,
    // когда оно больше не требуется.
    private var listener: ListenerRegistration? = null

    // Цвет фона карточек уведомлений.
    private val cardColor = Color.rgb(26, 26, 32)

    // Основной цвет текста.
    private val primaryColor = Color.WHITE

    // Дополнительный цвет текста.
    private val secondaryColor = Color.rgb(160, 160, 170)

    // Фиолетовый цвет, используемый для выделения заголовков публикаций.
    private val purpleColor = Color.rgb(139, 92, 246)


    // ---------------------------------------------------------
    // НАСТРОЙКА ЭКРАНА УВЕДОМЛЕНИЙ
    // ---------------------------------------------------------

    // Метод вызывается при открытии экрана уведомлений.
    fun setup(view: View) {

        // Находим контейнер, в который будут добавляться карточки.
        val container = view.findViewById<LinearLayout>(
            R.id.notificationsContainer
        ) ?: return

        // Удаляем старые элементы из контейнера.
        container.removeAllViews()

        // Устанавливаем тёмный фон экрана.
        container.setBackgroundColor(Color.rgb(18, 18, 22))

        // Получаем UID текущего авторизованного пользователя.
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        // Если пользователь не авторизован,
        // показываем соответствующее сообщение.
        if (userId == null) {
            showEmpty(
                container,
                "Войди в аккаунт, чтобы увидеть уведомления"
            )
            return
        }

        // Пока данные загружаются, показываем сообщение.
        showEmpty(container, "Загрузка уведомлений...")

        // Если ранее уже существовал слушатель,
        // отключаем его, чтобы не создавать несколько одновременно.
        listener?.remove()

        // Подключаемся к коллекции уведомлений текущего пользователя.
        //
        // Структура Firestore:
        // users -> UID пользователя -> notifications.
        //
        // addSnapshotListener следит за изменениями в реальном времени.
        listener = db.collection("users")
            .document(userId)
            .collection("notifications")
            .addSnapshotListener { snapshot, error ->

                // Проверяем, произошла ли ошибка получения данных.
                if (error != null) {

                    showEmpty(
                        container,
                        "Не удалось загрузить уведомления. Проверь правила Firestore."
                    )

                    return@addSnapshotListener
                }

                // Очищаем контейнер перед повторным отображением данных.
                container.removeAllViews()

                // Получаем документы из коллекции уведомлений.
                //
                // Сортируем их по дате создания:
                // самые новые уведомления будут отображаться первыми.
                val documents = snapshot?.documents
                    ?.sortedByDescending {
                        it.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                    }
                    .orEmpty()

                // Если уведомлений нет, показываем пустое состояние.
                if (documents.isEmpty()) {

                    showEmpty(
                        container,
                        "Пока нет уведомлений.\n\nЗдесь появятся подписки, лайки и комментарии."
                    )

                    return@addSnapshotListener
                }

                // Перебираем все полученные уведомления.
                documents.forEach { document ->

                    // Получаем тип уведомления.
                    // Если поле отсутствует, считаем уведомление лайком.
                    val type = document.getString("type") ?: "like"

                    // UID пользователя, который совершил действие.
                    val actorId = document.getString("actorId").orEmpty()

                    // Имя пользователя.
                    //
                    // Если имя отсутствует или пустое,
                    // используем стандартное название.
                    val actorName = document.getString("actorName")
                        ?.takeIf { it.isNotBlank() }
                        ?: "Игрок GamerHub"

                    // Получаем заголовок публикации.
                    val postTitle = document.getString("postTitle").orEmpty()

                    // Получаем ID публикации.
                    val postId = document.getString("postId").orEmpty()

                    // Проверяем, прочитано ли уведомление.
                    // По умолчанию считаем его непрочитанным.
                    val isRead = document.getBoolean("read") ?: false

                    // Получаем дату создания уведомления.
                    val createdAt = document.getTimestamp("createdAt")?.toDate()

                    // Формируем текст уведомления в зависимости от его типа.
                    val message = when (type) {

                        // Уведомление о новой подписке.
                        "follow" -> "$actorName подписался(ась) на тебя"

                        // Уведомление о комментарии.
                        "comment" -> "$actorName прокомментировал(а) твою публикацию"

                        // По умолчанию отображаем уведомление о лайке.
                        else -> "$actorName поставил(а) лайк твоей публикации"
                    }

                    // Создаём карточку уведомления и добавляем её на экран.
                    addNotification(
                        container = container,
                        type = type,
                        actorId = actorId,
                        actorName = actorName,
                        message = message,
                        postTitle = postTitle,
                        createdAt = createdAt,
                        isRead = isRead,
                        postId = postId,
                        notificationId = document.id,
                        userId = userId
                    )
                }
            }
    }


    // ---------------------------------------------------------
    // СОЗДАНИЕ КАРТОЧКИ УВЕДОМЛЕНИЯ
    // ---------------------------------------------------------

    // Метод создаёт одну карточку уведомления.
    private fun addNotification(
        container: LinearLayout,
        type: String,
        actorId: String,
        actorName: String,
        message: String,
        postTitle: String,
        createdAt: Date?,
        isRead: Boolean,
        postId: String,
        notificationId: String,
        userId: String
    ) {

        // Получаем Context из контейнера.
        // Он нужен для создания элементов интерфейса.
        val context = container.context

        // Получаем плотность экрана устройства.
        // Она нужна, чтобы правильно переводить dp в пиксели.
        val density = context.resources.displayMetrics.density

        // Вспомогательная функция для перевода dp в пиксели.
        fun dp(value: Int): Int = (value * density).toInt()


        // -----------------------------------------------------
        // ОСНОВНАЯ КАРТОЧКА
        // -----------------------------------------------------

        // Создаём горизонтальный LinearLayout.
        // Внутри него будут находиться иконка или аватар и текст.
        val card = LinearLayout(context).apply {

            // Располагаем элементы по горизонтали.
            orientation = LinearLayout.HORIZONTAL

            // Выравниваем элементы по вертикальному центру.
            gravity = Gravity.CENTER_VERTICAL

            // Добавляем внутренние отступы.
            setPadding(dp(14), dp(13), dp(14), dp(13))

            // Создаём закруглённый фон карточки.
            background = GradientDrawable().apply {

                // Радиус закругления углов.
                cornerRadius = dp(14).toFloat()

                // Для прочитанного уведомления используем обычный фон.
                // Для непрочитанного — более заметный фиолетовый оттенок.
                setColor(
                    if (isRead) cardColor else Color.rgb(39, 32, 53)
                )
            }

            // Разрешаем нажатие на карточку.
            isClickable = true

            // Разрешаем получение фокуса.
            isFocusable = true
        }


        // -----------------------------------------------------
        // АВАТАР ИЛИ ИКОНКА УВЕДОМЛЕНИЯ
        // -----------------------------------------------------

        // Для подписки показываем аватар пользователя.
        // Для лайков и комментариев используем тематические иконки.
        if (type == "follow") {

            // Создаём ImageView для аватара подписчика.
            val avatar = ImageView(context).apply {

                // Размер аватара.
                val size = dp(48)

                // Устанавливаем ширину и высоту.
                layoutParams = LinearLayout.LayoutParams(size, size)

                // Создаём круглый фон.
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.rgb(45, 40, 60))
                }

                // Обрезаем изображение по форме фона.
                clipToOutline = true

                // Изображение будет заполнять круг без искажения.
                scaleType = ImageView.ScaleType.CENTER_CROP

                // Устанавливаем изображение по умолчанию.
                setImageResource(R.drawable.ic_launcher_foreground)

                // Описание изображения для специальных возможностей.
                contentDescription = "Аватар $actorName"
            }

            // Добавляем аватар в карточку.
            card.addView(avatar)

            // Если UID подписчика существует,
            // пробуем загрузить его аватар из Firestore.
            if (actorId.isNotBlank()) {

                // Получаем LifecycleOwner контейнера.
                // Это позволяет запускать корутины с учётом жизненного цикла экрана.
                container.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {

                    try {

                        // Загружаем публичный профиль подписчика.
                        val profile = db.collection("publicProfiles")
                            .document(actorId)
                            .get()
                            .await()

                        // Сначала проверяем avatarUrl.
                        // Если его нет, пробуем использовать avatarUri.
                        val currentAvatar = profile.getString("avatarUrl")
                            ?.takeIf { it.isNotBlank() }
                            ?: profile.getString("avatarUri").orEmpty()

                        // Glide загружает изображение только в том случае,
                        // если получена ссылка HTTP или HTTPS.
                        if (currentAvatar.startsWith("https://")) {

                            Glide.with(avatar)
                                .load(currentAvatar)

                                // Изображение, которое показывается во время загрузки.
                                .placeholder(R.drawable.ic_launcher_foreground)

                                // Изображение, которое показывается при ошибке.
                                .error(R.drawable.ic_launcher_foreground)

                                // Обрезаем загруженную фотографию по кругу.
                                .circleCrop()

                                // Устанавливаем изображение в ImageView.
                                .into(avatar)
                        }

                    } catch (e: Exception) {

                        // Если загрузить аватар не удалось,
                        // записываем ошибку в Logcat.
                        android.util.Log.e(
                            "NotificationsManager",
                            "Не удалось загрузить аватар подписчика",
                            e
                        )
                    }
                }
            }

        } else {

            // Для лайков и комментариев создаём TextView с эмодзи.
            val icon = TextView(context).apply {

                // Выбираем эмодзи в зависимости от типа уведомления.
                text = when (type) {
                    "comment" -> "💬"
                    else -> "❤️"
                }

                // Размер эмодзи.
                textSize = 25f

                // Размещаем эмодзи по центру.
                gravity = Gravity.CENTER
            }

            // Добавляем иконку в карточку.
            card.addView(
                icon,
                LinearLayout.LayoutParams(dp(48), dp(48))
            )
        }


        // -----------------------------------------------------
        // КОНТЕЙНЕР ТЕКСТОВОЙ ИНФОРМАЦИИ
        // -----------------------------------------------------

        // Создаём вертикальный контейнер для текста уведомления.
        val textContainer = LinearLayout(context).apply {

            // Элементы будут располагаться друг под другом.
            orientation = LinearLayout.VERTICAL

            // Отступ слева от аватара или иконки.
            setPadding(dp(12), 0, 0, 0)
        }


        // -----------------------------------------------------
        // ОСНОВНОЙ ТЕКСТ УВЕДОМЛЕНИЯ
        // -----------------------------------------------------

        // Создаём TextView с описанием действия.
        val messageView = TextView(context).apply {

            // Устанавливаем текст уведомления.
            text = message

            // Размер текста.
            textSize = 14f

            // Белый цвет текста.
            setTextColor(primaryColor)

            // Непрочитанные уведомления выделяем жирным шрифтом.
            setTypeface(
                null,
                if (isRead) Typeface.NORMAL else Typeface.BOLD
            )
        }

        // Добавляем основной текст в контейнер.
        textContainer.addView(messageView)


        // -----------------------------------------------------
        // ЗАГОЛОВОК ПУБЛИКАЦИИ
        // -----------------------------------------------------

        // Если у уведомления есть заголовок публикации
        // и это не уведомление о подписке, показываем его.
        if (postTitle.isNotBlank() && type != "follow") {

            val titleView = TextView(context).apply {

                // Добавляем название публикации.
                text = "Публикация: $postTitle"

                // Размер текста.
                textSize = 13f

                // Используем фиолетовый цвет.
                setTextColor(purpleColor)

                // Небольшой отступ сверху.
                setPadding(0, dp(5), 0, 0)
            }

            // Добавляем заголовок в контейнер.
            textContainer.addView(titleView)
        }


        // -----------------------------------------------------
        // ДАТА И ВРЕМЯ УВЕДОМЛЕНИЯ
        // -----------------------------------------------------

        // Создаём TextView для даты.
        val timeView = TextView(context).apply {

            // Если дата существует, форматируем её.
            // Иначе отображаем "Недавно".
            text = createdAt?.let {

                SimpleDateFormat(
                    "dd.MM.yyyy, HH:mm",
                    Locale.getDefault()
                ).format(it)

            } ?: "Недавно"

            // Размер текста.
            textSize = 11f

            // Серый цвет.
            setTextColor(secondaryColor)

            // Отступ сверху.
            setPadding(0, dp(5), 0, 0)
        }

        // Добавляем дату в контейнер.
        textContainer.addView(timeView)


        // -----------------------------------------------------
        // ДОБАВЛЕНИЕ ТЕКСТА В КАРТОЧКУ
        // -----------------------------------------------------

        // Добавляем текстовый контейнер в карточку.
        //
        // Ширина 0 и weight = 1f означают,
        // что он займёт всё свободное пространство.
        card.addView(
            textContainer,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )


        // -----------------------------------------------------
        // ДОБАВЛЕНИЕ КАРТОЧКИ НА ЭКРАН
        // -----------------------------------------------------

        container.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {

                // Отступ снизу между карточками.
                bottomMargin = dp(9)
            }
        )


        // -----------------------------------------------------
        // ОБРАБОТКА НАЖАТИЯ НА УВЕДОМЛЕНИЕ
        // -----------------------------------------------------

        card.setOnClickListener {

            // Если уведомление ещё не прочитано,
            // обновляем его поле read в Firestore.
            if (!isRead) {

                db.collection("users")
                    .document(userId)
                    .collection("notifications")
                    .document(notificationId)

                    // Устанавливаем read = true.
                    .update("read", true)

                    // Если обновление не удалось,
                    // выводим ошибку в Logcat.
                    .addOnFailureListener { error ->

                        android.util.Log.e(
                            "NotificationsManager",
                            "Не удалось отметить уведомление прочитанным",
                            error
                        )
                    }
            }

            // Определяем действие в зависимости от типа уведомления.
            when (type) {

                // -------------------------------------------------
                // ПЕРЕХОД К ПРОФИЛЮ ПОДПИСЧИКА
                // -------------------------------------------------

                "follow" -> {

                    // Проверяем, существует ли UID пользователя.
                    if (actorId.isBlank()) {

                        Toast.makeText(
                            context,
                            "Профиль подписчика недоступен",
                            Toast.LENGTH_SHORT
                        ).show()

                    } else {

                        // Передаём UID в MainActivity
                        // для открытия публичного профиля.
                        onProfileClick(actorId)
                    }
                }


                // -------------------------------------------------
                // ПЕРЕХОД К ПУБЛИКАЦИИ
                // -------------------------------------------------

                "like", "comment" -> {

                    // Проверяем наличие ID публикации.
                    if (postId.isBlank()) {

                        Toast.makeText(
                            context,
                            "У этого уведомления нет ID публикации",
                            Toast.LENGTH_SHORT
                        ).show()

                    } else {

                        // Передаём ID публикации в MainActivity.
                        onNotificationClick(postId)
                    }
                }


                // -------------------------------------------------
                // ОБРАБОТКА ДРУГИХ ТИПОВ УВЕДОМЛЕНИЙ
                // -------------------------------------------------

                else -> {

                    // Если тип неизвестен, но ID публикации существует,
                    // всё равно пробуем открыть публикацию.
                    if (postId.isNotBlank()) {
                        onNotificationClick(postId)
                    }
                }
            }
        }
    }


    // ---------------------------------------------------------
    // ОТОБРАЖЕНИЕ ПУСТОГО СОСТОЯНИЯ
    // ---------------------------------------------------------

    // Метод показывает сообщение, если уведомления отсутствуют
    // или произошла ошибка загрузки.
    private fun showEmpty(
        container: LinearLayout,
        message: String
    ) {

        // Удаляем все предыдущие элементы.
        container.removeAllViews()

        // Создаём TextView с сообщением.
        val text = TextView(container.context).apply {

            // Устанавливаем текст.
            this.text = message

            // Размер шрифта.
            textSize = 14f

            // Серый цвет.
            setTextColor(secondaryColor)

            // Размещаем текст по центру.
            gravity = Gravity.CENTER

            // Добавляем внутренние отступы.
            setPadding(24, 32, 24, 32)
        }

        // Добавляем сообщение в контейнер.
        container.addView(
            text,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }


    // ---------------------------------------------------------
    // ОСТАНОВКА ПРОСЛУШИВАНИЯ FIRESTORE
    // ---------------------------------------------------------

    // Метод отключает слушатель изменений уведомлений.
    //
    // Его можно вызывать, когда экран уведомлений больше не нужен,
    // чтобы не получать обновления и не расходовать ресурсы.
    fun stopListening() {

        // Удаляем активный слушатель Firestore.
        listener?.remove()

        // Сбрасываем ссылку на слушатель.
        listener = null
    }
}