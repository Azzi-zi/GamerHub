package com.example.gamerhub

import android.view.Gravity
import android.widget.FrameLayout
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue

class PostsManager(
    private val activity: AppCompatActivity
) {

    // Подключение к базе данных Cloud Firestore.
    // Через этот объект загружаем публикации, профили и сохранённые записи.
    private val db = FirebaseFirestore.getInstance()

    // Кэш URL аватаров авторов.
    // Если аватар уже загружался, повторно обращаться к Firestore не нужно.
    private val authorAvatarCache = mutableMapOf<String, String>()

    // Список авторов, для которых прямо сейчас выполняется загрузка аватара.
    // Позволяет избежать нескольких одинаковых запросов одновременно.
    private val authorAvatarRequests = mutableSetOf<String>()

    // Список функций, которые нужно вызвать после загрузки аватара автора.
    // Один автор может иметь несколько карточек публикаций на экране.
    private val authorAvatarCallbacks =
        mutableMapOf<String, MutableList<(String) -> Unit>>()

    // Функция, которая вызывается при нажатии на карточку публикации.
    // Передаёт выбранную публикацию в MainActivity или другой управляющий класс.
    private var onPostClick: ((Post) -> Unit)? = null

    // Функция, которая вызывается при нажатии на имя или аватар автора.
    // Передаёт ID пользователя, профиль которого нужно открыть.
    private var onAuthorClick: ((String) -> Unit)? = null

    // Устанавливаем обработчик нажатия на автора публикации.
    fun setOnAuthorClickListener(listener: (String) -> Unit) {
        onAuthorClick = listener
    }

    // Устанавливаем обработчик нажатия на карточку публикации.
    fun setOnPostClickListener(listener: (Post) -> Unit) {
        onPostClick = listener
    }

    // Загружает аватар автора из его публичного профиля.
    private fun loadAuthorAvatar(
        authorId: String,
        callback: (String) -> Unit
    ) {
        // Если ID автора отсутствует, возвращаем пустую строку.
        if (authorId.isBlank()) {
            callback("")
            return
        }

        // Если URL уже сохранён в кэше, сразу передаём его в callback.
        if (authorAvatarCache.containsKey(authorId)) {
            callback(authorAvatarCache[authorId].orEmpty())
            return
        }

        // Получаем список ожидающих обновления карточек этого автора.
        // Если списка ещё нет, создаём новый.
        val callbacks = authorAvatarCallbacks.getOrPut(authorId) {
            mutableListOf()
        }

        // Добавляем текущую карточку в список ожидающих.
        callbacks.add(callback)

        // Если запрос для этого автора уже выполняется,
        // не запускаем ещё один такой же запрос.
        if (!authorAvatarRequests.add(authorId)) {
            return
        }

        // Запускаем корутину для обращения к Firestore.
        activity.lifecycleScope.launch {

            // Получаем URL аватара из публичного профиля.
            val avatarUrl = try {
                val document = db.collection("publicProfiles")
                    .document(authorId)
                    .get()
                    .await()

                // Сначала проверяем avatarUrl.
                // Если его нет, используем avatarUri.
                document.getString("avatarUrl")
                    ?: document.getString("avatarUri")
                    ?: ""

            } catch (e: Exception) {
                // Записываем ошибку в Logcat, если загрузка не удалась.
                android.util.Log.e(
                    "PostsManager",
                    "Не удалось загрузить аватар автора",
                    e
                )

                ""
            }

            // Сохраняем полученный URL в кэше.
            authorAvatarCache[authorId] = avatarUrl

            // Удаляем автора из списка выполняющихся запросов.
            authorAvatarRequests.remove(authorId)

            // Получаем все карточки, которые ожидали загрузку аватара.
            val waitingCallbacks =
                authorAvatarCallbacks.remove(authorId).orEmpty()

            // Обновляем аватары во всех ожидающих карточках.
            waitingCallbacks.forEach { update ->
                update(avatarUrl)
            }
        }
    }

    // Загружает публикации, относящиеся к конкретной игре.
    fun loadPostsForGame(view: View, gameTitle: String) {

        // Находим контейнер, в котором будут отображаться карточки публикаций.
        val container = view.findViewById<LinearLayout>(
            R.id.gamePostsContainer
        ) ?: return

        // Запускаем асинхронную загрузку данных.
        activity.lifecycleScope.launch {
            try {
                // Получаем публикации из коллекции posts.
                val snapshot = db.collection("posts")
                    .get()
                    .await()

                // Преобразуем документы Firestore в объекты Post.
                // ID документа записываем в поле id модели.
                val posts = snapshot.documents
                    .mapNotNull { document ->
                        document.toObject(Post::class.java)?.copy(
                            id = document.id
                        )
                    }
                    // Оставляем только публикации об указанной игре.
                    // ignoreCase = true позволяет не учитывать регистр букв.
                    .filter { post ->
                        post.game.trim().equals(
                            gameTitle.trim(),
                            ignoreCase = true
                        )
                    }
                    // Меняем порядок, чтобы новые публикации отображались первыми.
                    .reversed()

                // Удаляем старые карточки из контейнера.
                container.removeAllViews()

                // Если публикаций нет, показываем соответствующее сообщение.
                if (posts.isEmpty()) {
                    val emptyText = TextView(activity).apply {
                        text = "Публикаций об этой игре пока нет"
                        textSize = 15f
                        setTextColor(Color.LTGRAY)
                        setPadding(0, 12, 0, 12)
                    }

                    container.addView(emptyText)
                    return@launch
                }

                // Создаём карточку для каждой найденной публикации.
                posts.forEach { post ->
                    container.addView(createPostCard(post))
                }

            } catch (e: Exception) {
                // Записываем ошибку, если Firestore не смог вернуть данные.
                android.util.Log.e(
                    "PostsManager",
                    "Ошибка загрузки публикаций для игры: $gameTitle",
                    e
                )

                // Показываем пользователю сообщение об ошибке.
                Toast.makeText(
                    activity,
                    "Не удалось загрузить публикации игры",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Загружает публикации для главной страницы.
    // Поддерживает фильтрацию по категории и поисковому запросу.
    fun loadPosts(
        view: View,
        category: String = "Все",
        query: String = ""
    ) {

        // Находим контейнер для карточек публикаций.
        val container = view.findViewById<LinearLayout>(
            R.id.postsContainer
        ) ?: return

        // Загружаем данные в отдельной корутине.
        activity.lifecycleScope.launch {
            try {
                // Получаем документы из коллекции posts.
                val snapshot = db.collection("posts")
                    .get()
                    .await()

                // Убираем лишние пробелы в поисковом запросе.
                val searchText = query.trim()

                // Преобразуем документы в объекты Post и применяем фильтры.
                val posts = snapshot.documents
                    .mapNotNull { document ->
                        document.toObject(Post::class.java)
                            ?.copy(id = document.id)
                    }
                    .filter { post ->

                        // Сначала проверяем выбранную категорию.
                        // Категория "Все" означает отсутствие этого фильтра.
                        val matchesCategory =
                            category == "Все" ||
                                    post.postType.equals(
                                        category,
                                        ignoreCase = true
                                    )

                        // Формируем список текстовых данных публикации,
                        // по которым можно выполнять поиск.
                        val searchableText = listOf(
                            post.title,
                            post.content,
                            post.game,
                            post.authorName,
                            post.postType
                        ) + post.gameTags +
                                post.topicTags +
                                post.tags

                        // Проверяем, есть ли поисковый запрос
                        // хотя бы в одном из текстовых полей.
                        val matchesQuery =
                            searchText.isBlank() ||
                                    searchableText.any { text ->
                                        text.contains(
                                            searchText,
                                            ignoreCase = true
                                        )
                                    }

                        // Публикация должна соответствовать обоим условиям.
                        matchesCategory && matchesQuery
                    }
                    // Показываем сначала более новые публикации.
                    .reversed()

                // Очищаем контейнер перед добавлением новых карточек.
                container.removeAllViews()

                // Если ничего не найдено, показываем подходящее сообщение.
                if (posts.isEmpty()) {
                    val emptyText = TextView(activity).apply {
                        text = if (searchText.isNotBlank()) {
                            "По запросу «$searchText» ничего не найдено"
                        } else {
                            "Публикаций пока нет"
                        }

                        textSize = 15f
                        setTextColor(Color.LTGRAY)
                        setPadding(20, 24, 20, 24)
                    }

                    container.addView(emptyText)
                    return@launch
                }

                // Добавляем карточки найденных публикаций.
                posts.forEach { post ->
                    container.addView(createPostCard(post))
                }

            } catch (e: Exception) {
                // Записываем ошибку загрузки в Logcat.
                android.util.Log.e(
                    "PostsManager",
                    "Ошибка загрузки публикаций",
                    e
                )

                Toast.makeText(
                    activity,
                    "Не удалось загрузить публикации",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Загружает публикации конкретного пользователя.
    // Обычно используется при открытии его профиля.
    fun loadUserPosts(view: View, userId: String) {

        // Контейнер, в котором отображаются публикации пользователя.
        val container = view.findViewById<LinearLayout>(
            R.id.profilePostsContainer
        ) ?: return

        // Контейнер с сообщением о том, что публикаций пока нет.
        val emptyContainer = view.findViewById<View>(
            R.id.profileEmptyContainer
        )

        activity.lifecycleScope.launch {
            try {
                // Ищем публикации, у которых authorId совпадает с userId.
                val snapshot = db.collection("posts")
                    .whereEqualTo("authorId", userId)
                    .get()
                    .await()

                // Преобразуем документы Firestore в объекты Post.
                val posts = snapshot.documents
                    .mapNotNull { document ->
                        document.toObject(Post::class.java)
                            ?.copy(id = document.id)
                    }
                    .reversed()

                // Удаляем предыдущие карточки.
                container.removeAllViews()

                // Записываем количество найденных публикаций в Logcat.
                android.util.Log.d(
                    "PostsManager",
                    "Публикаций найдено: ${posts.size}"
                )

                if (posts.isEmpty()) {

                    // Если публикаций нет, скрываем список
                    // и показываем сообщение о пустом профиле.
                    container.visibility = View.GONE
                    emptyContainer?.visibility = View.VISIBLE

                    view.findViewById<TextView>(
                        R.id.profileEmptyMessage
                    )?.text = "У тебя пока нет публикаций"

                } else {

                    // Если публикации есть, показываем список.
                    container.visibility = View.VISIBLE
                    emptyContainer?.visibility = View.GONE

                    // Создаём карточку для каждой публикации.
                    posts.forEach { post ->
                        container.addView(createPostCard(post))
                    }
                }

                // Обновляем счётчик публикаций в профиле.
                view.findViewById<TextView>(
                    R.id.profilePostsCount
                )?.text = posts.size.toString()

            } catch (e: Exception) {
                // Обрабатываем ошибку загрузки публикаций профиля.
                android.util.Log.e(
                    "PostsManager",
                    "Ошибка загрузки публикаций профиля",
                    e
                )

                Toast.makeText(
                    activity,
                    "Не удалось загрузить публикации",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Загружает сохранённые публикации текущего пользователя.
    fun loadSavedPosts(view: View) {

        // Получаем UID авторизованного пользователя.
        // Если пользователь не вошёл в аккаунт, прекращаем выполнение.
        val uid = FirebaseAuth
            .getInstance().currentUser?.uid ?: return

        // Находим контейнер для сохранённых публикаций.
        val container = view.findViewById<LinearLayout>(
            R.id.profileSavedPostsContainer
        ) ?: return

        // Контейнер, который отображается, если сохранённых публикаций нет.
        val emptyContainer = view.findViewById<View>(
            R.id.profileSavedEmptyContainer
        )

        // Текст сообщения для пустого состояния.
        val emptyMessage = view.findViewById<TextView>(
            R.id.profileSavedEmptyMessage
        )

        activity.lifecycleScope.launch {
            try {
                // Перед новой загрузкой очищаем старые карточки.
                container.removeAllViews()
                emptyContainer?.visibility = View.GONE

                // Получаем список ID сохранённых публикаций пользователя.
                val savedSnapshot = db.collection("users")
                    .document(uid)
                    .collection("savedPosts")
                    .get()
                    .await()

                // Из каждого документа получаем postId.
                // Если поле отсутствует, используем ID самого документа.
                val savedIds = savedSnapshot.documents
                    .map { it.getString("postId") ?: it.id }
                    .toSet()

                // Если сохранённых публикаций нет, показываем пустое состояние.
                if (savedIds.isEmpty()) {
                    emptyMessage?.text =
                        "Здесь пока нет сохранённых публикаций"

                    emptyContainer?.visibility = View.VISIBLE
                    return@launch
                }

                // Загружаем публикации из общей коллекции одним запросом.
                val allPosts = db.collection("posts")
                    .get()
                    .await()

                // Оставляем только те публикации, ID которых есть в savedIds.
                // Затем преобразуем документы в объекты Post.
                val postsById = allPosts.documents
                    .filter { it.id in savedIds }
                    .mapNotNull { document ->
                        document.toObject(Post::class.java)
                            ?.copy(id = document.id)
                    }

                // Восстанавливаем порядок публикаций по списку сохранений.
                // Это позволяет сохранить порядок, в котором записи находятся
                // в коллекции savedPosts.
                val orderedPosts = savedSnapshot.documents
                    .mapNotNull { savedDocument ->
                        val postId = savedDocument.getString("postId")
                            ?: savedDocument.id

                        postsById.find { it.id == postId }
                    }

                // Очищаем контейнер перед отображением результата.
                container.removeAllViews()

                if (orderedPosts.isEmpty()) {

                    // Если все сохранённые публикации удалены или недоступны,
                    // показываем соответствующее сообщение.
                    emptyMessage?.text =
                        "Сохранённые публикации больше недоступны"

                    emptyContainer?.visibility = View.VISIBLE

                } else {

                    // Создаём карточки для доступных сохранённых публикаций.
                    orderedPosts.forEach { post ->
                        container.addView(createPostCard(post))
                    }
                }

            } catch (e: Exception) {
                // Обрабатываем ошибки при получении сохранённых публикаций.
                android.util.Log.e(
                    "PostsManager",
                    "Ошибка загрузки сохранённых публикаций",
                    e
                )

                emptyMessage?.text =
                    "Не удалось загрузить сохранённое"

                emptyContainer?.visibility = View.VISIBLE
            }
        }
    }

    // Публичный метод для создания карточки публикации в профиле.
    // Внутри использует тот же дизайн, что и карточки на главной странице.
    fun createProfilePostCard(post: Post): View {
        return createPostCard(post)
    }

    // Создаёт полноценную карточку публикации.
    // Здесь формируются изображение, категория, кнопка сохранения,
    // заголовок, описание, информация об авторе и теги.
    private fun createPostCard(post: Post): View {

        // Основной контейнер карточки.
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 14)

            // Создаём тёмный фон с закруглёнными углами.
            background = GradientDrawable().apply {
                setColor(Color.rgb(26, 26, 32))
                cornerRadius = 18f
            }

            // Обрезаем содержимое по границам закруглённого фона.
            clipToOutline = true

            // Карточка занимает всю доступную ширину.
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }

        // Контейнер изображения и элементов, расположенных поверх него.
        val imageContainer = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                190
            )
        }

        // ImageView, в котором отображается обложка публикации.
        val image = ImageView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            // CENTER_CROP заполняет всё пространство изображения,
            // обрезая лишние края при необходимости.
            scaleType = ImageView.ScaleType.CENTER_CROP

            contentDescription = "Обложка публикации"
        }

        // Если у публикации есть ссылка на изображение,
        // загружаем его с помощью библиотеки Glide.
        if (post.imageUrl.isNotBlank()) {
            Glide.with(activity)
                .load(post.imageUrl)
                .centerCrop()
                .into(image)
        }

        // Добавляем обложку в контейнер изображения.
        imageContainer.addView(image)

        // Плашка с названием категории публикации.
        val categoryLabel = TextView(activity).apply {
            text = post.postType.uppercase()
            textSize = 11f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)

            // Используем заранее созданный drawable-фон.
            background = androidx.core.content.ContextCompat.getDrawable(
                activity,
                R.drawable.bg_post_category
            )

            setPadding(12, 7, 12, 7)

            // Размещаем плашку в левом нижнем углу обложки.
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.START
            ).apply {
                setMargins(12, 12, 12, 12)
            }
        }

        imageContainer.addView(categoryLabel)

        // Кнопка добавления публикации в сохранённое.
        val bookmarkButton = TextView(activity).apply {
            text = "🔖"
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(12, 8, 12, 8)
            contentDescription = "Сохранить публикацию"

            // Создаём тёмный фон с закруглёнными углами.
            background = GradientDrawable().apply {
                setColor(Color.rgb(30, 30, 40))
                cornerRadius = 14f
            }

            // Размещаем кнопку в правом верхнем углу изображения.
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                setMargins(0, 12, 12, 0)
            }
        }

        imageContainer.addView(bookmarkButton)

        // Получаем UID текущего авторизованного пользователя.
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        // Если пользователь авторизован и у публикации есть ID,
        // разрешаем сохранять и удалять её из сохранённого.
        if (uid != null && post.id.isNotBlank()) {

            // Ссылка на документ сохранённой публикации.
            // Путь: users / UID / savedPosts / ID публикации.
            val savedRef = db.collection("users")
                .document(uid)
                .collection("savedPosts")
                .document(post.id)

            // Переменная показывает, сохранена ли публикация.
            var isSaved = false

            // Не позволяет отправлять несколько запросов одновременно.
            var requestInProgress = false

            // Проверяем, находится ли публикация в сохранённом.
            activity.lifecycleScope.launch {
                try {
                    isSaved = savedRef.get().await().exists()

                    // Меняем значок кнопки в зависимости от состояния.
                    bookmarkButton.text =
                        if (isSaved) "🔖" else "♧"

                    // Обновляем описание кнопки для доступности.
                    bookmarkButton.contentDescription =
                        if (isSaved) {
                            "Убрать из сохранённого"
                        } else {
                            "Сохранить публикацию"
                        }

                } catch (e: Exception) {
                    android.util.Log.e(
                        "PostsManager",
                        "Не удалось проверить сохранение",
                        e
                    )
                }
            }

            // Обработчик нажатия на кнопку сохранения.
            bookmarkButton.setOnClickListener {

                // Если предыдущий запрос ещё не завершился,
                // игнорируем повторное нажатие.
                if (requestInProgress) {
                    return@setOnClickListener
                }

                // Блокируем кнопку на время выполнения запроса.
                requestInProgress = true
                bookmarkButton.isEnabled = false

                activity.lifecycleScope.launch {
                    try {

                        if (isSaved) {

                            // Если публикация уже сохранена,
                            // удаляем её из коллекции savedPosts.
                            savedRef.delete().await()
                            isSaved = false

                            Toast.makeText(
                                activity,
                                "Публикация удалена из сохранённого",
                                Toast.LENGTH_SHORT
                            ).show()

                        } else {

                            // Если публикация ещё не сохранена,
                            // создаём документ с её ID и временем сохранения.
                            savedRef.set(
                                mapOf(
                                    "postId" to post.id,
                                    "createdAt" to
                                            FieldValue.serverTimestamp()
                                )
                            ).await()

                            isSaved = true

                            Toast.makeText(
                                activity,
                                "Публикация сохранена",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        // Обновляем значок после успешного изменения.
                        bookmarkButton.text =
                            if (isSaved) "🔖" else "♧"

                        // Обновляем описание кнопки.
                        bookmarkButton.contentDescription =
                            if (isSaved) {
                                "Убрать из сохранённого"
                            } else {
                                "Сохранить публикацию"
                            }

                    } catch (e: Exception) {

                        // Если запрос завершился ошибкой,
                        // записываем её в Logcat и показываем сообщение.
                        android.util.Log.e(
                            "PostsManager",
                            "Ошибка сохранения публикации",
                            e
                        )

                        Toast.makeText(
                            activity,
                            "Не удалось изменить сохранение",
                            Toast.LENGTH_SHORT
                        ).show()

                    } finally {

                        // В любом случае снова разрешаем нажимать кнопку.
                        requestInProgress = false
                        bookmarkButton.isEnabled = true
                    }
                }
            }

        } else {

            // Если пользователь не авторизован или у публикации нет ID,
            // отключаем возможность сохранения.
            bookmarkButton.isEnabled = false
        }

        // Контейнер для текстовой информации публикации.
        val textContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 4)
        }

        // Вспомогательная функция для добавления TextView в карточку.
        // Благодаря ей не приходится каждый раз создавать TextView вручную.
        fun addText(
            value: String,
            size: Float,
            color: Int,
            bold: Boolean = false
        ) {
            val text = TextView(activity).apply {
                text = value
                textSize = size
                setTextColor(color)

                // Если bold = true, устанавливаем жирный шрифт.
                if (bold) {
                    setTypeface(null, Typeface.BOLD)
                }

                setPadding(0, 4, 0, 4)
            }

            // Добавляем созданный текстовый элемент в контейнер.
            textContainer.addView(text)
        }

        // Заголовок публикации.
        addText(
            post.title,
            19f,
            Color.WHITE,
            true
        )

        // Название игры, к которой относится публикация.
        addText(
            "🎮 ${post.game}",
            13f,
            Color.LTGRAY
        )

        // Основной текст публикации.
        addText(
            post.content,
            14f,
            Color.rgb(220, 220, 225)
        )

        // Информация об авторе публикации.

        // Если имя автора отсутствует, используем запасное название.
        val author = post.authorName.ifBlank {
            "Игрок GamerHub"
        }

        // Горизонтальный контейнер для аватара и имени автора.
        val authorContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)
        }

        // ImageView для отображения круглого аватара автора.
        val authorAvatar = ImageView(activity).apply {

            // Рассчитываем размер аватара с учётом плотности экрана.
            val avatarSize =
                (40 * resources.displayMetrics.density).toInt()

            layoutParams = LinearLayout.LayoutParams(
                avatarSize,
                avatarSize
            )

            scaleType = ImageView.ScaleType.CENTER_CROP

            // Создаём круглый фон-заглушку.
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(45, 40, 60))
            }

            // Обрезаем изображение по форме круга.
            clipToOutline = true

            // Пока настоящий аватар не загружен,
            // показываем стандартную иконку приложения.
            setImageResource(R.drawable.ic_launcher_foreground)
        }

        // TextView с именем автора.
        val authorView = TextView(activity).apply {
            text = author
            textSize = 13f
            setTextColor(Color.rgb(180, 140, 255))
            setTypeface(null, Typeface.BOLD)

            // Добавляем небольшой отступ между аватаром и именем.
            setPadding(
                (10 * resources.displayMetrics.density).toInt(),
                0,
                0,
                0
            )
        }

        // Общий обработчик открытия профиля автора.
        val openAuthorProfile = {
            if (post.authorId.isNotBlank()) {

                // Передаём ID автора установленному обработчику.
                onAuthorClick?.invoke(post.authorId)

            } else {

                // Если ID автора отсутствует, открыть профиль невозможно.
                Toast.makeText(
                    activity,
                    "Профиль автора недоступен",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // Нажатие на контейнер автора открывает его профиль.
        authorContainer.setOnClickListener {
            openAuthorProfile()
        }

        // Нажатие непосредственно на аватар также открывает профиль.
        authorAvatar.setOnClickListener {
            openAuthorProfile()
        }

        // Нажатие на имя автора открывает его профиль.
        authorView.setOnClickListener {
            openAuthorProfile()
        }

        // Сначала пытаемся использовать URL аватара,
        // который уже сохранён непосредственно в публикации.
        if (post.authorAvatarUrl.startsWith("https://")) {

            Glide.with(activity)
                .load(post.authorAvatarUrl)
                .placeholder(R.drawable.ic_launcher_foreground)
                .error(R.drawable.ic_launcher_foreground)
                .circleCrop()
                .into(authorAvatar)

        } else {

            // Если URL в публикации отсутствует,
            // загружаем его из публичного профиля автора.
            loadAuthorAvatar(post.authorId) { avatarUrl ->

                // Загружаем изображение только при наличии HTTPS-ссылки.
                if (avatarUrl.startsWith("https://")) {
                    Glide.with(activity)
                        .load(avatarUrl)
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .error(R.drawable.ic_launcher_foreground)
                        .circleCrop()
                        .into(authorAvatar)
                }
            }
        }

        // Добавляем аватар и имя автора в горизонтальный контейнер.
        authorContainer.addView(authorAvatar)
        authorContainer.addView(authorView)

        // Добавляем информацию об авторе в карточку.
        textContainer.addView(authorContainer)

        // Получаем теги игры и темы публикации.
        // distinct() удаляет повторяющиеся значения.
        val tags = (post.gameTags + post.topicTags).distinct()

        // Если теги существуют, отображаем их под текстом публикации.
        if (tags.isNotEmpty()) {
            addText(
                tags.joinToString("  •  "),
                12f,
                Color.rgb(180, 150, 255)
            )
        }

        // Собираем карточку:
        // сначала изображение, затем текстовая информация.
        card.addView(imageContainer)
        card.addView(textContainer)

        // Разрешаем карточке получать нажатия и фокус.
        card.isClickable = true
        card.isFocusable = true

        // При нажатии на карточку открываем подробную страницу публикации.
        card.setOnClickListener {
            onPostClick?.invoke(post)
        }

        // Возвращаем полностью созданную карточку.
        return card
    }
}