
package com.example.gamerhub

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

class CommentsManager(
    private val activity: AppCompatActivity,
    private val postId: String
) {

    private val db = FirebaseFirestore.getInstance()
    private var commentsListener: ListenerRegistration? = null

    fun setup(view: View) {
        val container = view.findViewById<LinearLayout>(
            R.id.commentsContainer
        ) ?: return

        val emptyText = view.findViewById<TextView>(
            R.id.commentsEmptyText
        ) ?: return

        val input = view.findViewById<EditText>(
            R.id.commentInput
        ) ?: return

        val sendButton = view.findViewById<TextView>(
            R.id.sendCommentButton
        ) ?: return

        if (postId.isBlank()) {
            emptyText.text = "Не удалось определить публикацию"
            emptyText.visibility = View.VISIBLE
            sendButton.isEnabled = false
            return
        }

        val user = FirebaseAuth.getInstance().currentUser

        if (user == null) {
            input.isEnabled = false
            input.hint = "Войди, чтобы комментировать"
            sendButton.isEnabled = false
        }

        // Коллекция комментариев конкретной публикации.
        val comments = db.collection("posts")
            .document(postId)
            .collection("comments")

        // Слушаем изменения комментариев в реальном времени.
        commentsListener = comments
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->

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

                if (snapshot == null) return@addSnapshotListener

                container.removeAllViews()

                val documents = snapshot.documents

                emptyText.visibility = if (documents.isEmpty()) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

                documents.forEach { document ->
                    val authorId = document.getString("authorId").orEmpty()

                    val author = document.getString("authorName")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: "Игрок GamerHub"

                    val text = document.getString("text").orEmpty()
                    val timestamp = document.getTimestamp("createdAt")

                    val avatarUrl = document
                        .getString("authorAvatarUrl")
                        .orEmpty()

                    val commentView = createCommentView(
                        author = author,
                        authorId = authorId,
                        avatarUrl = avatarUrl,
                        commentText = text,
                        timestamp = timestamp
                    )

                    container.addView(commentView)
                }
            }

        // Отправка комментария.
        sendButton.setOnClickListener {
            val currentUser = FirebaseAuth.getInstance().currentUser

            if (currentUser == null) {
                Toast.makeText(
                    activity,
                    "Войди, чтобы оставить комментарий",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            val commentText = input.text.toString().trim()

            if (commentText.isBlank()) {
                input.error = "Напиши комментарий"
                return@setOnClickListener
            }

            if (commentText.length > 1000) {
                input.error = "Максимум 1000 символов"
                return@setOnClickListener
            }

            sendButton.isEnabled = false

            activity.lifecycleScope.launch {
                try {
                    // Получаем актуальные данные пользователя.
                    val profile = db.collection("publicProfiles")
                        .document(currentUser.uid)
                        .get()
                        .await()

                    val authorName = profile.getString("username")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: "Игрок GamerHub"

                    val authorAvatarUrl = profile.getString("avatarUrl")
                        ?.takeIf { it.isNotBlank() }
                        ?: profile.getString("avatarUri")
                            ?.takeIf { it.isNotBlank() }
                        ?: ""

                    val comment = hashMapOf(
                        "text" to commentText,
                        "authorId" to currentUser.uid,
                        "authorName" to authorName,
                        "authorAvatarUrl" to authorAvatarUrl,
                        "createdAt" to FieldValue.serverTimestamp()
                    )

                    // Сохраняем комментарий.
                    comments.add(comment).await()

                    input.text.clear()

                    // Уведомляем автора публикации.
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
                    sendButton.isEnabled =
                        FirebaseAuth.getInstance().currentUser != null
                }
            }
        }
    }

    private fun createCommentNotification(
        actorUid: String,
        actorName: String,
        commentText: String
    ) {
        // Получаем данные публикации.
        db.collection("posts")
            .document(postId)
            .get()
            .addOnSuccessListener { postDocument ->

                if (!postDocument.exists()) {
                    return@addOnSuccessListener
                }

                val postAuthorId = postDocument.getString("authorId")
                    ?: return@addOnSuccessListener

                // Не уведомляем пользователя о собственном комментарии.
                if (postAuthorId.isBlank() || postAuthorId == actorUid) {
                    return@addOnSuccessListener
                }

                val postTitle = postDocument.getString("title").orEmpty()

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

    private fun createCommentView(
        author: String,
        authorId: String,
        avatarUrl: String,
        commentText: String,
        timestamp: Timestamp?
    ): View {

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

        // Шапка комментария: аватар и ник.
        val authorRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val avatar = ImageView(activity).apply {
            val size = (36 * resources.displayMetrics.density).toInt()

            layoutParams = LinearLayout.LayoutParams(size, size)
            scaleType = ImageView.ScaleType.CENTER_CROP

            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(45, 40, 60))
            }

            clipToOutline = true
            setImageResource(R.drawable.ic_launcher_foreground)
            contentDescription = "Аватар $author"
        }

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

        // Загружаем актуальные имя и аватар из публичного профиля.
        if (authorId.isNotBlank()) {
            activity.lifecycleScope.launch {
                try {
                    val profile = db.collection("publicProfiles")
                        .document(authorId)
                        .get()
                        .await()

                    val profileName = profile.getString("username")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }

                    val profileAvatar = profile.getString("avatarUrl")
                        ?.takeIf { it.isNotBlank() }
                        ?: profile.getString("avatarUri").orEmpty()

                    // Обновляем ник, если он найден.
                    if (!profileName.isNullOrBlank()) {
                        authorView.text = profileName
                    }

                    // Сначала используем аватар из профиля.
                    // Если его нет, используем URL из комментария.
                    val finalAvatarUrl = profileAvatar
                        .takeIf { it.startsWith("https://") }
                        ?: avatarUrl.takeIf {
                            it.startsWith("https://")
                        }

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

                    // Если профиль недоступен, используем сохранённый аватар.
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
            // Для комментариев без authorId используем сохранённый аватар.
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

        // Дата публикации комментария.
        val dateView = TextView(activity).apply {
            text = timestamp?.toDate()?.let { date ->
                SimpleDateFormat(
                    "dd.MM.yyyy HH:mm",
                    Locale.getDefault()
                ).format(date)
            } ?: "Только что"

            textSize = 11f
            setTextColor(Color.rgb(160, 160, 170))
        }

        card.addView(authorRow)
        card.addView(contentView)
        card.addView(dateView)

        return card
    }

    fun cleanup() {
        commentsListener?.remove()
        commentsListener = null
    }
}
