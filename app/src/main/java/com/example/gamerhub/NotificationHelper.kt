package com.example.gamerhub

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

// Вспомогательный объект для создания уведомлений в Firestore.
//
// object означает, что создаётся один общий объект,
// поэтому обращаться к нему можно напрямую:
// NotificationHelper.createNotification(...)
object NotificationHelper {

    // Получаем экземпляр Firestore.
    // Через него будем записывать уведомления в базу данных.
    private val db = FirebaseFirestore.getInstance()

    /**
     * Создаёт уведомление для владельца публикации.
     *
     * recipientId — UID пользователя, которому отправляется уведомление.
     * actorName — имя пользователя, который совершил действие.
     * postId — уникальный ID публикации в Firestore.
     * postTitle — заголовок публикации.
     * type — тип уведомления: "like" или "comment".
     */
    fun createNotification(
        recipientId: String,
        actorName: String,
        postId: String,
        postTitle: String,
        type: String
    ) {

        // Получаем UID пользователя, который сейчас авторизован.
        //
        // Если пользователь не авторизован (currentUser == null),
        // функция сразу завершает работу.
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            ?: return

        // Проверяем, можно ли создавать уведомление.
        //
        // recipientId.isBlank() — получатель не указан.
        // recipientId == currentUserId — нельзя уведомлять пользователя
        // о его собственном действии.
        // postId.isBlank() — отсутствует ID публикации.
        //
        // Если хотя бы одно условие выполняется, выходим из функции.
        if (
            recipientId.isBlank() ||
            recipientId == currentUserId ||
            postId.isBlank()
        ) {
            return
        }

        // Проверяем тип уведомления.
        // Пока поддерживаются только лайки и комментарии.
        if (type != "like" && type != "comment") {
            return
        }

        // Создаём набор данных для будущего уведомления.
        //
        // HashMap хранит информацию в формате:
        // "название поля" -> значение.
        val notification = hashMapOf(

            // Тип действия: лайк или комментарий.
            "type" to type,

            // UID пользователя, который совершил действие.
            "actorId" to currentUserId,

            // Имя пользователя, который совершил действие.
            "actorName" to actorName,

            // ID публикации, к которой относится уведомление.
            "postId" to postId,

            // Заголовок публикации.
            "postTitle" to postTitle,

            // read = false означает, что уведомление ещё не прочитано.
            "read" to false,

            // Firestore самостоятельно устанавливает время создания.
            // Это надёжнее, чем использовать время устройства пользователя.
            "createdAt" to FieldValue.serverTimestamp()
        )

        // Переходим к коллекции пользователей.
        db.collection("users")

            // Выбираем документ конкретного получателя уведомления.
            .document(recipientId)

            // Открываем вложенную коллекцию notifications.
            .collection("notifications")

            // Добавляем новое уведомление.
            // Firestore автоматически создаёт для него уникальный ID.
            .add(notification)

            // Если произошла ошибка записи, выводим её в Logcat.
            .addOnFailureListener { error ->

                android.util.Log.e(
                    "NotificationHelper",
                    "Не удалось создать уведомление",
                    error
                )
            }
    }
}