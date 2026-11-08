package com.example.gamerhub // Указывает пакет, к которому относится файл.

import android.graphics.Color // Предоставляет готовые цвета и инструменты для работы с ними.
import android.graphics.Typeface // Позволяет настраивать начертание шрифта.
import android.graphics.drawable.GradientDrawable // Позволяет создавать фоны с цветом и закруглёнными углами.
import android.view.Gravity // Управляет выравниванием элементов интерфейса.
import android.view.View // Базовый класс всех элементов интерфейса Android.
import android.widget.EditText // Поле для ввода текста.
import android.widget.ImageView // Компонент для отображения изображений.
import android.widget.LinearLayout // Контейнер для размещения элементов по горизонтали или вертикали.
import android.widget.TextView // Компонент для отображения текста.
import android.widget.Toast // Позволяет показывать короткие всплывающие сообщения.
import androidx.appcompat.app.AppCompatActivity // Базовый класс Activity с поддержкой дополнительных возможностей.
import androidx.lifecycle.lifecycleScope // Позволяет запускать корутины, связанные с жизненным циклом Activity.
import com.bumptech.glide.Glide // Библиотека для загрузки изображений по ссылке.
import com.google.firebase.Timestamp // Тип данных Firebase для хранения даты и времени.
import com.google.firebase.auth.FirebaseAuth // Предоставляет доступ к данным авторизованного пользователя.
import com.google.firebase.firestore.FieldValue // Позволяет использовать специальные значения Firestore, например время сервера.
import com.google.firebase.firestore.FirebaseFirestore // Подключает облачную базу данных Firestore.
import com.google.firebase.firestore.ListenerRegistration // Позволяет управлять слушателями изменений Firestore.
import com.google.firebase.firestore.Query // Предоставляет параметры для настройки запросов к Firestore.
import kotlinx.coroutines.launch // Позволяет запускать корутины.
import kotlinx.coroutines.tasks.await // Позволяет ожидать завершения Firebase Task внутри корутины.
import java.text.SimpleDateFormat // Позволяет преобразовывать дату и время в строку заданного формата.
import java.util.Locale // Используется для учёта региональных настроек устройства.

