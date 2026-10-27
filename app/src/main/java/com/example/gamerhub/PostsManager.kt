
package com.example.gamerhub


import android.view.Gravity
import android.widget.FrameLayout
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
class PostsManager(
    private val activity: AppCompatActivity
) {

    private val db = FirebaseFirestore.getInstance()

    private var onPostClick: ((Post) -> Unit)? = null

    private var onAuthorClick: ((String) -> Unit)? = null

    fun setOnAuthorClickListener(listener: (String) -> Unit) {
        onAuthorClick = listener
    }
    fun setOnPostClickListener(listener: (Post) -> Unit) {
        onPostClick = listener
    }

    fun loadPostsForGame(view: View, gameTitle: String) {
        val container = view.findViewById<LinearLayout>(
            R.id.gamePostsContainer
        ) ?: return

        activity.lifecycleScope.launch {
            try {
                val snapshot = db.collection("posts")
                    .get()
                    .await()

                val posts = snapshot.documents
                    .mapNotNull { document ->
                        document.toObject(Post::class.java)?.copy(
                            id = document.id
                        )
                    }
                    .filter { post ->
                        post.game.trim().equals(
                            gameTitle.trim(),
                            ignoreCase = true
                        )
                    }
                    .reversed()

                container.removeAllViews()

                if (posts.isEmpty()) {
                    val emptyText = TextView(activity).apply {
                        text = "Публикаций об этой игре пока нет"
                        textSize = 15f
                        setTextColor(Color.LTGRAY)
                        setPadding(0, 12, 0, 12)
                    }

                    container.addView(emptyText)
                    return@launch
                }

                posts.forEach { post ->
                    container.addView(createPostCard(post))
                }
            } catch (e: Exception) {
                android.util.Log.e(
                    "PostsManager",
                    "Ошибка загрузки публикаций для игры: $gameTitle",
                    e
                )

                Toast.makeText(
                    activity,
                    "Не удалось загрузить публикации игры",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Загружаем публикации из Firestore

    // Загружаем публикации с фильтрацией по категории и поисковому запросу.
    fun loadPosts(
        view: View,
        category: String = "Все",
        query: String = ""
    ) {
        val container = view.findViewById<LinearLayout>(
            R.id.postsContainer
        ) ?: return

        activity.lifecycleScope.launch {
            try {
                val snapshot = db.collection("posts")
                    .get()
                    .await()

                val searchText = query.trim()

                val posts = snapshot.documents
                    .mapNotNull { document ->
                        document.toObject(Post::class.java)
                            ?.copy(id = document.id)
                    }
                    .filter { post ->
                        // Сначала проверяем категорию.
                        val matchesCategory =
                            category == "Все" ||
                                    post.postType.equals(
                                        category,
                                        ignoreCase = true
                                    )

                        // Затем ищем совпадения в содержимом публикации.
                        val searchableText = listOf(
                            post.title,
                            post.content,
                            post.game,
                            post.authorName,
                            post.postType
                        ) + post.gameTags +
                                post.topicTags +
                                post.tags

                        val matchesQuery =
                            searchText.isBlank() ||
                                    searchableText.any { text ->
                                        text.contains(
                                            searchText,
                                            ignoreCase = true
                                        )
                                    }

                        matchesCategory && matchesQuery
                    }
                    .reversed()

                container.removeAllViews()

                if (posts.isEmpty()) {
                    val emptyText = TextView(activity).apply {
                        text = if (searchText.isNotBlank()) {
                            "По запросу «$searchText» ничего не найдено"
                        } else {
                            "Публикаций пока нет"
                        }

                        textSize = 15f
                        setTextColor(Color.LTGRAY)
                        setPadding(20, 24, 20, 24)
                    }

                    container.addView(emptyText)
                    return@launch
                }

                posts.forEach { post ->
                    container.addView(createPostCard(post))
                }

            } catch (e: Exception) {
                android.util.Log.e(
                    "PostsManager",
                    "Ошибка загрузки публикаций",
                    e
                )

                Toast.makeText(
                    activity,
                    "Не удалось загрузить публикации",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


    // Загружаем публикации конкретного пользователя в его профиль

    fun loadUserPosts(view: View, userId: String) {
        val container = view.findViewById<LinearLayout>(
            R.id.profilePostsContainer
        ) ?: return

        val emptyContainer = view.findViewById<View>(
            R.id.profileEmptyContainer
        )

        activity.lifecycleScope.launch {
            try {
                val snapshot = db.collection("posts")
                    .whereEqualTo("authorId", userId)
                    .get()
                    .await()

                val posts = snapshot.documents
                    .mapNotNull { document ->
                        document.toObject(Post::class.java)
                            ?.copy(id = document.id)
                    }
                    .reversed()

                container.removeAllViews()

                android.util.Log.d(
                    "PostsManager",
                    "Публикаций найдено: ${posts.size}"
                )

                if (posts.isEmpty()) {
                    container.visibility = View.GONE
                    emptyContainer?.visibility = View.VISIBLE

                    view.findViewById<TextView>(
                        R.id.profileEmptyMessage
                    )?.text = "У тебя пока нет публикаций"
                } else {
                    container.visibility = View.VISIBLE
                    emptyContainer?.visibility = View.GONE

                    posts.forEach { post ->
                        container.addView(createPostCard(post))
                    }
                }

                view.findViewById<TextView>(
                    R.id.profilePostsCount
                )?.text = posts.size.toString()

            } catch (e: Exception) {
                android.util.Log.e(
                    "PostsManager",
                    "Ошибка загрузки публикаций профиля",
                    e
                )

                Toast.makeText(
                    activity,
                    "Не удалось загрузить публикации",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


    // Загружает сохранённые публикации текущего пользователя.

    fun loadSavedPosts(view: View) {
        val uid = com.google.firebase.auth.FirebaseAuth
            .getInstance().currentUser?.uid ?: return

        val container = view.findViewById<LinearLayout>(
            R.id.profileSavedPostsContainer
        ) ?: return

        val emptyContainer = view.findViewById<View>(
            R.id.profileSavedEmptyContainer
        )

        val emptyMessage = view.findViewById<TextView>(
            R.id.profileSavedEmptyMessage
        )

        activity.lifecycleScope.launch {
            try {
                container.removeAllViews()
                emptyContainer?.visibility = View.GONE

                val savedSnapshot = db.collection("users")
                    .document(uid)
                    .collection("savedPosts")
                    .get()
                    .await()

                val savedIds = savedSnapshot.documents
                    .map { it.getString("postId") ?: it.id }
                    .toSet()

                if (savedIds.isEmpty()) {
                    emptyMessage?.text =
                        "Здесь пока нет сохранённых публикаций"
                    emptyContainer?.visibility = View.VISIBLE
                    return@launch
                }

                // Загружаем публикации одним запросом.
                val allPosts = db.collection("posts")
                    .get()
                    .await()

                val postsById = allPosts.documents
                    .filter { it.id in savedIds }
                    .mapNotNull { document ->
                        document.toObject(Post::class.java)
                            ?.copy(id = document.id)
                    }

                // Сохраняем порядок по времени сохранения.
                val orderedPosts = savedSnapshot.documents
                    .mapNotNull { savedDocument ->
                        val postId = savedDocument.getString("postId")
                            ?: savedDocument.id

                        postsById.find { it.id == postId }
                    }

                container.removeAllViews()

                if (orderedPosts.isEmpty()) {
                    emptyMessage?.text =
                        "Сохранённые публикации больше недоступны"
                    emptyContainer?.visibility = View.VISIBLE
                } else {
                    orderedPosts.forEach { post ->
                        container.addView(createPostCard(post))
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e(
                    "PostsManager",
                    "Ошибка загрузки сохранённых публикаций",
                    e
                )

                emptyMessage?.text =
                    "Не удалось загрузить сохранённое"
                emptyContainer?.visibility = View.VISIBLE
            }
        }
    }




    fun createProfilePostCard(post: Post): View {
        return createPostCard(post)
    }
    // Создаём карточку публикации
    private fun createPostCard(post: Post): View {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 14)

            background = GradientDrawable().apply {
                setColor(Color.rgb(26, 26, 32))
                cornerRadius = 18f
            }

            clipToOutline = true

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }


        // Контейнер изображения и категории поверх него
        val imageContainer = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                190
            )
        }

        // Обложка публикации
        val image = ImageView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Обложка публикации"
        }

        if (post.imageUrl.isNotBlank()) {
            Glide.with(activity)
                .load(post.imageUrl)
                .centerCrop()
                .into(image)
        }

        imageContainer.addView(image)

        // Синяя плашка с категорией в левом нижнем углу
        val categoryLabel = TextView(activity).apply {
            text = post.postType.uppercase()
            textSize = 11f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)

            background = androidx.core.content.ContextCompat.getDrawable(
                activity,
                R.drawable.bg_post_category
            )

            setPadding(12, 7, 12, 7)

            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.START
            ).apply {
                setMargins(12, 12, 12, 12)
            }
        }

        imageContainer.addView(categoryLabel)


        // Кнопка сохранения публикации
        val bookmarkButton = TextView(activity).apply {
            text = "🔖"
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(12, 8, 12, 8)
            contentDescription = "Сохранить публикацию"
            background = GradientDrawable().apply {
                setColor(Color.rgb(30, 30, 40))
                cornerRadius = 14f
            }

            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                setMargins(0, 12, 12, 0)
            }
        }

        imageContainer.addView(bookmarkButton)

        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (uid != null && post.id.isNotBlank()) {
            val savedRef = db.collection("users")
                .document(uid)
                .collection("savedPosts")
                .document(post.id)

            var isSaved = false
            var requestInProgress = false

            // Проверяем, сохранена ли публикация ранее.
            activity.lifecycleScope.launch {
                try {
                    isSaved = savedRef.get().await().exists()

                    bookmarkButton.text =
                        if (isSaved) "🔖" else "♧"

                    bookmarkButton.contentDescription =
                        if (isSaved) {
                            "Убрать из сохранённого"
                        } else {
                            "Сохранить публикацию"
                        }
                } catch (e: Exception) {
                    android.util.Log.e(
                        "PostsManager",
                        "Не удалось проверить сохранение",
                        e
                    )
                }
            }

            bookmarkButton.setOnClickListener {
                if (requestInProgress) {
                    return@setOnClickListener
                }

                requestInProgress = true
                bookmarkButton.isEnabled = false

                activity.lifecycleScope.launch {
                    try {
                        if (isSaved) {
                            savedRef.delete().await()
                            isSaved = false

                            Toast.makeText(
                                activity,
                                "Публикация удалена из сохранённого",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            savedRef.set(
                                mapOf(
                                    "postId" to post.id,
                                    "createdAt" to
                                            FieldValue.serverTimestamp()
                                )
                            ).await()

                            isSaved = true

                            Toast.makeText(
                                activity,
                                "Публикация сохранена",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        bookmarkButton.text =
                            if (isSaved) "🔖" else "♧"

                        bookmarkButton.contentDescription =
                            if (isSaved) {
                                "Убрать из сохранённого"
                            } else {
                                "Сохранить публикацию"
                            }

                    } catch (e: Exception) {
                        android.util.Log.e(
                            "PostsManager",
                            "Ошибка сохранения публикации",
                            e
                        )

                        Toast.makeText(
                            activity,
                            "Не удалось изменить сохранение",
                            Toast.LENGTH_SHORT
                        ).show()
                    } finally {
                        requestInProgress = false
                        bookmarkButton.isEnabled = true
                    }
                }
            }
        } else {
            bookmarkButton.isEnabled = false
        }



        val textContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 4)
        }

        fun addText(
            value: String,
            size: Float,
            color: Int,
            bold: Boolean = false
        ) {
            val text = TextView(activity).apply {
                text = value
                textSize = size
                setTextColor(color)

                if (bold) {
                    setTypeface(null, Typeface.BOLD)
                }

                setPadding(0, 4, 0, 4)
            }

            textContainer.addView(text)
        }

        // Заголовок публикации
        addText(
            post.title,
            19f,
            Color.WHITE,
            true
        )

        // Игра
        addText(
            "🎮 ${post.game}",
            13f,
            Color.LTGRAY
        )

        // Полный текст публикации
        addText(
            post.content,
            14f,
            Color.rgb(220, 220, 225)
        )

        // Автор публикации

        // Имя автора — кнопка перехода в профиль.
        val author = post.authorName.ifBlank {
            "Игрок GamerHub"
        }

        val authorView = TextView(activity).apply {
            text = "👤 $author"
            textSize = 12f
            setTextColor(Color.rgb(180, 140, 255))
            setPadding(0, 4, 0, 4)
            isClickable = true
            isFocusable = true

            setOnClickListener {
                if (post.authorId.isNotBlank()) {
                    onAuthorClick?.invoke(post.authorId)
                } else {
                    Toast.makeText(
                        activity,
                        "Профиль автора недоступен",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        textContainer.addView(authorView)


        // Теги публикации
        val tags = (post.gameTags + post.topicTags).distinct()

        if (tags.isNotEmpty()) {
            addText(
                tags.joinToString("  •  "),
                12f,
                Color.rgb(180, 150, 255)
            )
        }



        card.addView(imageContainer)
        card.addView(textContainer)

        card.isClickable = true
        card.isFocusable = true

        card.setOnClickListener {
            onPostClick?.invoke(post)
        }

        return card
    }
}
