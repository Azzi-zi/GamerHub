
package com.example.gamerhub

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

// Менеджер отвечает за загрузку, отображение,
// создание и редактирование комментариев.
class CommentsManager(
    private val activity: AppCompatActivity,
    private val postId: String
) {

    private val db = FirebaseFirestore.getInstance()

    // Слушатель изменений комментариев в Firestore.
    private var commentsListener: ListenerRegistration? = null

    // Настройка экрана комментариев.
    fun setup(view: View) {

        // Контейнер карточек комментариев.
        val container = view.findViewById<LinearLayout>(
            R.id.commentsContainer
        ) ?: return

        // Текст, который отображается, если комментариев нет.
        val emptyText = view.findViewById<TextView>(
            R.id.commentsEmptyText
        ) ?: return

        // Поле ввода нового комментария.
        val input = view.findViewById<EditText>(
            R.id.commentInput
        ) ?: return

        // Кнопка отправки комментария.
        val sendButton = view.findViewById<TextView>(
            R.id.sendCommentButton
        ) ?: return

        // Проверяем ID публикации.
        if (postId.isBlank()) {
            emptyText.text = "Не удалось определить публикацию"
            emptyText.visibility = View.VISIBLE
            sendButton.isEnabled = false
            return
        }

        // Проверяем авторизацию пользователя.
        val user = FirebaseAuth.getInstance().currentUser

        if (user == null) {
            input.isEnabled = false
            input.hint = "Войди, чтобы комментировать"
            sendButton.isEnabled = false
        }

        // Получаем коллекцию комментариев конкретной публикации.
        val comments = db.collection("posts")
            .document(postId)
            .collection("comments")

        // Подписываемся на изменения комментариев.
        commentsListener = comments
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->

                // Обрабатываем ошибку загрузки.
                if (error != null) {

                    emptyText.text = "Не удалось загрузить комментарии"
                    emptyText.visibility = View.VISIBLE

                    android.util.Log.e(
                        "CommentsManager",
                        "Ошибка загрузки комментариев",
                        error
                    )

                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    return@addSnapshotListener
                }

                // Удаляем старые карточки.
                container.removeAllViews()

                val documents = snapshot.documents

                // Если комментариев нет, показываем соответствующее сообщение.
                emptyText.visibility = if (documents.isEmpty()) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

                // Создаём карточки для каждого комментария.
                documents.forEach { document ->

                    val authorId = document
                        .getString("authorId")
                        .orEmpty()

                    val author = document
                        .getString("authorName")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: "Игрок GamerHub"

                    val text = document
                        .getString("text")
                        .orEmpty()

                    val timestamp = document
                        .getTimestamp("createdAt")

                    // Проверяем, редактировался ли комментарий.
                    val isEdited = document
                        .getBoolean("isEdited") == true

                    val avatarUrl = document
                        .getString("authorAvatarUrl")
                        .orEmpty()

                    // Создаём карточку комментария.
                    val commentView = createCommentView(
                        commentId = document.id,
                        author = author,
                        authorId = authorId,
                        avatarUrl = avatarUrl,
                        commentText = text,
                        timestamp = timestamp,
                        isEdited = isEdited
                    )

                    container.addView(commentView)
                }
            }

        // Обработчик отправки нового комментария.
        sendButton.setOnClickListener {

            val currentUser = FirebaseAuth
                .getInstance()
                .currentUser

            // Проверяем авторизацию.
            if (currentUser == null) {

                Toast.makeText(
                    activity,
                    "Войди, чтобы оставить комментарий",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            // Получаем введённый текст.
            val commentText = input.text
                .toString()
                .trim()

            // Проверяем пустой текст.
            if (commentText.isBlank()) {
                input.error = "Напиши комментарий"
                return@setOnClickListener
            }

            // Проверяем ограничение длины.
            if (commentText.length > 1000) {
                input.error = "Максимум 1000 символов"
                return@setOnClickListener
            }

            sendButton.isEnabled = false

            // Выполняем запросы Firebase в корутине.
            activity.lifecycleScope.launch {

                try {

                    // Загружаем публичный профиль пользователя.
                    val profile = db.collection("publicProfiles")
                        .document(currentUser.uid)
                        .get()
                        .await()

                    // Получаем имя пользователя.
                    val authorName = profile
                        .getString("username")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: "Игрок GamerHub"

                    // Получаем аватар пользователя.
                    val authorAvatarUrl = profile
                        .getString("avatarUrl")
                        ?.takeIf { it.isNotBlank() }
                        ?: profile.getString("avatarUri")
                            ?.takeIf { it.isNotBlank() }
                        ?: ""

                    // Подготавливаем данные комментария.
                    val comment = hashMapOf(
                        "text" to commentText,
                        "authorId" to currentUser.uid,
                        "authorName" to authorName,
                        "authorAvatarUrl" to authorAvatarUrl,
                        "createdAt" to FieldValue.serverTimestamp()
                    )

                    // Сохраняем комментарий в Firestore.
                    comments.add(comment).await()

                    // Очищаем поле ввода.
                    input.text.clear()

                    // Создаём уведомление автору публикации.
                    createCommentNotification(
                        actorUid = currentUser.uid,
                        actorName = authorName,
                        commentText = commentText
                    )

                    Toast.makeText(
                        activity,
                        "Комментарий опубликован",
                        Toast.LENGTH_SHORT
                    ).show()

                } catch (e: Exception) {

                    android.util.Log.e(
                        "CommentsManager",
                        "Ошибка отправки комментария",
                        e
                    )

                    Toast.makeText(
                        activity,
                        "Не удалось отправить комментарий",
                        Toast.LENGTH_SHORT
                    ).show()

                } finally {

                    // Возвращаем доступность кнопки.
                    sendButton.isEnabled =
                        FirebaseAuth.getInstance().currentUser != null
                }
            }
        }
    }

    // Создаёт уведомление автору публикации о новом комментарии.
    private fun createCommentNotification(
        actorUid: String,
        actorName: String,
        commentText: String
    ) {

        // Загружаем публикацию.
        db.collection("posts")
            .document(postId)
            .get()
            .addOnSuccessListener { postDocument ->

                if (!postDocument.exists()) {
                    return@addOnSuccessListener
                }

                // Определяем автора публикации.
                val postAuthorId = postDocument
                    .getString("authorId")
                    ?: return@addOnSuccessListener

                // Не отправляем уведомление самому себе.
                if (
                    postAuthorId.isBlank() ||
                    postAuthorId == actorUid
                ) {
                    return@addOnSuccessListener
                }

                val postTitle = postDocument
                    .getString("title")
                    .orEmpty()

                // Формируем уведомление.
                val notification = hashMapOf(
                    "type" to "comment",
                    "actorId" to actorUid,
                    "actorName" to actorName,
                    "recipientId" to postAuthorId,
                    "postId" to postId,
                    "postTitle" to postTitle,
                    "commentText" to commentText,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "read" to false
                )

                // Сохраняем уведомление.
                db.collection("users")
                    .document(postAuthorId)
                    .collection("notifications")
                    .add(notification)
                    .addOnFailureListener { error ->

                        android.util.Log.e(
                            "CommentsManager",
                            "Не удалось создать уведомление",
                            error
                        )
                    }
            }
            .addOnFailureListener { error ->

                android.util.Log.e(
                    "CommentsManager",
                    "Не удалось загрузить публикацию",
                    error
                )
            }
    }

    // Создаёт одну карточку комментария.
    private fun createCommentView(
        commentId: String,
        author: String,
        authorId: String,
        avatarUrl: String,
        commentText: String,
        timestamp: Timestamp?,
        isEdited: Boolean
    ): View {

        // Основная карточка.
        val card = LinearLayout(activity).apply {

            orientation = LinearLayout.VERTICAL

            setPadding(14, 12, 14, 12)

            background = GradientDrawable().apply {
                setColor(Color.rgb(26, 26, 32))
                cornerRadius = 14f
            }

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 10
            }
        }

        // Строка с аватаром и именем автора.
        val authorRow = LinearLayout(activity).apply {

            orientation = LinearLayout.HORIZONTAL

            gravity = Gravity.CENTER_VERTICAL
        }

        // Аватар пользователя.
        val avatar = ImageView(activity).apply {

            val size = (
                    36 * resources.displayMetrics.density
                    ).toInt()

            layoutParams = LinearLayout.LayoutParams(
                size,
                size
            )

            scaleType = ImageView.ScaleType.CENTER_CROP

            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(45, 40, 60))
            }

            clipToOutline = true

            setImageResource(
                R.drawable.ic_launcher_foreground
            )

            contentDescription = "Аватар $author"
        }

        // Имя автора комментария.
        val authorView = TextView(activity).apply {

            text = author

            textSize = 14f

            setTextColor(Color.WHITE)

            setTypeface(null, Typeface.BOLD)

            setPadding(
                (10 * resources.displayMetrics.density).toInt(),
                0,
                0,
                0
            )
        }

        authorRow.addView(avatar)
        authorRow.addView(authorView)

        // Загружаем актуальное имя и аватар автора из профиля.
        if (authorId.isNotBlank()) {

            activity.lifecycleScope.launch {

                try {

                    val profile = db.collection("publicProfiles")
                        .document(authorId)
                        .get()
                        .await()

                    val profileName = profile
                        .getString("username")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }

                    val profileAvatar = profile
                        .getString("avatarUrl")
                        ?.takeIf { it.isNotBlank() }
                        ?: profile.getString("avatarUri").orEmpty()

                    // Обновляем имя.
                    if (!profileName.isNullOrBlank()) {
                        authorView.text = profileName
                    }

                    // Выбираем ссылку на аватар.
                    val finalAvatarUrl = profileAvatar
                        .takeIf { it.startsWith("https://") }
                        ?: avatarUrl.takeIf {
                            it.startsWith("https://")
                        }

                    // Загружаем аватар.
                    if (
                        !finalAvatarUrl.isNullOrBlank() &&
                        !activity.isFinishing &&
                        !activity.isDestroyed
                    ) {

                        Glide.with(activity)
                            .load(finalAvatarUrl)
                            .placeholder(R.drawable.ic_launcher_foreground)
                            .error(R.drawable.ic_launcher_foreground)
                            .circleCrop()
                            .into(avatar)
                    }

                } catch (e: Exception) {

                    android.util.Log.e(
                        "CommentsManager",
                        "Не удалось загрузить профиль комментатора",
                        e
                    )

                    // Если профиль не загрузился, используем
                    // аватар, сохранённый непосредственно в комментарии.
                    if (
                        avatarUrl.startsWith("https://") &&
                        !activity.isFinishing &&
                        !activity.isDestroyed
                    ) {

                        Glide.with(activity)
                            .load(avatarUrl)
                            .placeholder(R.drawable.ic_launcher_foreground)
                            .error(R.drawable.ic_launcher_foreground)
                            .circleCrop()
                            .into(avatar)
                    }
                }
            }

        } else if (avatarUrl.startsWith("https://")) {

            // Загружаем сохранённый аватар,
            // если ID автора отсутствует.
            if (!activity.isFinishing && !activity.isDestroyed) {

                Glide.with(activity)
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .error(R.drawable.ic_launcher_foreground)
                    .circleCrop()
                    .into(avatar)
            }
        }

        // Текст комментария.
        val contentView = TextView(activity).apply {

            text = commentText

            textSize = 14f

            setTextColor(Color.rgb(220, 220, 225))

            setPadding(0, 10, 0, 8)
        }

        // Дата создания комментария.
        val dateView = TextView(activity).apply {

            text = timestamp?.toDate()?.let { date ->

                SimpleDateFormat(
                    "dd.MM.yyyy HH:mm",
                    Locale.getDefault()
                ).format(date)

            } ?: "Только что"

            // Добавляем отметку, если комментарий редактировался.
            if (isEdited) {
                text = "$text · изменён"
            }

            textSize = 11f

            setTextColor(Color.rgb(160, 160, 170))
        }

        card.addView(authorRow)

        card.addView(contentView)

        card.addView(dateView)

        // Проверяем, принадлежит ли комментарий текущему пользователю.
        val currentUid = FirebaseAuth
            .getInstance()
            .currentUser
            ?.uid

        // Кнопка редактирования показывается только автору.
        if (authorId.isNotBlank() && currentUid == authorId) {

            val editButton = TextView(activity).apply {

                text = "✎ Изменить"

                textSize = 12f

                setTextColor(Color.rgb(180, 110, 255))

                gravity = Gravity.END

                setPadding(0, 12, 0, 0)

                isClickable = true

                isFocusable = true

                contentDescription = "Редактировать комментарий"

                setOnClickListener {

                    showEditCommentDialog(
                        commentId = commentId,
                        currentText = commentText
                    )
                }
            }

            card.addView(editButton)
        }

        return card
    }

    // Открывает диалог редактирования комментария.
    private fun showEditCommentDialog(
        commentId: String,
        currentText: String
    ) {

        // Получаем текущего пользователя.
        val currentUser = FirebaseAuth
            .getInstance()
            .currentUser

        if (currentUser == null) {

            Toast.makeText(
                activity,
                "Войди в аккаунт",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        // Поле с текущим текстом комментария.
        val editInput = EditText(activity).apply {

            setText(currentText)

            // Перемещаем курсор в конец текста.
            setSelection(text.length)

            minLines = 3

            maxLines = 8

            setPadding(24, 16, 24, 16)

            hint = "Текст комментария"
        }

        // Создаём окно редактирования.
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Редактировать комментарий")
            .setView(editInput)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить", null)
            .create()

        // Настраиваем кнопку сохранения после создания окна.
        dialog.setOnShowListener {

            val saveButton = dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            )

            saveButton.setOnClickListener {

                val updatedText = editInput.text
                    .toString()
                    .trim()

                // Проверяем пустой текст.
                if (updatedText.isBlank()) {

                    editInput.error =
                        "Комментарий не может быть пустым"

                    return@setOnClickListener
                }

                // Проверяем максимальную длину.
                if (updatedText.length > 1000) {

                    editInput.error = "Максимум 1000 символов"

                    return@setOnClickListener
                }

                // Временно блокируем кнопку.
                saveButton.isEnabled = false

                activity.lifecycleScope.launch {

                    try {

                        // Получаем ссылку на конкретный комментарий.
                        val commentRef = db.collection("posts")
                            .document(postId)
                            .collection("comments")
                            .document(commentId)

                        // Повторно загружаем документ из Firestore.
                        val commentDocument = commentRef
                            .get()
                            .await()

                        // Проверяем настоящего автора комментария.
                        val actualAuthorId = commentDocument
                            .getString("authorId")

                        // Не разрешаем редактировать чужие комментарии.
                        if (
                            !commentDocument.exists() ||
                            actualAuthorId != currentUser.uid
                        ) {

                            Toast.makeText(
                                activity,
                                "Можно редактировать только свой комментарий",
                                Toast.LENGTH_SHORT
                            ).show()

                            dialog.dismiss()

                            return@launch
                        }

                        // Сохраняем изменения в Firestore.
                        commentRef.update(
                            mapOf(
                                "text" to updatedText,
                                "isEdited" to true,
                                "editedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()

                        Toast.makeText(
                            activity,
                            "Комментарий обновлён",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Закрываем окно.
                        // Список комментариев обновится автоматически
                        // благодаря addSnapshotListener.
                        dialog.dismiss()

                    } catch (e: Exception) {

                        android.util.Log.e(
                            "CommentsManager",
                            "Ошибка редактирования комментария",
                            e
                        )

                        Toast.makeText(
                            activity,
                            "Не удалось изменить комментарий. Проверь права доступа.",
                            Toast.LENGTH_LONG
                        ).show()

                    } finally {

                        // Возвращаем кнопку в активное состояние,
                        // если диалог ещё открыт.
                        if (dialog.isShowing) {
                            saveButton.isEnabled = true
                        }
                    }
                }
            }
        }

        dialog.show()
    }

    // Отключаем слушатель Firestore,
    // когда экран комментариев больше не используется.
    fun cleanup() {

        commentsListener?.remove()

        commentsListener = null
    }
}