// Менеджер отвечает за загрузку, отображение и отправку комментариев к публикации.
class CommentsManager(
    private val activity: AppCompatActivity, // Activity, на которой отображаются комментарии.
    private val postId: String // ID публикации, к которой относятся комментарии.
) {

    private val db = FirebaseFirestore.getInstance() // Получает экземпляр облачной базы данных Firestore.
    private var commentsListener: ListenerRegistration? = null // Хранит слушатель изменений комментариев; изначально отсутствует.

    // Настраивает отображение комментариев и обработчик кнопки отправки.
    fun setup(view: View) {

        // Находит контейнер, в который будут добавляться карточки комментариев.
        val container = view.findViewById<LinearLayout>(
            R.id.commentsContainer // ID контейнера из XML-разметки.
        ) ?: return // Если контейнер не найден, прекращает выполнение функции.

        // Находит текстовый элемент для сообщений о состоянии списка комментариев.
        val emptyText = view.findViewById<TextView>(
            R.id.commentsEmptyText // ID текста пустого состояния из XML.
        ) ?: return // Если элемент отсутствует, прекращает настройку.

        // Находит поле, в которое пользователь вводит комментарий.
        val input = view.findViewById<EditText>(
            R.id.commentInput // ID поля ввода из XML.
        ) ?: return // Если поле не найдено, прекращает выполнение.

        // Находит кнопку отправки комментария.
        val sendButton = view.findViewById<TextView>(
            R.id.sendCommentButton // ID кнопки отправки из XML.
        ) ?: return // Если кнопка отсутствует, прекращает настройку.

        // Проверяет, передан ли корректный ID публикации.
        if (postId.isBlank()) {
            emptyText.text = "Не удалось определить публикацию" // Показывает сообщение об ошибке.
            emptyText.visibility = View.VISIBLE // Делает сообщение видимым.
            sendButton.isEnabled = false // Запрещает отправлять комментарии без ID публикации.
            return // Завершает настройку.
        }

        val user = FirebaseAuth.getInstance().currentUser // Получает данные текущего авторизованного пользователя.

        // Проверяет, выполнил ли пользователь вход в аккаунт.
        if (user == null) {
            input.isEnabled = false // Отключает поле ввода комментария.
            input.hint = "Войди, чтобы комментировать" // Показывает подсказку вместо возможности ввода.
            sendButton.isEnabled = false // Отключает кнопку отправки.
        }

        // Получает ссылку на коллекцию комментариев конкретной публикации.
        val comments = db.collection("posts") // Обращается к коллекции публикаций.
            .document(postId) // Выбирает документ нужной публикации.
            .collection("comments") // Переходит к вложенной коллекции комментариев.

        // Подписывается на изменения комментариев в реальном времени.
        commentsListener = comments
            .orderBy("createdAt", Query.Direction.ASCENDING) // Сортирует комментарии по времени от старых к новым.
            .addSnapshotListener { snapshot, error -> // Получает обновления коллекции при изменении данных.

                // Проверяет, возникла ли ошибка при получении комментариев.
                if (error != null) {
                    emptyText.text = "Не удалось загрузить комментарии" // Показывает сообщение об ошибке.
                    emptyText.visibility = View.VISIBLE // Делает сообщение видимым.

                    // Записывает подробную информацию об ошибке в Logcat.
                    android.util.Log.e(
                        "CommentsManager", // Тег, по которому можно найти сообщение в Logcat.
                        "Ошибка загрузки комментариев", // Описание произошедшей ошибки.
                        error // Сам объект ошибки.
                    )

                    return@addSnapshotListener // Завершает обработку текущего обновления.
                }

                if (snapshot == null) return@addSnapshotListener // Если данные отсутствуют, прекращает обработку.

                container.removeAllViews() // Удаляет старые карточки перед отображением актуального списка.

                val documents = snapshot.documents // Получает список документов с комментариями.

                // Показывает сообщение, если комментариев нет, и скрывает его в противном случае.
                emptyText.visibility = if (documents.isEmpty()) {
                    View.VISIBLE // Показывает элемент пустого состояния.
                } else {
                    View.GONE // Полностью скрывает элемент, если комментарии существуют.
                }

                // Перебирает все документы комментариев.
                documents.forEach { document ->

                    val authorId = document.getString("authorId").orEmpty() // Получает ID автора комментария или пустую строку.

                    // Получает имя автора и проверяет, что оно не пустое.
                    val author = document.getString("authorName")
                        ?.trim() // Убирает пробелы в начале и конце имени.
                        ?.takeIf { it.isNotBlank() } // Оставляет имя только в том случае, если оно содержит символы.
                        ?: "Игрок GamerHub" // Использует стандартное имя, если имя отсутствует.

                    val text = document.getString("text").orEmpty() // Получает текст комментария или пустую строку.
                    val timestamp = document.getTimestamp("createdAt") // Получает дату и время создания комментария.

                    // Получает ссылку на аватар, сохранённую непосредственно в комментарии.
                    val avatarUrl = document
                        .getString("authorAvatarUrl") // Читает поле со ссылкой на аватар.
                        .orEmpty() // Если ссылки нет, заменяет null пустой строкой.

                    // Создаёт визуальную карточку комментария с полученными данными.
                    val commentView = createCommentView(
                        author = author, // Передаёт имя автора.
                        authorId = authorId, // Передаёт ID автора.
                        avatarUrl = avatarUrl, // Передаёт ссылку на аватар.
                        commentText = text, // Передаёт текст комментария.
                        timestamp = timestamp // Передаёт дату создания комментария.
                    )

                    container.addView(commentView) // Добавляет готовую карточку в общий список комментариев.
                }
            }

        // Настраивает действие при нажатии на кнопку отправки комментария.
        sendButton.setOnClickListener {

            val currentUser = FirebaseAuth.getInstance().currentUser // Получает данные пользователя непосредственно перед отправкой.

            // Проверяет авторизацию ещё раз, чтобы не отправить комментарий от неизвестного пользователя.
            if (currentUser == null) {
                Toast.makeText(
                    activity, // Контекст экрана, на котором показывается уведомление.
                    "Войди, чтобы оставить комментарий", // Текст уведомления.
                    Toast.LENGTH_SHORT // Короткая продолжительность показа.
                ).show() // Показывает уведомление.

                return@setOnClickListener // Прекращает обработчик нажатия.
            }

            val commentText = input.text.toString().trim() // Получает введённый текст и удаляет пробелы по краям.

            // Проверяет, не пустой ли комментарий.
            if (commentText.isBlank()) {
                input.error = "Напиши комментарий" // Показывает ошибку возле поля ввода.
                return@setOnClickListener // Не продолжает отправку.
            }

            // Ограничивает длину комментария одной тысячей символов.
            if (commentText.length > 1000) {
                input.error = "Максимум 1000 символов" // Показывает сообщение о превышении лимита.
                return@setOnClickListener // Прерывает отправку.
            }

            sendButton.isEnabled = false // Временно отключает кнопку, чтобы избежать повторных нажатий.

            // Запускает корутину, чтобы выполнять асинхронные операции Firebase.
            activity.lifecycleScope.launch {
                try {
                    // Получаем актуальные данные пользователя из публичного профиля.
                    val profile = db.collection("publicProfiles") // Обращается к коллекции публичных профилей.
                        .document(currentUser.uid) // Выбирает профиль текущего пользователя.
                        .get() // Запрашивает документ профиля.
                        .await() // Ожидает завершения запроса, не блокируя основной поток.

                    // Получает имя автора из профиля.
                    val authorName = profile.getString("username")
                        ?.trim() // Убирает пробелы по краям имени.
                        ?.takeIf { it.isNotBlank() } // Проверяет, что имя не пустое.
                        ?: "Игрок GamerHub" // Использует стандартное имя, если имя отсутствует.

                    // Получает ссылку на аватар пользователя.
                    val authorAvatarUrl = profile.getString("avatarUrl")
                        ?.takeIf { it.isNotBlank() } // Использует avatarUrl, если поле заполнено.
                        ?: profile.getString("avatarUri") // Если avatarUrl отсутствует, проверяет avatarUri.
                            ?.takeIf { it.isNotBlank() } // Использует avatarUri, если оно заполнено.
                        ?: "" // Если обе ссылки отсутствуют, сохраняет пустую строку.

                    // Формирует данные комментария для сохранения в Firestore.
                    val comment = hashMapOf(
                        "text" to commentText, // Сохраняет текст комментария.
                        "authorId" to currentUser.uid, // Сохраняет ID пользователя, написавшего комментарий.
                        "authorName" to authorName, // Сохраняет имя автора.
                        "authorAvatarUrl" to authorAvatarUrl, // Сохраняет ссылку на аватар автора.
                        "createdAt" to FieldValue.serverTimestamp() // Записывает время создания по часам сервера Firebase.
                    )

                    // Сохраняет новый комментарий во вложенную коллекцию публикации.
                    comments.add(comment).await() // Добавляет документ и ожидает завершения записи.

                    input.text.clear() // Очищает поле ввода после успешного сохранения комментария.

                    // Создаёт уведомление для автора публикации.
                    createCommentNotification(
                        actorUid = currentUser.uid, // Передаёт ID пользователя, оставившего комментарий.
                        actorName = authorName, // Передаёт имя комментатора.
                        commentText = commentText // Передаёт текст комментария для уведомления.
                    )

                    // Показывает сообщение об успешной публикации комментария.
                    Toast.makeText(
                        activity, // Контекст текущего экрана.
                        "Комментарий опубликован", // Текст уведомления.
                        Toast.LENGTH_SHORT // Короткая длительность показа.
                    ).show() // Показывает уведомление.

                } catch (e: Exception) {
                    // Записывает подробную информацию об ошибке отправки в Logcat.
                    android.util.Log.e(
                        "CommentsManager", // Тег для поиска сообщения в Logcat.
                        "Ошибка отправки комментария", // Описание ошибки.
                        e // Объект исключения с подробностями.
                    )

                    // Сообщает пользователю, что отправить комментарий не удалось.
                    Toast.makeText(
                        activity, // Контекст текущего экрана.
                        "Не удалось отправить комментарий", // Текст уведомления.
                        Toast.LENGTH_SHORT // Короткая длительность показа.
                    ).show() // Показывает уведомление.

                } finally {
                    // Включает кнопку обратно после завершения отправки или возникновения ошибки.
                    sendButton.isEnabled =
                        FirebaseAuth.getInstance().currentUser != null // Кнопка доступна только авторизованному пользователю.
                }
            }
        }
    }

    // Создаёт уведомление для автора публикации о новом комментарии.
    private fun createCommentNotification(
        actorUid: String, // ID пользователя, который оставил комментарий.
        actorName: String, // Имя пользователя, который оставил комментарий.
        commentText: String // Текст оставленного комментария.
    ) {

        // Получает данные публикации, чтобы определить её автора.
        db.collection("posts") // Обращается к коллекции публикаций.
            .document(postId) // Выбирает публикацию, к которой оставили комментарий.
            .get() // Загружает документ публикации.
            .addOnSuccessListener { postDocument -> // Выполняется после успешной загрузки документа.

                // Проверяет, существует ли публикация.
                if (!postDocument.exists()) {
                    return@addOnSuccessListener // Если публикации нет, уведомление создавать не нужно.
                }

                // Получает ID автора публикации.
                val postAuthorId = postDocument.getString("authorId")
                    ?: return@addOnSuccessListener // Если ID отсутствует, завершает создание уведомления.

                // Не уведомляем пользователя о собственном комментарии.
                if (postAuthorId.isBlank() || postAuthorId == actorUid) {
                    return@addOnSuccessListener // Прекращает выполнение, если автор публикации оставил комментарий сам.
                }

                val postTitle = postDocument.getString("title").orEmpty() // Получает название публикации или пустую строку.

                // Формирует данные уведомления.
                val notification = hashMapOf(
                    "type" to "comment", // Указывает тип уведомления — комментарий.
                    "actorId" to actorUid, // Сохраняет ID пользователя, оставившего комментарий.
                    "actorName" to actorName, // Сохраняет имя комментатора.
                    "recipientId" to postAuthorId, // Указывает ID пользователя, которому предназначено уведомление.
                    "postId" to postId, // Сохраняет ID публикации, к которой оставили комментарий.
                    "postTitle" to postTitle, // Сохраняет название публикации.
                    "commentText" to commentText, // Сохраняет текст комментария.
                    "createdAt" to FieldValue.serverTimestamp(), // Сохраняет время создания уведомления по часам сервера.
                    "read" to false // Отмечает уведомление как непрочитанное.
                )

                // Переходит к коллекции уведомлений автора публикации.
                db.collection("users") // Обращается к коллекции пользовательских данных.
                    .document(postAuthorId) // Выбирает документ автора публикации.
                    .collection("notifications") // Переходит к вложенной коллекции уведомлений.
                    .add(notification) // Добавляет новое уведомление.
                    .addOnFailureListener { error -> // Обрабатывает ошибку, если уведомление не удалось сохранить.
                        android.util.Log.e(
                            "CommentsManager", // Тег для поиска ошибки в Logcat.
                            "Не удалось создать уведомление", // Описание ошибки.
                            error // Объект ошибки.
                        )
                    }
            }
            .addOnFailureListener { error -> // Обрабатывает ошибку загрузки самой публикации.
                android.util.Log.e(
                    "CommentsManager", // Тег для поиска ошибки в Logcat.
                    "Не удалось загрузить публикацию", // Описание ошибки.
                    error // Объект ошибки.
                )
            }
    }

    // Создаёт и оформляет одну карточку комментария.
    private fun createCommentView(
        author: String, // Имя автора комментария.
        authorId: String, // ID автора комментария.
        avatarUrl: String, // Ссылка на сохранённый аватар автора.
        commentText: String, // Текст комментария.
        timestamp: Timestamp? // Дата и время создания комментария; может отсутствовать.
    ): View { // Функция возвращает готовый элемент интерфейса.

        // Создаёт основную карточку комментария.
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL // Располагает содержимое карточки вертикально.
            setPadding(14, 12, 14, 12) // Задаёт внутренние отступы: слева, сверху, справа и снизу.

            // Создаёт фон карточки.
            background = GradientDrawable().apply {
                setColor(Color.rgb(26, 26, 32)) // Устанавливает тёмный цвет фона.
                cornerRadius = 14f // Закругляет углы карточки.
            }

            // Настраивает размеры и отступы карточки внутри родительского контейнера.
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, // Карточка занимает всю доступную ширину.
                LinearLayout.LayoutParams.WRAP_CONTENT // Высота зависит от содержимого.
            ).apply {
                bottomMargin = 10 // Добавляет отступ снизу между карточками.
            }
        }

        // Шапка комментария: аватар и ник.
        val authorRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL // Размещает аватар и имя по горизонтали.
            gravity = Gravity.CENTER_VERTICAL // Выравнивает элементы по вертикальному центру.
        }

        // Создаёт изображение аватара автора комментария.
        val avatar = ImageView(activity).apply {
            val size = (36 * resources.displayMetrics.density).toInt() // Переводит размер аватара из dp в пиксели с учётом плотности экрана.

            layoutParams = LinearLayout.LayoutParams(size, size) // Устанавливает ширину и высоту аватара.
            scaleType = ImageView.ScaleType.CENTER_CROP // Масштабирует изображение, заполняя область и обрезая лишние края.

            // Создаёт круглый фон для аватара.
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL // Устанавливает овальную форму, которая при одинаковых размерах становится кругом.
                setColor(Color.rgb(45, 40, 60)) // Задаёт цвет фона на случай отсутствия изображения.
            }

            clipToOutline = true // Обрезает содержимое изображения по форме фона.
            setImageResource(R.drawable.ic_launcher_foreground) // Устанавливает стандартную картинку до загрузки аватара.
            contentDescription = "Аватар $author" // Добавляет описание изображения для специальных средств доступности.
        }

        // Создаёт текстовый элемент с именем автора.
        val authorView = TextView(activity).apply {
            text = author // Устанавливает переданное имя автора.
            textSize = 14f // Задаёт размер текста.
            setTextColor(Color.WHITE) // Устанавливает белый цвет текста.
            setTypeface(null, Typeface.BOLD) // Делает имя автора жирным.

            // Добавляет отступ слева от аватара.
            setPadding(
                (10 * resources.displayMetrics.density).toInt(), // Отступ слева, переведённый из dp в пиксели.
                0, // Отступ сверху.
                0, // Отступ справа.
                0 // Отступ снизу.
            )
        }

        authorRow.addView(avatar) // Добавляет аватар в строку автора.
        authorRow.addView(authorView) // Добавляет имя рядом с аватаром.

        // Загружаем актуальные имя и аватар из публичного профиля.
        if (authorId.isNotBlank()) { // Проверяет, что ID автора существует.
            activity.lifecycleScope.launch { // Запускает корутину, связанную с жизненным циклом Activity.
                try {
                    // Загружает актуальный публичный профиль автора комментария.
                    val profile = db.collection("publicProfiles") // Обращается к коллекции публичных профилей.
                        .document(authorId) // Выбирает профиль автора комментария.
                        .get() // Запрашивает документ профиля.
                        .await() // Ожидает завершения запроса, не блокируя основной поток.

                    // Получает актуальное имя пользователя из профиля.
                    val profileName = profile.getString("username")
                        ?.trim() // Убирает пробелы по краям имени.
                        ?.takeIf { it.isNotBlank() } // Оставляет имя, только если оно не пустое.

                    // Получает ссылку на актуальный аватар.
                    val profileAvatar = profile.getString("avatarUrl")
                        ?.takeIf { it.isNotBlank() } // Использует avatarUrl, если он заполнен.
                        ?: profile.getString("avatarUri").orEmpty() // Если avatarUrl отсутствует, проверяет avatarUri.

                    // Обновляем ник, если он найден.
                    if (!profileName.isNullOrBlank()) {
                        authorView.text = profileName // Заменяет сохранённое имя актуальным именем из профиля.
                    }

                    // Сначала используем аватар из профиля.
                    // Если его нет, используем URL из комментария.
                    val finalAvatarUrl = profileAvatar
                        .takeIf { it.startsWith("https://") } // Выбирает ссылку из профиля, если она начинается с https://.
                        ?: avatarUrl.takeIf { // Если ссылки профиля нет, проверяет ссылку, сохранённую в комментарии.
                            it.startsWith("https://") // Оставляет её только при наличии безопасного HTTPS-адреса.
                        }

                    // Проверяет наличие ссылки и состояние Activity перед загрузкой изображения.
                    if (
                        !finalAvatarUrl.isNullOrBlank() && // Проверяет, что ссылка существует и не пустая.
                        !activity.isFinishing && // Проверяет, что Activity не завершает работу.
                        !activity.isDestroyed // Проверяет, что Activity ещё не уничтожена.
                    ) {
                        Glide.with(activity) // Создаёт запрос Glide с контекстом Activity.
                            .load(finalAvatarUrl) // Указывает адрес изображения.
                            .placeholder(R.drawable.ic_launcher_foreground) // Показывает стандартное изображение во время загрузки.
                            .error(R.drawable.ic_launcher_foreground) // Показывает стандартное изображение, если загрузка не удалась.
                            .circleCrop() // Обрезает загруженное изображение по кругу.
                            .into(avatar) // Помещает загруженное изображение в ImageView.
                    }

                } catch (e: Exception) {
                    // Записывает ошибку загрузки профиля в Logcat.
                    android.util.Log.e(
                        "CommentsManager", // Тег для поиска сообщения.
                        "Не удалось загрузить профиль комментатора", // Описание ошибки.
                        e // Объект исключения.
                    )

                    // Если профиль недоступен, используем сохранённый аватар.
                    if (
                        avatarUrl.startsWith("https://") && // Проверяет, что сохранённая ссылка начинается с HTTPS.
                        !activity.isFinishing && // Проверяет, что Activity не завершает работу.
                        !activity.isDestroyed // Проверяет, что Activity ещё не уничтожена.
                    ) {
                        Glide.with(activity) // Создаёт запрос загрузки изображения.
                            .load(avatarUrl) // Использует ссылку из документа комментария.
                            .placeholder(R.drawable.ic_launcher_foreground) // Показывает стандартный аватар во время загрузки.
                            .error(R.drawable.ic_launcher_foreground) // Показывает стандартный аватар при ошибке.
                            .circleCrop() // Обрезает изображение по кругу.
                            .into(avatar) // Загружает изображение в ImageView.
                    }
                }
            }
        } else if (avatarUrl.startsWith("https://")) { // Если ID автора отсутствует, проверяет сохранённую ссылку на аватар.

            // Для комментариев без authorId используем сохранённый аватар.
            if (!activity.isFinishing && !activity.isDestroyed) { // Проверяет, что Activity ещё доступна.
                Glide.with(activity) // Создаёт запрос Glide.
                    .load(avatarUrl) // Передаёт ссылку на сохранённый аватар.
                    .placeholder(R.drawable.ic_launcher_foreground) // Показывает стандартную картинку во время загрузки.
                    .error(R.drawable.ic_launcher_foreground) // Показывает стандартную картинку при ошибке.
                    .circleCrop() // Обрезает изображение по кругу.
                    .into(avatar) // Загружает изображение в аватар.
            }
        }

        // Создаёт текстовый элемент для содержимого комментария.
        val contentView = TextView(activity).apply {
            text = commentText // Устанавливает текст комментария.
            textSize = 14f // Задаёт размер текста.
            setTextColor(Color.rgb(220, 220, 225)) // Устанавливает светло-серый цвет текста.
            setPadding(0, 10, 0, 8) // Добавляет отступы вокруг текста.
        }

        // Создаёт текстовый элемент для даты публикации комментария.
        val dateView = TextView(activity).apply {
            text = timestamp?.toDate()?.let { date -> // Проверяет наличие временной метки и преобразует её в Date.
                SimpleDateFormat( // Создаёт форматировщик даты и времени.
                    "dd.MM.yyyy HH:mm", // Задаёт формат: день, месяц, год, часы и минуты.
                    Locale.getDefault() // Использует региональные настройки устройства.
                ).format(date) // Преобразует дату в строку.
            } ?: "Только что" // Если временная метка отсутствует, показывает текст «Только что».

            textSize = 11f // Устанавливает небольшой размер текста.
            setTextColor(Color.rgb(160, 160, 170)) // Задаёт серый цвет даты.
        }

        card.addView(authorRow) // Добавляет шапку с аватаром и именем в карточку.
        card.addView(contentView) // Добавляет текст комментария.
        card.addView(dateView) // Добавляет дату под текстом комментария.

        return card // Возвращает готовую карточку для добавления в контейнер.
    }

    // Отключает слушатель комментариев, когда экран больше не нуждается в обновлениях.
    fun cleanup() {
        commentsListener?.remove() // Удаляет слушатель Firestore, если он был создан.
        commentsListener = null // Очищает ссылку на слушатель, чтобы не хранить ненужный объект.
    }
}