
package com.example.gamerhub

import android.graphics.Color
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PublicProfileManager(
    private val activity: AppCompatActivity,
    private val userId: String,
    private val postsManager: PostsManager,
    private val onBack: () -> Unit
) {

    private val db = FirebaseFirestore.getInstance()

    fun setup(view: View) {
        view.findViewById<View>(
            R.id.publicProfileBackButton
        ).setOnClickListener {
            onBack()
        }

        loadProfile(view)

        // Загружаем публикации именно этого пользователя.
        postsManager.loadUserPosts(view, userId)
    }

    private fun loadProfile(view: View) {
        activity.lifecycleScope.launch {
            try {
                // Публичные данные предназначены для просмотра другими игроками.
                var document = db.collection("publicProfiles")
                    .document(userId)
                    .get()
                    .await()

                // Если публичного документа нет, пробуем профиль users.
                if (!document.exists()) {
                    document = db.collection("users")
                        .document(userId)
                        .get()
                        .await()
                }

                if (!document.exists()) {
                    Toast.makeText(
                        activity,
                        "Профиль пользователя не найден",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val username = document.getString("username")
                    ?.takeIf { it.isNotBlank() }
                    ?: "Игрок GamerHub"

                view.findViewById<TextView>(
                    R.id.publicProfileUsername
                ).text = username

                val tag = document.getString("tag")
                    ?: "@${username.lowercase().replace(" ", "_")}"

                view.findViewById<TextView>(
                    R.id.publicProfileTag
                ).text = tag

                view.findViewById<TextView>(
                    R.id.publicProfileBio
                ).text = document.getString("bio").orEmpty()
                    .ifBlank { "Пользователь пока ничего не рассказал о себе." }

                val roles = document.getString("roles")
                    .orEmpty()
                    .split("|")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

                view.findViewById<TextView>(
                    R.id.publicProfileRoles
                ).text = roles.joinToString("  •  ")

                val avatarUrl = document.getString("avatarUrl")
                    ?: document.getString("avatarUri")
                    ?: ""

                val avatar = view.findViewById<ImageView>(
                    R.id.publicProfileAvatar
                )

                if (avatarUrl.startsWith("https://")) {
                    Glide.with(activity)
                        .load(avatarUrl)
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .error(R.drawable.ic_launcher_foreground)
                        .into(avatar)
                } else {
                    avatar.setImageResource(
                        R.drawable.ic_launcher_foreground
                    )
                }

            } catch (e: Exception) {
                Log.e(
                    "PublicProfileManager",
                    "Ошибка загрузки профиля $userId",
                    e
                )

                Toast.makeText(
                    activity,
                    "Не удалось загрузить профиль",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
