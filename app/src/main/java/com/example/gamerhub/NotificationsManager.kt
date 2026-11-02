
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

class NotificationsManager(
    private val onNotificationClick: (String) -> Unit = {},
    private val onProfileClick: (String) -> Unit = {}
) {

    private val db = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null

    private val cardColor = Color.rgb(26, 26, 32)
    private val primaryColor = Color.WHITE
    private val secondaryColor = Color.rgb(160, 160, 170)
    private val purpleColor = Color.rgb(139, 92, 246)

    fun setup(view: View) {
        val container = view.findViewById<LinearLayout>(
            R.id.notificationsContainer
        ) ?: return

        container.removeAllViews()
        container.setBackgroundColor(Color.rgb(18, 18, 22))

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
                        "Пока нет уведомлений.\n\nЗдесь появятся подписки, лайки и комментарии."
                    )
                    return@addSnapshotListener
                }

                documents.forEach { document ->
                    val type = document.getString("type") ?: "like"
                    val actorId = document.getString("actorId").orEmpty()
                    val actorName = document.getString("actorName")
                        ?.takeIf { it.isNotBlank() }
                        ?: "Игрок GamerHub"
                    val postTitle = document.getString("postTitle").orEmpty()
                    val postId = document.getString("postId").orEmpty()
                    val isRead = document.getBoolean("read") ?: false
                    val createdAt = document.getTimestamp("createdAt")?.toDate()

                    val message = when (type) {
                        "follow" -> "$actorName подписался(ась) на тебя"
                        "comment" -> "$actorName прокомментировал(а) твою публикацию"
                        else -> "$actorName поставил(а) лайк твоей публикации"
                    }

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
        val context = container.context
        val density = context.resources.displayMetrics.density

        fun dp(value: Int): Int = (value * density).toInt()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))

            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(
                    if (isRead) cardColor else Color.rgb(39, 32, 53)
                )
            }

            isClickable = true
            isFocusable = true
        }

        // Для подписки показываем настоящий аватар пользователя.
        // Для остальных типов оставляем тематическую иконку.
        if (type == "follow") {
            val avatar = ImageView(context).apply {
                val size = dp(48)

                layoutParams = LinearLayout.LayoutParams(size, size)

                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.rgb(45, 40, 60))
                }

                clipToOutline = true
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(R.drawable.ic_launcher_foreground)
                contentDescription = "Аватар $actorName"
            }

            card.addView(avatar)

            if (actorId.isNotBlank()) {
                container.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                    try {
                        val profile = db.collection("publicProfiles")
                            .document(actorId)
                            .get()
                            .await()

                        val currentAvatar = profile.getString("avatarUrl")
                            ?.takeIf { it.isNotBlank() }
                            ?: profile.getString("avatarUri").orEmpty()

                        if (currentAvatar.startsWith("https://")) {
                            Glide.with(avatar)
                                .load(currentAvatar)
                                .placeholder(R.drawable.ic_launcher_foreground)
                                .error(R.drawable.ic_launcher_foreground)
                                .circleCrop()
                                .into(avatar)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(
                            "NotificationsManager",
                            "Не удалось загрузить аватар подписчика",
                            e
                        )
                    }
                }
            }
        } else {
            val icon = TextView(context).apply {
                text = when (type) {
                    "comment" -> "💬"
                    else -> "❤️"
                }

                textSize = 25f
                gravity = Gravity.CENTER
            }

            card.addView(
                icon,
                LinearLayout.LayoutParams(dp(48), dp(48))
            )
        }

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
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

        if (postTitle.isNotBlank() && type != "follow") {
            val titleView = TextView(context).apply {
                text = "Публикация: $postTitle"
                textSize = 13f
                setTextColor(purpleColor)
                setPadding(0, dp(5), 0, 0)
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
            setPadding(0, dp(5), 0, 0)
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

        container.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(9)
            }
        )

        card.setOnClickListener {
            // Помечаем уведомление прочитанным.
            if (!isRead) {
                db.collection("users")
                    .document(userId)
                    .collection("notifications")
                    .document(notificationId)
                    .update("read", true)
                    .addOnFailureListener { error ->
                        android.util.Log.e(
                            "NotificationsManager",
                            "Не удалось отметить уведомление прочитанным",
                            error
                        )
                    }
            }

            when (type) {
                "follow" -> {
                    if (actorId.isBlank()) {
                        Toast.makeText(
                            context,
                            "Профиль подписчика недоступен",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        onProfileClick(actorId)
                    }
                }

                "like", "comment" -> {
                    if (postId.isBlank()) {
                        Toast.makeText(
                            context,
                            "У этого уведомления нет ID публикации",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        onNotificationClick(postId)
                    }
                }

                else -> {
                    if (postId.isNotBlank()) {
                        onNotificationClick(postId)
                    }
                }
            }
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
