
package com.example.gamerhub

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

object NotificationHelper {

    private val db = FirebaseFirestore.getInstance()

    /**
     * Создаёт уведомление у владельца публикации.
     *
     * recipientId — UID получателя.
     * actorName — имя того, кто поставил лайк или написал комментарий.
     * postId — ID публикации в Firestore.
     * postTitle — заголовок публикации.
     * type — "like" или "comment".
     */
    fun createNotification(
        recipientId: String,
        actorName: String,
        postId: String,
        postTitle: String,
        type: String
    ) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            ?: return

        // Не уведомляем пользователя о его собственных действиях.
        if (recipientId.isBlank() ||
            recipientId == currentUserId ||
            postId.isBlank()
        ) {
            return
        }

        if (type != "like" && type != "comment") {
            return
        }

        val notification = hashMapOf(
            "type" to type,
            "actorId" to currentUserId,
            "actorName" to actorName,
            "postId" to postId,
            "postTitle" to postTitle,
            "read" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection("users")
            .document(recipientId)
            .collection("notifications")
            .add(notification)
            .addOnFailureListener { error ->
                android.util.Log.e(
                    "NotificationHelper",
                    "Не удалось создать уведомление",
                    error
                )
            }
    }
}
