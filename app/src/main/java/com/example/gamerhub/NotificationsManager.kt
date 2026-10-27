
package com.example.gamerhub

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationsManager(
    private val onNotificationClick: (String) -> Unit = {}
) {

    private val db = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null

    private val backgroundColor = Color.rgb(18, 18, 22)
    private val cardColor = Color.rgb(26, 26, 32)
    private val primaryColor = Color.WHITE
    private val secondaryColor = Color.rgb(160, 160, 170)
    private val purpleColor = Color.rgb(139, 92, 246)

    fun setup(view: View) {
        val container =
            view.findViewById<LinearLayout>(R.id.notificationsContainer)

        if (container == null) {
            Toast.makeText(
                view.context,
                "Не найден контейнер уведомлений",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        container.removeAllViews()
        container.setBackgroundColor(backgroundColor)

        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            showEmpty(container, "Войди в аккаунт, чтобы увидеть уведомления")
            return
        }

        showEmpty(container, "Загрузка уведомлений...")

        listener?.remove()

        listener = db.collection("users")
            .document(userId)
            .collection("notifications")
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    showEmpty(
                        container,
                        "Не удалось загрузить уведомления. Проверь правила Firestore."
                    )
                    return@addSnapshotListener
                }

                container.removeAllViews()

                val documents = snapshot?.documents
                    ?.sortedByDescending {
                        it.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                    }
                    .orEmpty()

                if (documents.isEmpty()) {
                    showEmpty(
                        container,
                        "Пока нет уведомлений.\n\n" +
                                "Здесь появятся подписки, лайки и комментарии."
                    )
                    return@addSnapshotListener
                }

                documents.forEach { document ->
                    val type = document.getString("type") ?: "like"
                    val actorName =
                        document.getString("actorName") ?: "Игрок"
                    val postTitle =
                        document.getString("postTitle").orEmpty()
                    val postId = document.getString("postId").orEmpty()
                    val isRead = document.getBoolean("read") ?: false
                    val createdAt =
                        document.getTimestamp("createdAt")?.toDate()

                    val message = when (type) {
                        "follow" ->
                            "$actorName подписался(ась) на тебя"

                        "comment" ->
                            "$actorName прокомментировал(а) твою публикацию"

                        else ->
                            "$actorName поставил(а) лайк твоей публикации"
                    }

                    addNotification(
                        container = container,
                        type = type,
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

    private fun addNotification(
        container: LinearLayout,
        type: String,
        message: String,
        postTitle: String,
        createdAt: Date?,
        isRead: Boolean,
        postId: String,
        notificationId: String,
        userId: String
    ) {
        val context = container.context

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 14, 16, 14)

            setBackgroundColor(
                if (isRead) cardColor else Color.rgb(39, 32, 53)
            )

            isClickable = true
            isFocusable = true
        }

        val icon = TextView(context).apply {
            text = when (type) {
                "follow" -> "👤"
                "comment" -> "💬"
                else -> "❤️"
            }

            textSize = 25f
            gravity = Gravity.CENTER
        }

        card.addView(
            icon,
            LinearLayout.LayoutParams(44, 48)
        )

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 0, 0, 0)
        }

        val messageView = TextView(context).apply {
            text = message
            textSize = 14f
            setTextColor(primaryColor)

            setTypeface(
                null,
                if (isRead) Typeface.NORMAL else Typeface.BOLD
            )
        }

        textContainer.addView(messageView)

        // Для уведомлений о подписке название публикации не нужно.
        if (postTitle.isNotBlank()) {
            val titleView = TextView(context).apply {
                text = postTitle
                textSize = 13f
                setTextColor(purpleColor)
                setPadding(0, 5, 0, 0)
            }

            textContainer.addView(titleView)
        }

        val timeView = TextView(context).apply {
            text = createdAt?.let {
                SimpleDateFormat(
                    "dd.MM.yyyy, HH:mm",
                    Locale.getDefault()
                ).format(it)
            } ?: "Недавно"

            textSize = 11f
            setTextColor(secondaryColor)
            setPadding(0, 5, 0, 0)
        }

        textContainer.addView(timeView)

        card.addView(
            textContainer,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val cardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 8
        }

        container.addView(card, cardParams)

        card.setOnClickListener {
            // Отмечаем уведомление прочитанным.
            if (!isRead) {
                db.collection("users")
                    .document(userId)
                    .collection("notifications")
                    .document(notificationId)
                    .update("read", true)
            }

            // У уведомления о подписке нет публикации.
            // Поэтому открывать PostDetailsManager не нужно.
            if (type == "follow") {
                return@setOnClickListener
            }

            if (postId.isBlank()) {
                Toast.makeText(
                    context,
                    "У этого уведомления нет ID публикации",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            onNotificationClick(postId)
        }
    }

    private fun showEmpty(
        container: LinearLayout,
        message: String
    ) {
        container.removeAllViews()

        val text = TextView(container.context).apply {
            this.text = message
            textSize = 14f
            setTextColor(secondaryColor)
            gravity = Gravity.CENTER
            setPadding(24, 32, 24, 32)
        }

        container.addView(
            text,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    fun stopListening() {
        listener?.remove()
        listener = null
    }
}
