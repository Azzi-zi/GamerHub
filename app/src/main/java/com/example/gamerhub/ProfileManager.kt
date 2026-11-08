package com.example.gamerhub

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

// Класс отвечает за управление экраном профиля пользователя.
//
// Основные задачи:
// - загрузка информации о пользователе из Firestore;
// - отображение аватара, обложки, ролей и любимых игр;
// - подсчёт публикаций, подписчиков, подписок и лайков;
// - редактирование профиля;
// - загрузка изображений в Supabase Storage;
// - сохранение URL изображений в Firestore;
// - выход из аккаунта.
class ProfileManager(
    private val view: View,
    private val postsManager: PostsManager
) {

    // Получаем MainActivity через контекст переданного View.
    // Используем getter, чтобы не хранить отдельную ссылку на Activity.
    private val activity: MainActivity
        get() = view.context as MainActivity

    // Получаем экземпляр Firestore.
    // База данных используется для хранения профилей и другой информации.
    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    // Получаем Firebase Authentication.
    // Он позволяет определить текущего пользователя и управлять авторизацией.
    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    // Основные цвета интерфейса профиля.
    private val purple = android.graphics.Color.rgb(155, 122, 255)
    private val white = android.graphics.Color.WHITE
    private val gray = android.graphics.Color.rgb(136, 136, 146)
    private val lightGray = android.graphics.Color.rgb(170, 170, 170)
    private val dark = android.graphics.Color.rgb(26, 26, 32)

    // Список ролей, которые пользователь может выбрать в настройках профиля.
    private val availableRoles = listOf(
        "🎮 Геймер",
        "📚 Лоровед",
        "✍️ Писатель",
        "💻 Разработчик",
        "🎨 Художник",
        "🎥 Контент-мейкер",
        "🧪 Тестировщик",
        "🎵 Музыкант"
    )

    // Главная точка запуска менеджера профиля.
    // Вызывается при открытии экрана профиля.
    fun setup() {

        // Загружаем основные данные пользователя.
        loadProfile()

        // Подсчитываем лайки под публикациями пользователя.
        loadProfileLikes()

        // Настраиваем кнопки редактирования, настроек и смены изображений.
        setupProfileActions()

        // Настраиваем переключение вкладок публикаций и сохранённого.
        setupTabs()

        // Загружаем количество подписчиков и подписок.
        setupFollowCounts()
    }

    // Настройка счётчиков подписчиков и подписок.
    private fun setupFollowCounts() {

        // Получаем ID текущего пользователя.
        val userId = auth.currentUser?.uid ?: return

        // TextView, отображающий количество подписчиков.
        val followersCount = view.findViewById<TextView>(
            R.id.profileFriendsCount
        )

        // TextView, отображающий количество подписок.
        val followingCount = view.findViewById<TextView>(
            R.id.profileFollowingCount
        )

        // При нажатии на количество подписчиков
        // открываем список пользователей, подписанных на текущий аккаунт.
        followersCount.setOnClickListener {
            FollowListManager(activity) { targetUid ->

                // При выборе пользователя открываем его публичный профиль.
                activity.openPublicProfile(targetUid)

            }.showFollowers(userId)
        }

        // При нажатии на количество подписок
        // открываем список аккаунтов, на которые подписан пользователь.
        followingCount.setOnClickListener {
            FollowListManager(activity) { targetUid ->
                activity.openPublicProfile(targetUid)
            }.showFollowing(userId)
        }

        // Загружаем список подписчиков из Firestore.
        db.collection("users")
            .document(userId)
            .collection("followers")
            .get()
            .addOnSuccessListener { documents ->

                // Количество документов соответствует количеству подписчиков.
                followersCount.text = documents.size().toString()
            }
            .addOnFailureListener { error ->

                // Записываем ошибку, если загрузить список не удалось.
                Log.e(TAG, "Ошибка загрузки подписчиков", error)
            }

        // Загружаем список подписок из Firestore.
        db.collection("users")
            .document(userId)
            .collection("following")
            .get()
            .addOnSuccessListener { documents ->

                // Обновляем счётчик подписок.
                followingCount.text = documents.size().toString()
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Ошибка загрузки подписок", error)
            }
    }

    // Загружает профиль текущего пользователя из Firestore.
    private fun loadProfile() {

        // Если пользователь не авторизован, прекращаем выполнение.
        val user = auth.currentUser ?: return

        // Запускаем корутину, чтобы не блокировать главный поток приложения.
        activity.lifecycleScope.launch {
            try {

                // Получаем документ пользователя из коллекции users.
                val document = withContext(Dispatchers.IO) {
                    Tasks.await(
                        db.collection("users")
                            .document(user.uid)
                            .get()
                    )
                }

                // Проверяем, существует ли документ профиля.
                check(document.exists()) {
                    "Профиль не найден в Firestore"
                }

                // Получаем имя пользователя.
                // Если поле отсутствует, выбрасываем ошибку.
                val username = document.getString("username")
                    ?: error("Имя пользователя не найдено")

                // Получаем тег пользователя.
                // Если тег отсутствует, формируем его автоматически
                // на основе имени пользователя.
                val tag = document.getString("tag")
                    ?: "@${username.lowercase().replace(" ", "_")}"

                // Отображаем имя пользователя на экране.
                view.findViewById<TextView>(
                    R.id.profileUsername
                ).text = username

                // Отображаем тег.
                view.findViewById<TextView>(
                    R.id.profileTag
                ).text = tag

                // Загружаем описание профиля.
                view.findViewById<TextView>(
                    R.id.profileBio
                ).text = document.getString("bio").orEmpty()

                // Отображаем выбранные игровые роли.
                renderRoles(
                    document.getString("roles").orEmpty()
                )

                // Получаем список любимых игр.
                // Firestore возвращает список объектов, поэтому оставляем
                // только элементы типа String.
                val favoriteGames = (
                        document.get("favoriteGames") as? List<*>
                        ).orEmpty().filterIsInstance<String>()

                // Отображаем любимые игры.
                renderFavoriteGames(favoriteGames)

                // Получаем URL аватара.
                // Если avatarUrl отсутствует, проверяем avatarUri.
                val avatarUrl = document.getString("avatarUrl")
                    ?: document.getString("avatarUri")
                    ?: ""

                // Отображаем аватар.
                showAvatar(avatarUrl)

                // Получаем URL обложки профиля.
                val coverUrl = document.getString("coverUrl").orEmpty()

                // Отображаем обложку.
                showCover(coverUrl)

            } catch (e: Exception) {

                // Записываем ошибку загрузки профиля в Logcat.
                Log.e(TAG, "Ошибка загрузки профиля", e)

                showToast(
                    "Не удалось загрузить профиль: " +
                            (e.localizedMessage ?: "неизвестная ошибка")
                )
            }
        }
    }

    // Отображает аватар пользователя.
    private fun showAvatar(url: String) {

        // Находим ImageView аватара в XML-разметке.
        val avatar = view.findViewById<ImageView>(
            R.id.profileAvatar
        )

        // Если URL начинается с HTTPS, загружаем изображение через Glide.
        if (url.startsWith("https://")) {

            Glide.with(avatar)
                .load(url)

                // Изображение-заглушка на время загрузки.
                .placeholder(R.drawable.ic_launcher_foreground)

                // Изображение, которое показывается при ошибке.
                .error(R.drawable.ic_launcher_foreground)

                .into(avatar)

        } else {

            // Если ссылки нет, отображаем стандартную иконку приложения.
            avatar.setImageResource(
                R.drawable.ic_launcher_foreground
            )
        }
    }

    // Отображает обложку профиля.
    private fun showCover(url: String) {

        // Находим ImageView обложки.
        val cover = view.findViewById<ImageView>(
            R.id.profileCover
        )

        if (url.startsWith("https://")) {

            // Загружаем изображение обложки.
            Glide.with(cover)
                .load(url)

                // Пока изображение загружается, показываем тёмный фон.
                .placeholder(ColorDrawable(dark))

                // При ошибке также оставляем тёмный фон.
                .error(ColorDrawable(dark))

                // Заполняем доступную область изображения.
                .centerCrop()

                .into(cover)

        } else {

            // Если ссылка отсутствует, оставляем однотонный фон.
            cover.setImageDrawable(ColorDrawable(dark))
        }
    }

    // Отображает игровые роли пользователя.
    // В Firestore роли хранятся в одной строке через символ |.
    // Например: "🎮 Геймер|💻 Разработчик".
    private fun renderRoles(rolesString: String) {

        // Контейнер, в котором будут находиться плашки ролей.
        val container = view.findViewById<LinearLayout>(
            R.id.profileRolesContainer
        )

        // Удаляем предыдущие плашки.
        container.removeAllViews()

        // Разделяем строку по символу |,
        // убираем пробелы и пропускаем пустые значения.
        rolesString.split('|')
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach { role ->

                // Создаём отдельную плашку для каждой роли.
                val roleView = TextView(view.context).apply {
                    text = role
                    textSize = 12f
                    setTextColor(white)

                    // Получаем плотность экрана.
                    // Она нужна для правильного пересчёта размеров в пиксели.
                    val density = resources.displayMetrics.density

                    // Внутренние отступы плашки.
                    setPadding(
                        (12 * density).toInt(),
                        (6 * density).toInt(),
                        (12 * density).toInt(),
                        (6 * density).toInt()
                    )

                    // Устанавливаем готовый фон из ресурсов проекта.
                    setBackgroundResource(
                        R.drawable.bg_category_active
                    )
                }

                // Рассчитываем отступ между плашками.
                val density = view.resources.displayMetrics.density

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = (8 * density).toInt()
                }

                // Добавляем плашку в контейнер.
                container.addView(roleView, params)
            }
    }

    // Отображает любимые игры пользователя.
    private fun renderFavoriteGames(games: List<String>) {

        // Находим контейнер любимых игр.
        val container = view.findViewById<LinearLayout>(
            R.id.profileFavoriteGames
        ) ?: return

        // Удаляем предыдущие элементы.
        container.removeAllViews()

        // Плотность экрана для расчёта размеров.
        val density = view.resources.displayMetrics.density

        // Если список пустой, показываем соответствующее сообщение.
        if (games.isEmpty()) {
            container.addView(
                TextView(view.context).apply {
                    text = "Пока не добавлено ни одной игры"
                    textSize = 13f
                    setTextColor(lightGray)
                    setPadding(0, 4, 0, 4)
                }
            )
            return
        }

        // Создаём отдельную плашку для каждой любимой игры.
        games.forEach { game ->

            val gameView = TextView(view.context).apply {
                text = game
                textSize = 13f
                setTextColor(white)

                // Внутренние отступы плашки.
                setPadding(
                    (12 * density).toInt(),
                    (9 * density).toInt(),
                    (12 * density).toInt(),
                    (9 * density).toInt()
                )

                // Создаём тёмный фон с закруглёнными углами и рамкой.
                background = GradientDrawable().apply {
                    cornerRadius = 12 * density
                    setColor(dark)

                    setStroke(
                        (1 * density).toInt(),
                        android.graphics.Color.rgb(55, 52, 72)
                    )
                }
            }

            // Устанавливаем отступ между плашками.
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = (8 * density).toInt()
            }

            // Добавляем игру в контейнер.
            container.addView(gameView, params)
        }
    }

    // Подсчитывает общее количество лайков под публикациями пользователя.
    private fun loadProfileLikes() {

        // Получаем UID текущего пользователя.
        val uid = auth.currentUser?.uid ?: return

        activity.lifecycleScope.launch {
            try {

                // Выполняем запросы к Firestore в фоновом потоке.
                val totalLikes = withContext(Dispatchers.IO) {

                    // Получаем все публикации текущего пользователя.
                    val posts = Tasks.await(
                        db.collection("posts")
                            .whereEqualTo("authorId", uid)
                            .get()
                    )

                    // Переменная для хранения общего количества лайков.
                    var total = 0

                    // Проходим по каждой публикации.
                    for (post in posts.documents) {

                        // Получаем коллекцию likes конкретной публикации.
                        val likes = Tasks.await(
                            db.collection("posts")
                                .document(post.id)
                                .collection("likes")
                                .get()
                        )

                        // Добавляем количество лайков к общему результату.
                        total += likes.size()
                    }

                    // Возвращаем итоговое количество.
                    total
                }

                // Отображаем количество лайков в профиле.
                view.findViewById<TextView>(
                    R.id.profileLikesCount
                ).text = totalLikes.toString()

            } catch (e: Exception) {
                Log.e(TAG, "Ошибка подсчёта лайков", e)
            }
        }
    }

    // Настраивает кнопки и элементы управления профилем.
    private fun setupProfileActions() {

        // Открываем настройки профиля.
        view.findViewById<View>(
            R.id.profileSettings
        ).setOnClickListener {
            showSettingsDialog()
        }

        // При нажатии на кнопку смены обложки
        // открываем системный выбор изображения.
        view.findViewById<View>(
            R.id.changeCoverButton
        ).setOnClickListener {
            activity.openAvatarPicker { uri ->
                saveCover(uri)
            }
        }

        // При нажатии на контейнер аватара
        // открываем выбор нового изображения.
        view.findViewById<View>(
            R.id.profileAvatarContainer
        ).setOnClickListener {
            activity.openAvatarPicker { uri ->
                saveAvatar(uri)
            }
        }

        // Открываем окно редактирования профиля.
        view.findViewById<View>(
            R.id.editProfileButton
        ).setOnClickListener {
            showEditDialog()
        }

        // Отображаем электронную почту авторизованного пользователя.
        view.findViewById<TextView>(
            R.id.profileEmail
        ).text = auth.currentUser?.email ?: "Аккаунт не найден"
    }

    // Настраивает переключение между вкладками:
    // "Мои статьи" и "Сохранённое".
    private fun setupTabs() {

        // Контейнер собственных публикаций.
        val postsContainer = view.findViewById<View>(
            R.id.profilePostsContainer
        )

        // Контейнер сообщения об отсутствии публикаций.
        val postsEmpty = view.findViewById<View>(
            R.id.profileEmptyContainer
        )

        // Контейнер сохранённых публикаций.
        val savedSection = view.findViewById<View>(
            R.id.profileSavedSection
        )

        // Кнопка вкладки собственных публикаций.
        val postsTab = view.findViewById<TextView>(
            R.id.profilePostsTab
        )

        // Кнопка вкладки сохранённых публикаций.
        val savedTab = view.findViewById<TextView>(
            R.id.profileSavedTab
        )

        // Флаг показывает, загружали ли мы сохранённые публикации.
        // Нужен, чтобы не выполнять повторную загрузку при каждом переключении.
        var savedLoaded = false

        // Переключает внешний вид и содержимое вкладок.
        fun selectTab(showPosts: Boolean) {

            // Меняем цвет названий вкладок.
            postsTab.setTextColor(
                if (showPosts) purple else gray
            )

            savedTab.setTextColor(
                if (showPosts) gray else purple
            )

            // Выделяем активную вкладку жирным шрифтом.
            postsTab.setTypeface(
                null,
                if (showPosts) Typeface.BOLD else Typeface.NORMAL
            )

            savedTab.setTypeface(
                null,
                if (showPosts) Typeface.NORMAL else Typeface.BOLD
            )

            // Показываем собственные публикации или скрываем их.
            postsContainer.visibility =
                if (showPosts) View.VISIBLE else View.GONE

            // Проверяем, есть ли карточки в контейнере публикаций.
            val postsLayout = postsContainer as? LinearLayout

            // Если собственных публикаций нет, показываем пустое состояние.
            postsEmpty.visibility =
                if (showPosts && (postsLayout?.childCount ?: 0) == 0) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

            // Показываем сохранённые публикации только при выборе этой вкладки.
            savedSection.visibility =
                if (showPosts) View.GONE else View.VISIBLE

            // Загружаем сохранённые публикации только один раз.
            if (!showPosts && !savedLoaded) {
                savedLoaded = true
                postsManager.loadSavedPosts(view)
            }
        }

        // Обработчик нажатия на вкладку публикаций.
        postsTab.setOnClickListener {
            selectTab(true)
        }

        // Обработчик нажатия на вкладку сохранённого.
        savedTab.setOnClickListener {
            selectTab(false)
        }

        // При открытии профиля сразу выбираем вкладку публикаций.
        selectTab(true)
    }

    // Загружает изображение в Supabase Storage через Edge Function.
    // После успешной загрузки возвращает публичный URL изображения.
    private suspend fun uploadAvatarToSupabase(
        uri: Uri
    ): String = withContext(Dispatchers.IO) {

        // Получаем текущего пользователя Firebase.
        val user = auth.currentUser
            ?: error("Пользователь не авторизован")

        // Получаем Firebase ID Token.
        // Он используется для подтверждения личности пользователя
        // при обращении к Supabase Edge Function.
        val token = Tasks.await(
            user.getIdToken(false)
        ).token ?: error("Не удалось получить токен")

        Log.d(
            "GamerHubUpload",
            "Firebase token получен: ${token.split(".").size == 3} частей JWT"
        )

        // Получаем ContentResolver для чтения выбранного изображения.
        val resolver = view.context.contentResolver

        // Определяем MIME-тип файла.
        // Например: image/jpeg или image/png.
        val mimeType = resolver.getType(uri)
            ?: error("Не удалось определить формат изображения")

        // Определяем расширение файла на основе MIME-типа.
        val extension = when (mimeType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"

            // Остальные форматы не поддерживаются.
            else -> throw IllegalArgumentException(
                "Поддерживаются только JPG, PNG и WebP"
            )
        }

        // Открываем поток для чтения выбранного изображения.
        val inputStream = resolver.openInputStream(uri)
            ?: error("Не удалось открыть изображение")

        // Считываем содержимое изображения в массив байтов.
        // use автоматически закрывает поток после чтения.
        val fileBytes = inputStream.use {
            it.readBytes()
        }

        // Проверяем размер изображения.
        // Максимальный размер задаётся константой MAX_IMAGE_SIZE.
        require(fileBytes.size <= MAX_IMAGE_SIZE) {
            "Размер изображения не должен превышать 5 МБ"
        }

        // Создаём уникальную границу для multipart-запроса.
        // Она отделяет текстовые поля от содержимого файла.
        val boundary = "----GamerHub${UUID.randomUUID()}"

        // Открываем HTTP-соединение с Supabase Edge Function.
        val connection = URL(SUPABASE_FUNCTION_URL)
            .openConnection() as HttpURLConnection

        try {

            // Настраиваем HTTP-запрос.
            connection.requestMethod = "POST"

            // Максимальное время ожидания подключения.
            connection.connectTimeout = 15_000

            // Максимальное время ожидания ответа.
            connection.readTimeout = 30_000

            // Разрешаем отправку данных.
            connection.doOutput = true

            // Отключаем использование кэша.
            connection.useCaches = false

            // Передаём публичный ключ Supabase.
            connection.setRequestProperty(
                "apikey",
                "sb_publishable_gjoGJw1PsN3_hXth7DuIsg_ruUUnIZ5"
            )

            // Передаём Firebase ID Token.
            connection.setRequestProperty(
                "Authorization",
                "Bearer $token"
            )

            Log.d(
                "GamerHubUpload",
                "Токен получен, количество частей: ${token.split(".").size}"
            )

            // Указываем формат отправляемых данных.
            connection.setRequestProperty(
                "Content-Type",
                "multipart/form-data; boundary=$boundary"
            )

            Log.d("GamerHubUpload", "Начинаем отправку файла")

            // Запоминаем время начала отправки.
            val uploadStartTime = System.currentTimeMillis()

            // Открываем поток для отправки файла.
            connection.outputStream.use { output ->

                // Начинаем multipart-содержимое.
                writeText(output, "--$boundary\r\n")

                // Передаём название поля и имя файла.
                writeText(
                    output,
                    "Content-Disposition: form-data; " +
                            "name=\"file\"; filename=\"image.$extension\"\r\n"
                )

                // Передаём MIME-тип изображения.
                writeText(
                    output,
                    "Content-Type: $mimeType\r\n\r\n"
                )

                // Отправляем непосредственно содержимое изображения.
                output.write(fileBytes)

                // Завершаем multipart-запрос.
                writeText(output, "\r\n--$boundary--\r\n")
            }

            Log.d(
                "GamerHubUpload",
                "Файл передан в поток за ${System.currentTimeMillis() - uploadStartTime} мс"
            )

            Log.d(
                "GamerHubUpload",
                "Ждём ответ Supabase; прошло ${System.currentTimeMillis() - uploadStartTime} мс"
            )

            // Получаем HTTP-код ответа сервера.
            val responseCode = connection.responseCode

            // Для успешного ответа читаем inputStream,
            // для ошибки — errorStream.
            val responseStream =
                if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            // Преобразуем ответ сервера в строку.
            val response = responseStream
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            // Если сервер вернул ошибочный HTTP-код,
            // выбрасываем исключение с текстом ответа.
            if (responseCode !in 200..299) {
                error("Ошибка загрузки ($responseCode): $response")
            }

            // Преобразуем JSON-ответ и получаем публичный URL изображения.
            JSONObject(response).getString("url")

        } finally {

            // Закрываем HTTP-соединение даже в случае ошибки.
            connection.disconnect()
        }
    }

    // Записывает текст в поток загрузки.
    // Используется при формировании multipart-запроса.
    private fun writeText(
        output: OutputStream,
        text: String
    ) {
        output.write(text.toByteArray(Charsets.UTF_8))
    }

    // Сохраняет новый аватар пользователя.
    private fun saveAvatar(uri: Uri) {

        // Проверяем, авторизован ли пользователь.
        val user = auth.currentUser

        if (user == null) {
            showToast("Сначала войди в аккаунт")
            return
        }

        // Запускаем загрузку в корутине.
        activity.lifecycleScope.launch {
            try {

                showToast("Загружаем аватар...")

                // Загружаем изображение в Supabase.
                // Получаем публичную ссылку на загруженный файл.
                val avatarUrl = uploadAvatarToSupabase(uri)

                // Сохраняем ссылку в Firestore.
                withContext(Dispatchers.IO) {

                    // Batch позволяет выполнить несколько операций записи
                    // как одну общую операцию.
                    val batch = db.batch()

                    // Обновляем приватный профиль пользователя.
                    batch.set(
                        db.collection("users").document(user.uid),
                        mapOf(
                            "avatarUrl" to avatarUrl,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )

                    // Обновляем публичный профиль.
                    // Благодаря этому другие пользователи тоже увидят новый аватар.
                    batch.set(
                        db.collection("publicProfiles").document(user.uid),
                        mapOf("avatarUrl" to avatarUrl),
                        SetOptions.merge()
                    )

                    // Отправляем пакет изменений в Firestore.
                    Tasks.await(batch.commit())
                }

                // Обновляем аватар на экране без повторного открытия профиля.
                showAvatar(avatarUrl)

                showToast("Аватар обновлён")

            } catch (e: Exception) {

                // Записываем ошибку загрузки аватара.
                Log.e(TAG, "Ошибка загрузки аватара", e)

                showToast(
                    "Не удалось загрузить аватар: " +
                            (e.localizedMessage ?: "неизвестная ошибка")
                )
            }
        }
    }

    // Сохраняет новую обложку профиля.
    private fun saveCover(uri: Uri) {

        // Проверяем наличие авторизованного пользователя.
        val user = auth.currentUser

        if (user == null) {
            showToast("Сначала войди в аккаунт")
            return
        }

        activity.lifecycleScope.launch {
            try {

                showToast("Загружаем обложку...")

                // Загружаем выбранное изображение в Supabase.
                val coverUrl = uploadAvatarToSupabase(uri)

                // Записываем ссылку в Firestore.
                withContext(Dispatchers.IO) {

                    val batch = db.batch()

                    // Обновляем приватный профиль.
                    batch.set(
                        db.collection("users").document(user.uid),
                        mapOf(
                            "coverUrl" to coverUrl,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )

                    // Обновляем публичный профиль.
                    batch.set(
                        db.collection("publicProfiles").document(user.uid),
                        mapOf("coverUrl" to coverUrl),
                        SetOptions.merge()
                    )

                    // Выполняем запись.
                    Tasks.await(batch.commit())
                }

                // Обновляем изображение обложки на экране.
                showCover(coverUrl)

                showToast("Обложка обновлена")

            } catch (e: Exception) {

                Log.e(TAG, "Ошибка загрузки обложки", e)

                showToast(
                    "Не удалось установить обложку: " +
                            (e.localizedMessage ?: "неизвестная ошибка")
                )
            }
        }
    }

    // Показывает диалог подтверждения выхода из аккаунта.
    private fun confirmLogout() {

        val dialog = AlertDialog.Builder(view.context)
            .setTitle("Выход из аккаунта")
            .setMessage("Ты действительно хочешь выйти?")

            // Кнопка отмены закрывает диалог без выхода.
            .setNegativeButton("Отмена", null)

            // При подтверждении выполняем выход из Firebase.
            .setPositiveButton("Выйти") { _, _ ->

                auth.signOut()

                // Открываем экран авторизации.
                activity.startActivity(
                    Intent(activity, LoginActivity::class.java)
                )

                // Закрываем MainActivity.
                activity.finish()
            }
            .create()

        // Настраиваем внешний вид диалога.
        dialog.setOnShowListener {
            styleDialog(dialog)
        }

        dialog.show()
    }

    // Показывает список доступных настроек профиля.
    private fun showSettingsDialog() {

        // Названия пунктов меню.
        val options = arrayOf(
            "Редактировать профиль",
            "Изменить аватар",
            "Изменить обложку",
            "Информация о приложении",
            "Выйти из аккаунта"
        )

        val dialog = AlertDialog.Builder(view.context)
            .setTitle("Настройки GamerHub")

            // Обрабатываем выбор пункта меню.
            .setItems(options) { _, which ->

                when (which) {

                    // Открываем редактор профиля.
                    0 -> showEditDialog()

                    // Выбираем и сохраняем новый аватар.
                    1 -> activity.openAvatarPicker { uri ->
                        saveAvatar(uri)
                    }

                    // Выбираем и сохраняем новую обложку.
                    2 -> activity.openAvatarPicker { uri ->
                        saveCover(uri)
                    }

                    // Показываем информацию о приложении.
                    3 -> showAboutDialog()

                    // Подтверждаем выход из аккаунта.
                    4 -> confirmLogout()
                }
            }

            .setNegativeButton("Закрыть", null)
            .create()

        dialog.setOnShowListener {
            styleDialog(dialog)

            // Устанавливаем тёмный фон.
            dialog.window?.setBackgroundDrawable(ColorDrawable(dark))
        }

        dialog.show()
    }

    // Отображает информацию о приложении GamerHub.
    private fun showAboutDialog() {

        val dialog = AlertDialog.Builder(view.context)
            .setTitle("О GamerHub")
            .setMessage(
                "GamerHub — место для общения игроков, " +
                        "обсуждения игр и обмена игровым опытом."
            )
            .setPositiveButton("Понятно", null)
            .create()

        dialog.setOnShowListener {
            styleDialog(dialog)

            // Меняем цвет основного текста.
            dialog.findViewById<TextView>(
                android.R.id.message
            )?.setTextColor(lightGray)
        }

        dialog.show()
    }

    // Настраивает внешний вид AlertDialog.
    // Метод используется для оформления различных окон приложения.
    private fun styleDialog(dialog: AlertDialog) {

        // Получаем системный ID заголовка диалогового окна.
        val titleId = view.resources.getIdentifier(
            "alertTitle",
            "id",
            "android"
        )

        // Если заголовок найден, устанавливаем белый цвет текста.
        if (titleId != 0) {
            dialog.findViewById<TextView>(
                titleId
            )?.setTextColor(white)
        }

        // Создаём тёмный фон с закруглёнными углами.
        val background = GradientDrawable().apply {
            setColor(dark)

            cornerRadius =
                20 * view.resources.displayMetrics.density
        }

        // Устанавливаем фон диалога.
        dialog.window?.setBackgroundDrawable(background)

        // Меняем цвет текста сообщения.
        dialog.findViewById<TextView>(
            android.R.id.message
        )?.setTextColor(lightGray)

        // Если в диалоге есть список элементов, оформляем его.
        dialog.listView?.let { list ->

            list.setBackgroundColor(dark)

            // Разделители между элементами списка.
            list.divider = ColorDrawable(
                android.graphics.Color.rgb(48, 48, 57)
            )

            list.dividerHeight = 1

            // Меняем цвет текста уже созданных элементов списка.
            for (i in 0 until list.childCount) {
                (list.getChildAt(i) as? TextView)
                    ?.setTextColor(white)
            }
        }

        // Оформляем кнопки диалога.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(purple)

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            ?.setTextColor(purple)

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            ?.setTextColor(purple)
    }

    // Открывает окно редактирования профиля.
    private fun showEditDialog() {

        // Получаем элементы, которые показывают текущие данные профиля.
        val usernameView = view.findViewById<TextView>(
            R.id.profileUsername
        )

        val tagView = view.findViewById<TextView>(
            R.id.profileTag
        )

        val bioView = view.findViewById<TextView>(
            R.id.profileBio
        )

        // Проверяем авторизацию.
        val user = auth.currentUser

        if (user == null) {
            showToast("Не удалось определить аккаунт")
            return
        }

        // Загружаем необходимые данные для редактора.
        activity.lifecycleScope.launch {
            try {

                // Получаем профиль и каталог игр.
                // Операции выполняются в фоновом потоке.
                val data = withContext(Dispatchers.IO) {

                    // Загружаем документ профиля.
                    val profile = Tasks.await(
                        db.collection("users")
                            .document(user.uid)
                            .get()
                    )

                    // Получаем список игр из коллекции games.
                    val gamesSnapshot = Tasks.await(
                        db.collection("games").get()
                    )

                    // Из каждого документа получаем поле title.
                    // Убираем пустые и повторяющиеся названия.
                    val games = gamesSnapshot.documents
                        .mapNotNull { it.getString("title") }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()

                    // Получаем текущий список любимых игр.
                    val favorites = (
                            profile.get("favoriteGames") as? List<*>
                            ).orEmpty()
                        .filterIsInstance<String>()
                        .toSet()

                    // Возвращаем три результата:
                    // профиль, список всех игр и список любимых игр.
                    Triple(profile, games, favorites)
                }

                // Список всех игр из каталога.
                val gameTitles = data.second

                // MutableSet позволяет добавлять и удалять любимые игры.
                val selectedFavoriteGames = data.third.toMutableSet()

                // Создаём ScrollView, чтобы содержимое редактора
                // можно было прокручивать, если оно не помещается на экране.
                val scrollView = android.widget.ScrollView(view.context)

                // Основной вертикальный контейнер полей редактора.
                val fields = LinearLayout(view.context).apply {
                    orientation = LinearLayout.VERTICAL

                    val density = resources.displayMetrics.density

                    setPadding(
                        (20 * density).toInt(),
                        (8 * density).toInt(),
                        (20 * density).toInt(),
                        (12 * density).toInt()
                    )

                    setBackgroundColor(dark)
                }

                // Добавляем контейнер внутрь ScrollView.
                scrollView.addView(
                    fields,
                    android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )

                // Поле для изменения имени пользователя.
                val usernameInput = createEditText(
                    "Имя пользователя",
                    usernameView.text.toString()
                )

                // Поле для изменения тега.
                val tagInput = createEditText(
                    "@username",
                    tagView.text.toString()
                )

                // Поле для изменения описания.
                val bioInput = createEditText(
                    "О себе",
                    bioView.text.toString()
                ).apply {

                    // Делаем поле многострочным.
                    minLines = 3

                    // Текст начинается с верхней части поля.
                    gravity = Gravity.TOP
                }

                // Добавляем поля в редактор.
                fields.addView(usernameInput)
                fields.addView(tagInput)
                fields.addView(bioInput)

                // Раздел с игровыми ролями.
                fields.addView(
                    createSectionTitle("Мои роли")
                )

                // Получаем контейнер уже отображаемых ролей.
                val rolesContainer = view.findViewById<LinearLayout>(
                    R.id.profileRolesContainer
                )

                // Получаем текущие роли из отображаемых TextView.
                val currentRoles = (0 until rolesContainer.childCount)
                    .map { rolesContainer.getChildAt(it) }
                    .filterIsInstance<TextView>()
                    .map { it.text.toString() }
                    .toSet()

                // Здесь будут храниться CheckBox для каждой роли.
                // Ключ — название роли, значение — соответствующий CheckBox.
                val roleCheckboxes = mutableMapOf<String, CheckBox>()

                // Создаём переключатель для каждой доступной роли.
                availableRoles.forEach { role ->

                    val checkBox = CheckBox(view.context).apply {
                        text = role
                        setTextColor(white)

                        // Устанавливаем цвет переключателя.
                        buttonTintList = ColorStateList.valueOf(purple)

                        // Отмечаем роли, которые уже выбраны пользователем.
                        isChecked = role in currentRoles
                    }

                    // Сохраняем CheckBox в коллекции.
                    roleCheckboxes[role] = checkBox

                    // Добавляем переключатель в редактор.
                    fields.addView(checkBox)
                }

                // Раздел любимых игр.
                fields.addView(
                    createSectionTitle("Любимые игры")
                )

                // Контейнер выбранных любимых игр.
                val selectedGamesContainer = LinearLayout(view.context).apply {
                    orientation = LinearLayout.VERTICAL
                }

                // Spinner — выпадающий список с доступными играми.
                val gameSpinner = Spinner(view.context)

                // Создаём адаптер для отображения названий игр в Spinner.
                val gameAdapter = ArrayAdapter(
                    activity,
                    android.R.layout.simple_spinner_item,
                    gameTitles
                ).apply {

                    // Определяем внешний вид раскрытого списка.
                    setDropDownViewResource(
                        android.R.layout.simple_spinner_dropdown_item
                    )
                }

                // Подключаем адаптер к Spinner.
                gameSpinner.adapter = gameAdapter

                // Кнопка добавления выбранной игры.
                val addGameButton = TextView(view.context).apply {
                    text = "+ Добавить выбранную игру"
                    textSize = 14f
                    setTextColor(purple)
                    setPadding(0, 12, 0, 12)
                    gravity = Gravity.CENTER_VERTICAL

                    isClickable = true
                    isFocusable = true
                }

                // Обновляет список любимых игр в окне редактирования.
                fun renderSelectedGames() {

                    // Удаляем предыдущие элементы.
                    selectedGamesContainer.removeAllViews()

                    // Если игры ещё не выбраны, показываем сообщение.
                    if (selectedFavoriteGames.isEmpty()) {
                        selectedGamesContainer.addView(
                            TextView(view.context).apply {
                                text = "Игры пока не выбраны"
                                textSize = 13f
                                setTextColor(lightGray)
                                setPadding(0, 8, 0, 8)
                            }
                        )
                        return
                    }

                    // Создаём отдельную строку для каждой выбранной игры.
                    selectedFavoriteGames.toList().forEach { game ->

                        val gameRow = LinearLayout(view.context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(0, 4, 0, 4)
                        }

                        // Название игры.
                        val gameName = TextView(view.context).apply {
                            text = game
                            textSize = 14f
                            setTextColor(white)

                            // Занимаем всё свободное место в строке.
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            )
                        }

                        // Кнопка удаления игры из списка.
                        val removeButton = TextView(view.context).apply {
                            text = "✕"
                            textSize = 16f
                            setTextColor(purple)
                            setPadding(16, 8, 8, 8)

                            setOnClickListener {

                                // Удаляем игру из набора.
                                selectedFavoriteGames.remove(game)

                                // Перерисовываем список.
                                renderSelectedGames()
                            }
                        }

                        // Добавляем название и кнопку удаления в строку.
                        gameRow.addView(gameName)
                        gameRow.addView(removeButton)

                        // Добавляем строку в общий контейнер.
                        selectedGamesContainer.addView(gameRow)
                    }
                }

                // Обработчик добавления любимой игры.
                addGameButton.setOnClickListener {

                    // Получаем выбранную игру из Spinner.
                    val selectedGame = gameSpinner.selectedItem?.toString()

                    // Проверяем, выбрана ли игра.
                    if (selectedGame.isNullOrBlank()) {
                        showToast("В каталоге нет игр")
                        return@setOnClickListener
                    }

                    // Добавляем игру в набор.
                    // add() возвращает false, если такая игра уже есть.
                    if (!selectedFavoriteGames.add(selectedGame)) {

                        showToast("Эта игра уже добавлена")

                    } else {

                        // Обновляем список после добавления.
                        renderSelectedGames()
                    }
                }

                // Если каталог игр пустой, показываем сообщение.
                if (gameTitles.isEmpty()) {
                    fields.addView(
                        TextView(view.context).apply {
                            text = "В каталоге games не найдено игр с полем title"
                            setTextColor(lightGray)
                            textSize = 13f
                            setPadding(0, 8, 0, 8)
                        }
                    )
                } else {

                    // Если игры есть, показываем Spinner и кнопку добавления.
                    fields.addView(gameSpinner)
                    fields.addView(addGameButton)
                }

                // Добавляем контейнер выбранных игр.
                fields.addView(selectedGamesContainer)

                // Первоначально отображаем уже сохранённые игры.
                renderSelectedGames()

                // Создаём диалог редактирования профиля.
                val dialog = AlertDialog.Builder(view.context)
                    .setTitle("Редактировать профиль")
                    .setView(scrollView)
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Сохранить", null)
                    .create()

                // Настраиваем действия после отображения диалога.
                dialog.setOnShowListener {

                    styleDialog(dialog)

                    // Получаем кнопку сохранения.
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener {

                            // Получаем новые значения из полей.
                            val username = usernameInput.text
                                .toString()
                                .trim()

                            val tag = tagInput.text
                                .toString()
                                .trim()

                            val bio = bioInput.text
                                .toString()
                                .trim()

                            // Проверяем обязательные поля.
                            if (username.isBlank() || tag.isBlank()) {
                                showToast("Имя и тег не должны быть пустыми")
                                return@setOnClickListener
                            }

                            // Если пользователь не добавил @ в начале тега,
                            // добавляем его автоматически.
                            val normalizedTag =
                                if (tag.startsWith("@")) tag else "@$tag"

                            // Собираем выбранные роли в одну строку.
                            // Например: "🎮 Геймер|💻 Разработчик".
                            val selectedRoles = roleCheckboxes
                                .filterValues { it.isChecked }
                                .keys
                                .joinToString("|")

                            // Проверяем, выбрал ли пользователь хотя бы одну роль.
                            if (selectedRoles.isBlank()) {
                                showToast("Выбери хотя бы одну роль")
                                return@setOnClickListener
                            }

                            // Сохраняем обновлённые данные профиля.
                            saveProfileChanges(
                                username = username,
                                tag = normalizedTag,
                                bio = bio,
                                roles = selectedRoles,
                                favoriteGames = selectedFavoriteGames.toList(),

                                // Этот блок выполнится после успешной записи.
                                onSuccess = {

                                    // Обновляем данные на экране.
                                    usernameView.text = username
                                    tagView.text = normalizedTag
                                    bioView.text = bio

                                    // Перерисовываем роли и любимые игры.
                                    renderRoles(selectedRoles)

                                    renderFavoriteGames(
                                        selectedFavoriteGames.toList()
                                    )

                                    showToast("Профиль сохранён")

                                    // Закрываем диалог.
                                    dialog.dismiss()
                                }
                            )
                        }
                }

                // Показываем диалог редактирования.
                dialog.show()

            } catch (e: Exception) {

                // Обрабатываем ошибку загрузки данных для редактора.
                Log.e(TAG, "Ошибка загрузки редактора профиля", e)

                showToast(
                    "Не удалось загрузить профиль или игры: " +
                            (e.localizedMessage ?: "неизвестная ошибка")
                )
            }
        }
    }

    // Создаёт заголовок раздела в окне редактирования.
    private fun createSectionTitle(title: String): TextView {
        return TextView(view.context).apply {
            text = title
            textSize = 16f
            setTextColor(white)

            // Добавляем отступы вокруг заголовка.
            val density = resources.displayMetrics.density

            setPadding(
                0,
                (20 * density).toInt(),
                0,
                (8 * density).toInt()
            )
        }
    }

    // Создаёт текстовое поле EditText с заданной подсказкой и значением.
    private fun createEditText(
        hintText: String,
        value: String
    ): EditText {
        return EditText(view.context).apply {

            // Подсказка, которая отображается при пустом поле.
            hint = hintText

            // Текущее значение.
            setText(value)

            // Цвет вводимого текста.
            setTextColor(white)

            // Цвет подсказки.
            setHintTextColor(lightGray)

            // Цвет нижней линии поля ввода.
            backgroundTintList = ColorStateList.valueOf(purple)
        }
    }

    // Сохраняет изменения профиля в Firestore.
    //
    // Приватные данные записываются в users/{uid}.
    // Публичные данные записываются в publicProfiles/{uid}.
    private fun saveProfileChanges(
        username: String,
        tag: String,
        bio: String,
        roles: String,
        favoriteGames: List<String>,
        onSuccess: () -> Unit
    ) {

        // Получаем UID пользователя.
        val uid = auth.currentUser?.uid

        if (uid == null) {
            showToast("Не удалось определить аккаунт")
            return
        }

        activity.lifecycleScope.launch {
            try {

                // Все операции с Firestore выполняем в фоновом потоке.
                withContext(Dispatchers.IO) {

                    // Создаём пакет изменений.
                    val batch = db.batch()

                    // Данные приватного профиля.
                    val privateProfile = hashMapOf<String, Any>(
                        "uid" to uid,
                        "username" to username,
                        "tag" to tag,
                        "bio" to bio,
                        "roles" to roles,
                        "favoriteGames" to favoriteGames,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )

                    // Данные публичного профиля.
                    // Здесь сохраняются поля, которые могут быть доступны
                    // другим пользователям приложения.
                    val publicProfile = hashMapOf<String, Any>(
                        "uid" to uid,
                        "username" to username,
                        "tag" to tag,
                        "bio" to bio,
                        "roles" to roles,
                        "favoriteGames" to favoriteGames
                    )

                    // Обновляем приватный профиль.
                    // SetOptions.merge() сохраняет поля,
                    // которые не указаны в передаваемой карте.
                    batch.set(
                        db.collection("users").document(uid),
                        privateProfile,
                        SetOptions.merge()
                    )

                    // Обновляем публичный профиль.
                    batch.set(
                        db.collection("publicProfiles").document(uid),
                        publicProfile,
                        SetOptions.merge()
                    )

                    // Выполняем обе записи.
                    Tasks.await(batch.commit())
                }

                // Вызываем callback после успешного сохранения.
                onSuccess()

            } catch (e: Exception) {

                // Записываем ошибку сохранения в Logcat.
                Log.e(TAG, "Ошибка сохранения профиля", e)

                showToast(
                    "Ошибка сохранения: " +
                            (e.localizedMessage ?: e.javaClass.simpleName)
                )
            }
        }
    }

    // Универсальный метод для показа Toast-сообщений.
    private fun showToast(message: String) {
        Toast.makeText(
            view.context,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    // Константы класса.
    // companion object позволяет обращаться к ним без создания объекта ProfileManager.
    private companion object {

        // Тег для поиска сообщений этого класса в Logcat.
        const val TAG = "GamerHubProfile"

        // Максимальный допустимый размер изображения — 5 МБ.
        const val MAX_IMAGE_SIZE = 5 * 1024 * 1024

        // Адрес Supabase Edge Function, которая принимает изображения.
        const val SUPABASE_FUNCTION_URL =
            "https://rewxoagdwwstvuuiqtqc.supabase.co/functions/v1/funk"
    }
}