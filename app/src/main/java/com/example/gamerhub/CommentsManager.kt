
package com.example.gamerhub

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
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

        // Коллекция комментариев конкретной публикации
        val comments = db.collection("posts")
            .document(postId)
            .collection("comments")

        // Слушаем изменения в реальном времени
        commentsListener = comments
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    emptyText.text = "Не удалось загрузить комментарии"
                    emptyText.visibility = View.VISIBLE
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
                    val author = document.getString("authorName")
                        ?.ifBlank { "Игрок GamerHub" }
                        ?: "Игрок GamerHub"

                    val text = document.getString("text").orEmpty()
                    val timestamp = document.getTimestamp("createdAt")

                    container.addView(
                        createCommentView(author, text, timestamp)
                    )
                }
            }

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

            val authorName = currentUser.displayName
                ?.takeIf { it.isNotBlank() }
                ?: currentUser.email
                    ?.substringBefore("@")
                    ?.takeIf { it.isNotBlank() }
                ?: "Игрок GamerHub"

            val comment = hashMapOf(
                "text" to commentText,
                "authorId" to currentUser.uid,
                "authorName" to authorName,
                "createdAt" to FieldValue.serverTimestamp()
            )

            comments.add(comment)
                .addOnSuccessListener {
                    input.text.clear()

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
                }
                .addOnFailureListener { exception ->
                    android.util.Log.e(
                        "CommentsManager",
                        "Ошибка отправки комментария",
                        exception
                    )

                    Toast.makeText(
                        activity,
                        "Ошибка: ${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                .addOnCompleteListener {
                    sendButton.isEnabled = true
                }
        }
    }
    private fun createCommentNotification(
        actorUid: String,
        actorName: String,
        commentText: String
    ) {
        // Получаем данные публикации
        db.collection("posts")
            .document(postId)
            .get()
            .addOnSuccessListener { postDocument ->

                if (!postDocument.exists()) return@addOnSuccessListener

                val postAuthorId = postDocument.getString("authorId")
                    ?: return@addOnSuccessListener

                // Не уведомляем пользователя о собственном комментарии
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

        val authorView = TextView(activity).apply {
            text = author
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }

        val contentView = TextView(activity).apply {
            text = commentText
            textSize = 14f
            setTextColor(Color.rgb(220, 220, 225))
            setPadding(0, 8, 0, 8)
        }

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

        card.addView(authorView)
        card.addView(contentView)
        card.addView(dateView)

        return card
    }

    fun cleanup() {
        commentsListener?.remove()
        commentsListener = null
    }
}
