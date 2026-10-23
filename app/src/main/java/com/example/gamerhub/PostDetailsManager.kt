
package com.example.gamerhub

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class PostDetailsManager(
    private val activity: AppCompatActivity,
    private val post: Post,
    private val onBack: () -> Unit
) {

    fun setup(view: View) {
        // Кнопка возврата
        view.findViewById<TextView>(
            R.id.postDetailsBackButton
        ).setOnClickListener {
            onBack()
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
        view.findViewById<TextView>(
            R.id.postDetailsAuthor
        ).text = "Автор: ${post.authorName}"

        // Полный текст публикации
        view.findViewById<TextView>(
            R.id.postDetailsContent
        ).text = post.content

        // Теги
        val tags = (
                post.gameTags +
                        post.topicTags +
                        post.tags
                ).distinct()

        view.findViewById<TextView>(
            R.id.postDetailsTags
        ).text = tags.joinToString("  •  ") { "#$it" }
    }
}
