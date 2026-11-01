
package com.example.gamerhub

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import android.graphics.drawable.GradientDrawable
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
class PostDetailsManager(
    private val activity: AppCompatActivity,
    private val post: Post,
    private val onBack: () -> Unit,
    private val onAuthorClick: (String) -> Unit = {},
    private val onEditClick: (Post) -> Unit = {}
) {

    private val db = FirebaseFirestore.getInstance()

    private var likesListener: ListenerRegistration? = null
    private var commentsManager: CommentsManager? = null
    private var isLiked = false
    private var isLikeRequestInProgress = false

    fun setup(view: View) {

        // Кнопка возврата
        view.findViewById<TextView>(
            R.id.postDetailsBackButton
        ).setOnClickListener {
            onBack()
        }

        // Кнопка редактирования доступна только автору публикации
        val editButton = view.findViewById<TextView>(
            R.id.postDetailsEditButton
        )

        if (post.authorId == FirebaseAuth.getInstance().currentUser?.uid) {
            editButton.visibility = View.VISIBLE

            editButton.setOnClickListener {
                onEditClick(post)
            }
        } else {
            editButton.visibility = View.GONE
        }

        // Обложка публикации
        val image = view.findViewById<ImageView>(
            R.id.postDetailsImage
        )

        if (post.imageUrl.isNotBlank()) {
            Glide.with(activity)
                .load(post.imageUrl)
                .centerCrop()
                .into(image)
        }

        // Тип публикации
        view.findViewById<TextView>(
            R.id.postDetailsType
        ).text = post.postType.uppercase()

        // Заголовок
        view.findViewById<TextView>(
            R.id.postDetailsTitle
        ).text = post.title

        // Игра
        view.findViewById<TextView>(
            R.id.postDetailsGame
        ).text = if (post.game.isNotBlank()) {
            "🎮 ${post.game}"
        } else {
            ""
        }

        // Автор
        // Автор публикации

        val authorView = view.findViewById<TextView>(
            R.id.postDetailsAuthor
        )

        val authorAvatar = view.findViewById<ImageView>(
            R.id.postDetailsAuthorAvatar
        )

        authorView.text = post.authorName.ifBlank {
            "Игрок GamerHub"
        }

        authorView.setOnClickListener {
            if (post.authorId.isNotBlank()) {
                onAuthorClick(post.authorId)
            } else {
                Toast.makeText(
                    activity,
                    "Профиль автора недоступен",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

// Заглушка аватара.
        authorAvatar.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(android.graphics.Color.rgb(45, 40, 60))
        }

        authorAvatar.clipToOutline = true
        authorAvatar.setImageResource(
            R.drawable.ic_launcher_foreground
        )

// Переход в профиль по нажатию на аватар.
        authorAvatar.setOnClickListener {
            if (post.authorId.isNotBlank()) {
                onAuthorClick(post.authorId)
            }
        }

// Загружаем аватар.
        activity.lifecycleScope.launch {

            val avatarUrl = try {

                if (post.authorAvatarUrl.startsWith("https://")) {
                    post.authorAvatarUrl
                } else if (post.authorId.isNotBlank()) {

                    val document = db.collection("publicProfiles")
                        .document(post.authorId)
                        .get()
                        .await()

                    document.getString("avatarUrl")
                        ?: document.getString("avatarUri")
                        ?: ""

                } else {
                    ""
                }

            } catch (e: Exception) {
                android.util.Log.e(
                    "PostDetailsManager",
                    "Ошибка загрузки аватара автора",
                    e
                )
                ""
            }

            if (avatarUrl.startsWith("https://")) {
                Glide.with(activity)
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .error(R.drawable.ic_launcher_foreground)
                    .circleCrop()
                    .into(authorAvatar)
            }
        }

        // Текст публикации
        // Текст публикации
        view.findViewById<TextView>(R.id.postDetailsContent).text = post.content

        // Теги
        val tags = (
                post.gameTags +
                        post.topicTags +
                        post.tags
                ).distinct()

        view.findViewById<TextView>(
            R.id.postDetailsTags
        ).text = tags.joinToString("  •  ") { "#$it" }

        // Подключаем лайки
        setupLikes(view)

        // Подключаем комментарии
        commentsManager = CommentsManager(
            activity = activity,
            postId = post.id
        )

        commentsManager?.setup(view)
    }

    private fun setupLikes(view: View) {

        val likeButton = view.findViewById<TextView>(
            R.id.postLikeButton
        )

        val uid = FirebaseAuth.getInstance()
            .currentUser?.uid

        // Пользователь не авторизован
        if (uid == null) {
            likeButton.isEnabled = false
            likeButton.text = "🤍 0"

            likeButton.setOnClickListener {
                Toast.makeText(
                    activity,
                    "Войди, чтобы ставить лайки",
                    Toast.LENGTH_SHORT
                ).show()
            }

            return
        }

        // Проверяем ID публикации
        if (post.id.isBlank()) {
            likeButton.isEnabled = false
            likeButton.text = "🤍 0"
            return
        }

        // Коллекция лайков в Firestore
        val likesCollection = db.collection("posts")
            .document(post.id)
            .collection("likes")

        // Слушаем изменения лайков в Firebase
        likesListener = likesCollection.addSnapshotListener {
                snapshot, error ->

            if (error != null) {
                Toast.makeText(
                    activity,
                    "Ошибка загрузки лайков",
                    Toast.LENGTH_SHORT
                ).show()

                return@addSnapshotListener
            }

            if (snapshot == null) {
                return@addSnapshotListener
            }

            // Настоящее количество лайков
            val count = snapshot.size()

            // Проверяем, поставил ли лайк текущий пользователь
            isLiked = snapshot.documents.any {
                it.id == uid
            }

            // Сердце и количество
            likeButton.text = if (isLiked) {
                "❤️ $count"
            } else {
                "🤍 $count"
            }
        }

        // Нажатие на кнопку лайка
        likeButton.setOnClickListener {

            if (isLikeRequestInProgress) {
                return@setOnClickListener
            }

            isLikeRequestInProgress = true
            likeButton.isEnabled = false

            val myLike = likesCollection.document(uid)

            // Проверяем состояние лайка в Firebase
            myLike.get()
                .addOnSuccessListener { document ->

                    val operation = if (document.exists()) {

                        // Снимаем лайк
                        myLike.delete()

                    } else {

                        // Сначала сохраняем лайк
                        myLike.set(
                            mapOf(
                                "uid" to uid,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        ).addOnSuccessListener {

                            // Создаём уведомление после сохранения лайка
                            createLikeNotification(uid)
                        }
                    }

                    operation
                        .addOnFailureListener {
                            Toast.makeText(
                                activity,
                                "Не удалось сохранить лайк",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .addOnCompleteListener {
                            isLikeRequestInProgress = false
                            likeButton.isEnabled = true
                        }
                }
                .addOnFailureListener {
                    isLikeRequestInProgress = false
                    likeButton.isEnabled = true

                    Toast.makeText(
                        activity,
                        "Ошибка доступа к лайкам",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    private fun createLikeNotification(actorUid: String) {

        // Не уведомляем автора о его собственном лайке
        if (post.authorId.isBlank() || post.authorId == actorUid) {
            return
        }

        val actorName = FirebaseAuth.getInstance()
            .currentUser?.displayName
            ?.takeIf { it.isNotBlank() }
            ?: "Игрок"

        val notification = hashMapOf(
            "type" to "like",
            "actorId" to actorUid,
            "actorName" to actorName,
            "recipientId" to post.authorId,
            "postId" to post.id,
            "postTitle" to post.title,
            "createdAt" to FieldValue.serverTimestamp(),
            "read" to false
        )

        db.collection("users")
            .document(post.authorId)
            .collection("notifications")
            .add(notification)
            .addOnFailureListener {
                Toast.makeText(
                    activity,
                    "Не удалось создать уведомление о лайке",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    // Отключаем слушатель при закрытии страницы
    fun cleanup() {
        likesListener?.remove()
        likesListener = null

        commentsManager?.cleanup()
        commentsManager = null
    }
}
