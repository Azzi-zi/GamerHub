package com.example.gamerhub

import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Класс отвечает за работу с экраном отдельной публикации.
//
// Он позволяет:
// 1. Отображать информацию о публикации.
// 2. Показывать автора и его аватар.
// 3. Открывать профиль автора.
// 4. Редактировать и удалять собственные публикации.
// 5. Ставить и убирать лайки.
// 6. Создавать уведомления о лайках.
// 7. Отображать комментарии.
class PostDetailsManager(

    // Текущая Activity.
    // Нужна для доступа к интерфейсу, Context и жизненному циклу.
    private val activity: AppCompatActivity,

    // Публикация, которую необходимо отобразить.
    private val post: Post,

    // Функция возврата на предыдущий экран.
    private val onBack: () -> Unit,

    // Функция открытия публичного профиля автора.
    // В качестве параметра передаётся UID пользователя.
    private val onAuthorClick: (String) -> Unit = {},

    // Функция открытия редактора публикации.
    // Передаём туда публикацию, которую нужно изменить.
    private val onEditClick: (Post) -> Unit = {}

) {

    // Получаем экземпляр Firestore для работы с базой данных.
    private val db = FirebaseFirestore.getInstance()

    // Слушатель изменений коллекции лайков.
    // Позволяет автоматически обновлять количество лайков.
    private var likesListener: ListenerRegistration? = null

    // Менеджер комментариев.
    // Отвечает за загрузку, отображение и добавление комментариев.
    private var commentsManager: CommentsManager? = null

    // Хранит состояние лайка текущего пользователя.
    // true — лайк поставлен, false — лайка нет.
    private var isLiked = false

    // Защищает кнопку лайка от повторных нажатий,
    // пока предыдущий запрос к Firestore ещё выполняется.
    private var isLikeRequestInProgress = false


    // ---------------------------------------------------------
    // НАСТРОЙКА ЭКРАНА ПУБЛИКАЦИИ
    // ---------------------------------------------------------

    // Метод заполняет экран данными публикации
    // и подключает обработчики кнопок.
    fun setup(view: View) {

        // -----------------------------------------------------
        // КНОПКА ВОЗВРАТА
        // -----------------------------------------------------

        // Находим кнопку возврата.
        view.findViewById<TextView>(
            R.id.postDetailsBackButton
        ).setOnClickListener {

            // Вызываем переданную функцию возврата.
            onBack()
        }


        // -----------------------------------------------------
        // КНОПКА РЕДАКТИРОВАНИЯ
        // -----------------------------------------------------

        // Находим кнопку редактирования публикации.
        val editButton = view.findViewById<TextView>(
            R.id.postDetailsEditButton
        )

        // Проверяем, принадлежит ли публикация текущему пользователю.
        if (post.authorId == FirebaseAuth.getInstance().currentUser?.uid) {

            // Если это публикация текущего пользователя,
            // показываем кнопку редактирования.
            editButton.visibility = View.VISIBLE

            // Обрабатываем нажатие.
            editButton.setOnClickListener {

                // Передаём публикацию в редактор.
                onEditClick(post)
            }

        } else {

            // Если публикация принадлежит другому пользователю,
            // скрываем кнопку редактирования.
            editButton.visibility = View.GONE
        }


        // -----------------------------------------------------
        // КНОПКА УДАЛЕНИЯ
        // -----------------------------------------------------

        // Находим кнопку удаления публикации.
        val deleteButton = view.findViewById<TextView>(
            R.id.postDetailsDeleteButton
        )

        // Получаем UID текущего пользователя.
        val currentUid = FirebaseAuth.getInstance()
            .currentUser?.uid

        // Удалять публикацию может только её автор.
        if (
            post.authorId.isNotBlank() &&
            post.authorId == currentUid
        ) {

            // Показываем кнопку удаления.
            deleteButton.visibility = View.VISIBLE

            // Обрабатываем нажатие на кнопку.
            deleteButton.setOnClickListener {

                // Создаём диалог подтверждения удаления.
                AlertDialog.Builder(activity)

                    // Заголовок окна.
                    .setTitle("Удалить публикацию?")

                    // Предупреждение о последствиях.
                    .setMessage(
                        "Публикация будет удалена. Это действие нельзя отменить."
                    )

                    // Кнопка отмены.
                    .setNegativeButton("Отмена", null)

                    // Кнопка подтверждения удаления.
                    .setPositiveButton("Удалить") { _, _ ->

                        // Временно блокируем кнопку,
                        // чтобы пользователь не отправил запрос повторно.
                        deleteButton.isEnabled = false

                        // Запускаем корутину для выполнения запроса.
                        activity.lifecycleScope.launch {

                            try {

                                // Находим публикацию в Firestore
                                // и удаляем её по уникальному ID.
                                db.collection("posts")
                                    .document(post.id)
                                    .delete()
                                    .await()

                                // Показываем сообщение об успешном удалении.
                                Toast.makeText(
                                    activity,
                                    "Публикация удалена",
                                    Toast.LENGTH_SHORT
                                ).show()

                                // Возвращаемся на предыдущий экран.
                                onBack()

                            } catch (e: Exception) {

                                // Записываем информацию об ошибке в Logcat.
                                android.util.Log.e(
                                    "PostDetailsManager",
                                    "Ошибка удаления публикации",
                                    e
                                )

                                // Показываем сообщение об ошибке.
                                Toast.makeText(
                                    activity,
                                    "Не удалось удалить публикацию",
                                    Toast.LENGTH_SHORT
                                ).show()

                                // Разрешаем повторное нажатие.
                                deleteButton.isEnabled = true
                            }
                        }
                    }

                    // Показываем диалог.
                    .show()
            }

        } else {

            // Если пользователь не является автором,
            // скрываем кнопку удаления.
            deleteButton.visibility = View.GONE
        }


        // -----------------------------------------------------
        // ОБЛОЖКА ПУБЛИКАЦИИ
        // -----------------------------------------------------

        // Находим ImageView для изображения публикации.
        val image = view.findViewById<ImageView>(
            R.id.postDetailsImage
        )

        // Если ссылка на изображение существует,
        // загружаем его с помощью Glide.
        if (post.imageUrl.isNotBlank()) {

            Glide.with(activity)
                .load(post.imageUrl)

                // Обрезаем изображение под размеры ImageView.
                .centerCrop()

                // Устанавливаем изображение.
                .into(image)
        }


        // -----------------------------------------------------
        // ТИП ПУБЛИКАЦИИ
        // -----------------------------------------------------

        // Находим TextView с типом публикации.
        // uppercase() переводит текст в верхний регистр.
        view.findViewById<TextView>(
            R.id.postDetailsType
        ).text = post.postType.uppercase()


        // -----------------------------------------------------
        // ЗАГОЛОВОК ПУБЛИКАЦИИ
        // -----------------------------------------------------

        // Отображаем заголовок.
        view.findViewById<TextView>(
            R.id.postDetailsTitle
        ).text = post.title


        // -----------------------------------------------------
        // НАЗВАНИЕ ИГРЫ
        // -----------------------------------------------------

        // Отображаем название игры, если оно указано.
        //
        // Если название отсутствует, оставляем поле пустым.
        view.findViewById<TextView>(
            R.id.postDetailsGame
        ).text = if (post.game.isNotBlank()) {
            "🎮 ${post.game}"
        } else {
            ""
        }


        // -----------------------------------------------------
        // ИНФОРМАЦИЯ ОБ АВТОРЕ
        // -----------------------------------------------------

        // Находим TextView с именем автора.
        val authorView = view.findViewById<TextView>(
            R.id.postDetailsAuthor
        )

        // Находим ImageView с аватаром автора.
        val authorAvatar = view.findViewById<ImageView>(
            R.id.postDetailsAuthorAvatar
        )

        // Устанавливаем имя автора.
        //
        // Если имя пустое, показываем стандартное название.
        authorView.text = post.authorName.ifBlank {
            "Игрок GamerHub"
        }

        // При нажатии на имя автора открываем его профиль.
        authorView.setOnClickListener {

            // Проверяем, существует ли UID автора.
            if (post.authorId.isNotBlank()) {

                // Передаём UID в MainActivity.
                onAuthorClick(post.authorId)

            } else {

                // Если UID отсутствует, сообщаем об этом.
                Toast.makeText(
                    activity,
                    "Профиль автора недоступен",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }


        // -----------------------------------------------------
        // НАСТРОЙКА АВАТАРА АВТОРА
        // -----------------------------------------------------

        // Создаём круглую заглушку для аватара.
        authorAvatar.background = GradientDrawable().apply {

            // Устанавливаем круглую форму.
            shape = GradientDrawable.OVAL

            // Устанавливаем тёмный фиолетовый фон.
            setColor(android.graphics.Color.rgb(45, 40, 60))
        }

        // Обрезаем изображение по форме фона.
        authorAvatar.clipToOutline = true

        // Устанавливаем изображение по умолчанию.
        authorAvatar.setImageResource(
            R.drawable.ic_launcher_foreground
        )

        // При нажатии на аватар открываем профиль автора.
        authorAvatar.setOnClickListener {

            if (post.authorId.isNotBlank()) {
                onAuthorClick(post.authorId)
            }
        }


        // -----------------------------------------------------
        // ЗАГРУЗКА АВАТАРА АВТОРА
        // -----------------------------------------------------

        // Запускаем корутину для получения ссылки на аватар.
        activity.lifecycleScope.launch {

            // Пытаемся получить ссылку на изображение.
            val avatarUrl = try {

                // Сначала проверяем, есть ли готовая HTTPS-ссылка
                // непосредственно в объекте публикации.
                if (post.authorAvatarUrl.startsWith("https://")) {

                    post.authorAvatarUrl

                } else if (post.authorId.isNotBlank()) {

                    // Если ссылки нет, загружаем публичный профиль автора.
                    val document = db.collection("publicProfiles")
                        .document(post.authorId)
                        .get()
                        .await()

                    // Получаем ссылку на аватар из профиля.
                    //
                    // Сначала проверяем avatarUrl,
                    // затем avatarUri.
                    document.getString("avatarUrl")
                        ?: document.getString("avatarUri")
                        ?: ""

                } else {

                    // Если UID отсутствует, возвращаем пустую строку.
                    ""
                }

            } catch (e: Exception) {

                // Записываем ошибку загрузки аватара.
                android.util.Log.e(
                    "PostDetailsManager",
                    "Ошибка загрузки аватара автора",
                    e
                )

                // При ошибке используем пустую строку.
                ""
            }

            // Если получена HTTPS-ссылка,
            // загружаем изображение через Glide.
            if (avatarUrl.startsWith("https://")) {

                Glide.with(activity)
                    .load(avatarUrl)

                    // Изображение по умолчанию во время загрузки.
                    .placeholder(R.drawable.ic_launcher_foreground)

                    // Изображение при ошибке загрузки.
                    .error(R.drawable.ic_launcher_foreground)

                    // Обрезаем аватар по кругу.
                    .circleCrop()

                    // Устанавливаем изображение в ImageView.
                    .into(authorAvatar)
            }
        }


        // -----------------------------------------------------
        // ОСНОВНОЙ ТЕКСТ ПУБЛИКАЦИИ
        // -----------------------------------------------------

        // Находим TextView и устанавливаем содержимое публикации.
        view.findViewById<TextView>(
            R.id.postDetailsContent
        ).text = post.content


        // -----------------------------------------------------
        // ТЕГИ ПУБЛИКАЦИИ
        // -----------------------------------------------------

        // Объединяем все три списка тегов:
        //
        // gameTags — теги игр.
        // topicTags — тематические теги.
        // tags — общий список тегов.
        //
        // distinct() удаляет повторяющиеся элементы.
        val tags = (
                post.gameTags +
                        post.topicTags +
                        post.tags
                ).distinct()

        // Отображаем теги через разделитель " • ".
        //
        // Перед каждым тегом добавляется символ #.
        view.findViewById<TextView>(
            R.id.postDetailsTags
        ).text = tags.joinToString("  •  ") {
            "#$it"
        }


        // -----------------------------------------------------
        // ПОДКЛЮЧЕНИЕ СИСТЕМЫ ЛАЙКОВ
        // -----------------------------------------------------

        // Настраиваем кнопку лайка и подписку на изменения
        // количества лайков в Firestore.
        setupLikes(view)


        // -----------------------------------------------------
        // ПОДКЛЮЧЕНИЕ СИСТЕМЫ КОММЕНТАРИЕВ
        // -----------------------------------------------------

        // Создаём менеджер комментариев.
        // Передаём ему текущую Activity и ID публикации.
        commentsManager = CommentsManager(
            activity = activity,
            postId = post.id
        )

        // Настраиваем отображение и обработку комментариев.
        commentsManager?.setup(view)
    }


    // ---------------------------------------------------------
    // НАСТРОЙКА ЛАЙКОВ
    // ---------------------------------------------------------

    // Метод отвечает за получение, отображение и изменение лайков.
    private fun setupLikes(view: View) {

        // Находим кнопку лайка.
        val likeButton = view.findViewById<TextView>(
            R.id.postLikeButton
        )

        // Получаем UID текущего пользователя.
        val uid = FirebaseAuth.getInstance()
            .currentUser?.uid


        // -----------------------------------------------------
        // ПРОВЕРКА АВТОРИЗАЦИИ
        // -----------------------------------------------------

        // Если пользователь не вошёл в аккаунт,
        // запрещаем ему ставить лайки.
        if (uid == null) {

            // Блокируем кнопку.
            likeButton.isEnabled = false

            // Показываем начальное количество лайков.
            likeButton.text = "🤍 0"

            // При попытке нажать на кнопку показываем сообщение.
            likeButton.setOnClickListener {

                Toast.makeText(
                    activity,
                    "Войди, чтобы ставить лайки",
                    Toast.LENGTH_SHORT
                ).show()
            }

            return
        }


        // -----------------------------------------------------
        // ПРОВЕРКА ID ПУБЛИКАЦИИ
        // -----------------------------------------------------

        // Если ID публикации пустой, работать с лайками нельзя.
        if (post.id.isBlank()) {

            likeButton.isEnabled = false
            likeButton.text = "🤍 0"

            return
        }


        // -----------------------------------------------------
        // КОЛЛЕКЦИЯ ЛАЙКОВ FIRESTORE
        // -----------------------------------------------------

        // Открываем вложенную коллекцию likes конкретной публикации.
        //
        // Структура Firestore:
        //
        // posts
        //   -> ID публикации
        //       -> likes
        //           -> UID пользователя
        //
        // Каждый пользователь может иметь только один документ лайка.
        val likesCollection = db.collection("posts")
            .document(post.id)
            .collection("likes")


        // -----------------------------------------------------
        // ОТСЛЕЖИВАНИЕ ИЗМЕНЕНИЙ ЛАЙКОВ
        // -----------------------------------------------------

        // Подключаем слушатель Firestore.
        // Он автоматически сообщает об изменениях в коллекции likes.
        likesListener = likesCollection.addSnapshotListener { snapshot, error ->

            // Если произошла ошибка получения лайков,
            // показываем сообщение.
            if (error != null) {

                Toast.makeText(
                    activity,
                    "Ошибка загрузки лайков",
                    Toast.LENGTH_SHORT
                ).show()

                return@addSnapshotListener
            }

            // Если снимок данных отсутствует, ничего не делаем.
            if (snapshot == null) {
                return@addSnapshotListener
            }

            // Получаем настоящее количество лайков.
            val count = snapshot.size()

            // Проверяем, есть ли среди документов лайк текущего пользователя.
            //
            // ID каждого документа совпадает с UID пользователя.
            isLiked = snapshot.documents.any {
                it.id == uid
            }

            // Обновляем текст кнопки.
            //
            // Если пользователь уже поставил лайк,
            // показываем закрашенное сердце.
            //
            // Если лайка нет — незакрашенное.
            likeButton.text = if (isLiked) {
                "❤️ $count"
            } else {
                "🤍 $count"
            }
        }


        // -----------------------------------------------------
        // ОБРАБОТКА НАЖАТИЯ НА ЛАЙК
        // -----------------------------------------------------

        likeButton.setOnClickListener {

            // Если предыдущий запрос ещё выполняется,
            // игнорируем повторное нажатие.
            if (isLikeRequestInProgress) {
                return@setOnClickListener
            }

            // Отмечаем, что запрос начался.
            isLikeRequestInProgress = true

            // Временно блокируем кнопку.
            likeButton.isEnabled = false

            // Получаем документ лайка текущего пользователя.
            val myLike = likesCollection.document(uid)


            // -------------------------------------------------
            // ПРОВЕРКА ТЕКУЩЕГО СОСТОЯНИЯ ЛАЙКА
            // -------------------------------------------------

            // Проверяем, существует ли документ лайка в Firestore.
            myLike.get()

                .addOnSuccessListener { document ->

                    // Если документ существует,
                    // пользователь уже поставил лайк.
                    val operation = if (document.exists()) {

                        // Удаляем документ, тем самым снимая лайк.
                        myLike.delete()

                    } else {

                        // Если документа нет, создаём новый лайк.
                        myLike.set(
                            mapOf(

                                // UID пользователя, поставившего лайк.
                                "uid" to uid,

                                // Время создания лайка.
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        ).addOnSuccessListener {

                            // После успешного сохранения лайка
                            // создаём уведомление автору публикации.
                            createLikeNotification(uid)
                        }
                    }

                    // Обрабатываем результат операции удаления или добавления.
                    operation

                        // Если запрос завершился ошибкой,
                        // показываем сообщение.
                        .addOnFailureListener {

                            Toast.makeText(
                                activity,
                                "Не удалось сохранить лайк",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        // После завершения запроса
                        // снова разрешаем нажимать на кнопку.
                        .addOnCompleteListener {

                            isLikeRequestInProgress = false
                            likeButton.isEnabled = true
                        }
                }

                // Если не удалось даже получить документ лайка,
                // обрабатываем ошибку отдельно.
                .addOnFailureListener {

                    // Сбрасываем состояние запроса.
                    isLikeRequestInProgress = false

                    // Разблокируем кнопку.
                    likeButton.isEnabled = true

                    Toast.makeText(
                        activity,
                        "Ошибка доступа к лайкам",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }


    // ---------------------------------------------------------
    // СОЗДАНИЕ УВЕДОМЛЕНИЯ О ЛАЙКЕ
    // ---------------------------------------------------------

    // Метод создаёт уведомление для автора публикации,
    // когда другой пользователь ставит ей лайк.
    private fun createLikeNotification(actorUid: String) {

        // Не уведомляем автора о его собственном лайке.
        //
        // Также проверяем, что у публикации есть автор.
        if (
            post.authorId.isBlank() ||
            post.authorId == actorUid
        ) {
            return
        }

        // Получаем отображаемое имя текущего пользователя.
        //
        // Если displayName отсутствует или пустой,
        // используем стандартное имя.
        val actorName = FirebaseAuth.getInstance()
            .currentUser?.displayName
            ?.takeIf { it.isNotBlank() }
            ?: "Игрок"


        // -----------------------------------------------------
        // ДАННЫЕ УВЕДОМЛЕНИЯ
        // -----------------------------------------------------

        // Создаём набор данных, который будет записан в Firestore.
        val notification = hashMapOf(

            // Тип уведомления.
            "type" to "like",

            // UID пользователя, поставившего лайк.
            "actorId" to actorUid,

            // Имя пользователя, поставившего лайк.
            "actorName" to actorName,

            // UID получателя уведомления — автора публикации.
            "recipientId" to post.authorId,

            // ID публикации, которой поставили лайк.
            "postId" to post.id,

            // Заголовок публикации.
            "postTitle" to post.title,

            // Время создания уведомления.
            "createdAt" to FieldValue.serverTimestamp(),

            // Новое уведомление ещё не прочитано.
            "read" to false
        )


        // -----------------------------------------------------
        // СОХРАНЕНИЕ УВЕДОМЛЕНИЯ
        // -----------------------------------------------------

        // Переходим к коллекции users.
        db.collection("users")

            // Выбираем документ автора публикации.
            .document(post.authorId)

            // Открываем коллекцию уведомлений автора.
            .collection("notifications")

            // Добавляем новое уведомление.
            .add(notification)

            // Если запись не удалась, показываем ошибку.
            .addOnFailureListener {

                Toast.makeText(
                    activity,
                    "Не удалось создать уведомление о лайке",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }


    // ---------------------------------------------------------
    // ЗАВЕРШЕНИЕ РАБОТЫ МЕНЕДЖЕРА
    // ---------------------------------------------------------

    // Метод вызывается, когда экран публикации больше не нужен.
    //
    // Он отключает слушателей Firestore,
    // чтобы не оставлять ненужные подключения.
    fun cleanup() {

        // Отключаем слушатель лайков.
        likesListener?.remove()

        // Сбрасываем ссылку на слушатель.
        likesListener = null

        // Останавливаем работу менеджера комментариев.
        commentsManager?.cleanup()

        // Удаляем ссылку на менеджер комментариев.
        commentsManager = null
    }
}