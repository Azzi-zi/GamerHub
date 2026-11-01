
package com.example.gamerhub

import android.graphics.Color
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.widget.LinearLayout
class PublicProfileManager(
    private val activity: AppCompatActivity,
    private val userId: String,
    private val postsManager: PostsManager,
    private val onBack: () -> Unit
) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var followersListener: ListenerRegistration? = null
    private var followingListener: ListenerRegistration? = null
    private var myFollowListener: ListenerRegistration? = null

    private var requestInProgress = false

    fun setup(view: View) {
        view.findViewById<View>(
            R.id.publicProfileBackButton
        ).setOnClickListener {
            onBack()
        }

        loadProfile(view)
        postsManager.loadUserPosts(view, userId)
        setupFollow(view)
    }

    private fun setupFollow(view: View) {
        val currentUid = auth.currentUser?.uid
        val followButton = view.findViewById<Button>(
            R.id.publicProfileFollowButton
        )

        // Нельзя подписаться на самого себя.
        if (currentUid == userId) {
            followButton.visibility = View.GONE
        } else if (currentUid == null) {
            followButton.isEnabled = false
            followButton.text = "Войди, чтобы подписаться"
        } else {
            followButton.setOnClickListener {
                toggleFollow(view, currentUid)
            }

            // Слушаем, подписан ли текущий пользователь.
            myFollowListener = db.collection("users")
                .document(userId)
                .collection("followers")
                .document(currentUid)
                .addSnapshotListener { document, error ->
                    if (error != null || document == null) {
                        Log.e(
                            "PublicProfileManager",
                            "Не удалось проверить подписку",
                            error
                        )
                        return@addSnapshotListener
                    }

                    val isFollowing = document.exists()

                    followButton.text = if (isFollowing) {
                        "Отписаться"
                    } else {
                        "Подписаться"
                    }

                    followButton.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(
                            if (isFollowing) {
                                Color.rgb(55, 45, 70)
                            } else {
                                Color.rgb(139, 92, 246)
                            }
                        )
                }
        }

        // Подсчитываем подписчиков в реальном времени.
        followersListener = db.collection("users")
            .document(userId)
            .collection("followers")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(
                        "PublicProfileManager",
                        "Ошибка подсчёта подписчиков",
                        error
                    )
                    return@addSnapshotListener
                }

                view.findViewById<TextView>(
                    R.id.publicProfileFollowersCount
                ).text = (snapshot?.size() ?: 0).toString()
            }

        // Подсчитываем подписки этого пользователя.
        followingListener = db.collection("users")
            .document(userId)
            .collection("following")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(
                        "PublicProfileManager",
                        "Ошибка подсчёта подписок",
                        error
                    )
                    return@addSnapshotListener
                }

                view.findViewById<TextView>(
                    R.id.publicProfileFollowingCount
                ).text = (snapshot?.size() ?: 0).toString()
            }
    }

    private fun toggleFollow(view: View, currentUid: String) {
        if (requestInProgress) return

        if (currentUid == userId) return

        val button = view.findViewById<Button>(
            R.id.publicProfileFollowButton
        )

        requestInProgress = true
        button.isEnabled = false

        activity.lifecycleScope.launch {
            try {
                val followerRef = db.collection("users")
                    .document(userId)
                    .collection("followers")
                    .document(currentUid)

                val followingRef = db.collection("users")
                    .document(currentUid)
                    .collection("following")
                    .document(userId)

                val existingFollow = followerRef.get().await()

                if (existingFollow.exists()) {
                    // Отписка: удаляем связь в обоих местах.
                    db.runBatch { batch ->
                        batch.delete(followerRef)
                        batch.delete(followingRef)
                    }.await()

                    Toast.makeText(
                        activity,
                        "Ты отписался от пользователя",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val actorName = getCurrentUsername(currentUid)

                    // Создаём связь подписки и уведомление одним пакетом.
                    val notificationRef = db.collection("users")
                        .document(userId)
                        .collection("notifications")
                        .document()

                    db.runBatch { batch ->
                        batch.set(
                            followerRef,
                            mapOf(
                                "uid" to currentUid,
                                "username" to actorName,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        )

                        batch.set(
                            followingRef,
                            mapOf(
                                "uid" to userId,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        )

                        batch.set(
                            notificationRef,
                            mapOf(
                                "type" to "follow",
                                "actorId" to currentUid,
                                "actorName" to actorName,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "read" to false
                            )
                        )
                    }.await()

                    Toast.makeText(
                        activity,
                        "Ты подписался на пользователя",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(
                    "PublicProfileManager",
                    "Ошибка изменения подписки",
                    e
                )

                Toast.makeText(
                    activity,
                    "Не удалось изменить подписку. Проверь правила Firestore.",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                requestInProgress = false
                button.isEnabled = true
            }
        }
    }

    private suspend fun getCurrentUsername(uid: String): String {
        val publicProfile = db.collection("publicProfiles")
            .document(uid)
            .get()
            .await()

        val publicName = publicProfile.getString("username")

        if (!publicName.isNullOrBlank()) {
            return publicName
        }

        val userProfile = db.collection("users")
            .document(uid)
            .get()
            .await()

        return userProfile.getString("username")
            ?.takeIf { it.isNotBlank() }
            ?: "Игрок GamerHub"
    }


    private fun loadProfile(view: View) {
        activity.lifecycleScope.launch {
            try {
                val document = db.collection("publicProfiles")
                    .document(userId)
                    .get()
                    .await()

                if (!document.exists()) {
                    Toast.makeText(
                        activity,
                        "Публичный профиль пользователя не найден",
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

                view.findViewById<TextView>(
                    R.id.publicProfileTag
                ).text = document.getString("tag")
                    ?: "@${username.lowercase().replace(" ", "_")}"

                // Описание
                val bio = document.getString("bio").orEmpty()

                view.findViewById<TextView>(
                    R.id.publicProfileBio
                ).text = bio.ifBlank {
                    "Пользователь пока ничего не рассказал о себе."
                }

                // Роли пользователя
                val roles = document.getString("roles")
                    .orEmpty()
                    .split("|")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

                view.findViewById<TextView>(
                    R.id.publicProfileRoles
                ).text = roles.joinToString("  •  ")
                    .ifBlank { "Роли не указаны" }

                // Любимые игры
                val favoriteGames = (
                        document.get("favoriteGames") as? List<*>
                        ).orEmpty().filterIsInstance<String>()

                val gamesContainer = view.findViewById<LinearLayout>(
                    R.id.publicProfileFavoriteGames
                )

                gamesContainer.removeAllViews()

                if (favoriteGames.isEmpty()) {
                    gamesContainer.addView(
                        TextView(activity).apply {
                            text = "Любимые игры не указаны"
                            textSize = 13f
                            setTextColor(Color.LTGRAY)
                        }
                    )
                } else {
                    val density = activity.resources.displayMetrics.density

                    favoriteGames.forEach { game ->
                        val gameView = TextView(activity).apply {
                            text = game
                            textSize = 13f
                            setTextColor(Color.WHITE)

                            setPadding(
                                (12 * density).toInt(),
                                (9 * density).toInt(),
                                (12 * density).toInt(),
                                (9 * density).toInt()
                            )

                            background = android.graphics.drawable
                                .GradientDrawable().apply {
                                    cornerRadius = 12 * density
                                    setColor(Color.rgb(26, 26, 32))
                                    setStroke(
                                        (1 * density).toInt(),
                                        Color.rgb(55, 52, 72)
                                    )
                                }
                        }

                        val params = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams
                                .MATCH_PARENT,
                            android.widget.LinearLayout.LayoutParams
                                .WRAP_CONTENT
                        ).apply {
                            bottomMargin = (6 * density).toInt()
                        }

                        gamesContainer.addView(gameView, params)
                    }
                }

                // Аватар
                val avatarUrl = document.getString("avatarUrl")
                    ?: document.getString("avatarUri")
                    ?: ""

                val avatar = view.findViewById<ImageView>(
                    R.id.publicProfileAvatar
                )

                if (avatarUrl.startsWith("https://")) {
                    Glide.with(avatar)
                        .load(avatarUrl)
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .error(R.drawable.ic_launcher_foreground)
                        .into(avatar)
                } else {
                    avatar.setImageResource(
                        R.drawable.ic_launcher_foreground
                    )
                }

                // Обложка — в компактном контейнере, а не на весь экран
                val cover = view.findViewById<ImageView>(
                    R.id.publicProfileCover
                )

                val coverUrl = document.getString("coverUrl").orEmpty()

                if (coverUrl.startsWith("https://")) {
                    cover.visibility = View.VISIBLE

                    Glide.with(cover)
                        .load(coverUrl)
                        .placeholder(
                            android.graphics.drawable.ColorDrawable(
                                Color.rgb(26, 26, 32)
                            )
                        )
                        .error(
                            android.graphics.drawable.ColorDrawable(
                                Color.rgb(26, 26, 32)
                            )
                        )
                        .centerCrop()
                        .into(cover)
                } else {
                    Glide.with(cover).clear(cover)
                    cover.setImageDrawable(
                        android.graphics.drawable.ColorDrawable(
                            Color.rgb(26, 26, 32)
                        )
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


    fun stopListening() {
        followersListener?.remove()
        followingListener?.remove()
        myFollowListener?.remove()

        followersListener = null
        followingListener = null
        myFollowListener = null
    }
}
