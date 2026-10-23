
package com.example.gamerhub

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

class PostsManager(
    private val activity: AppCompatActivity
) {

    private val db = FirebaseFirestore.getInstance()

    private var onPostClick: ((Post) -> Unit)? = null

    fun setOnPostClickListener(listener: (Post) -> Unit) {
        onPostClick = listener
    }
    // Загружаем публикации из Firestore
    fun loadPosts(view: View, category: String = "Все") {
        val container = view.findViewById<LinearLayout>(
            R.id.postsContainer
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
                        category == "Все" ||
                                post.postType.equals(
                                    category,
                                    ignoreCase = true
                                )
                    }
                    .reversed()

                container.removeAllViews()

                if (posts.isEmpty()) {
                    val emptyText = TextView(activity).apply {
                        text = "Публикаций пока нет"
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

        val image = ImageView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                190
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        if (post.imageUrl.isNotBlank()) {
            Glide.with(activity)
                .load(post.imageUrl)
                .centerCrop()
                .into(image)
        }

        card.addView(image)

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
                this.text = value
                textSize = size
                setTextColor(color)

                if (bold) {
                    setTypeface(null, Typeface.BOLD)
                }

                setPadding(0, 4, 0, 4)
            }

            textContainer.addView(text)
        }

        addText(
            post.postType.uppercase(),
            11f,
            Color.rgb(180, 110, 255),
            true
        )

        addText(
            post.title,
            19f,
            Color.WHITE,
            true
        )

        addText(
            "🎮 ${post.game}",
            13f,
            Color.LTGRAY
        )

        addText(
            post.content,
            14f,
            Color.rgb(220, 220, 225)
        )

        addText(
            "Автор: ${post.authorName}",
            12f,
            Color.rgb(160, 160, 170)
        )

        val tags = (post.gameTags + post.topicTags).distinct()

        if (tags.isNotEmpty()) {
            addText(
                tags.joinToString("  •  "),
                12f,
                Color.rgb(180, 150, 255)
            )
        }

        card.addView(textContainer)

        card.isClickable = true
        card.isFocusable = true

        card.setOnClickListener {
            onPostClick?.invoke(post)
        }

        return card
    }
}
