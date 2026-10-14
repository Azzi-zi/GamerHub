
package com.example.gamerhub

import android.app.AlertDialog
import android.view.View
import android.widget.EditText

import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent
import android.graphics.Typeface
import com.google.firebase.auth.FirebaseAuth
class ProfileManager(
    private val view: View,
    private val repository: GameRepository
) {
    private fun loadProfile() {
        val activity = view.context as MainActivity

        activity.lifecycleScope.launch {
            val profile = withContext(Dispatchers.IO) {
                repository.getUserProfile()
            } ?: UserProfile().also { newProfile ->
                withContext(Dispatchers.IO) {
                    repository.saveUserProfile(newProfile)
                }
            }

            view.findViewById<TextView>(R.id.profileUsername).text =
                profile.username

            view.findViewById<TextView>(R.id.profileTag).text =
                profile.tag

            view.findViewById<TextView>(R.id.profileBio).text =
                profile.bio
        }
    }

    fun setup() {
        loadProfile()

        // Кнопка редактирования профиля
        val editButton = view.findViewById<View>(R.id.editProfileButton)
        editButton.setOnClickListener {
            showEditDialog()
        }

        // Показываем почту текущего пользователя Firebase
        val emailView = view.findViewById<TextView>(R.id.profileEmail)
        val currentUser = FirebaseAuth.getInstance().currentUser

        emailView.text = currentUser?.email ?: "Аккаунт не найден"

        // Кнопка выхода из аккаунта
        val logoutButton = view.findViewById<View>(R.id.logoutButton)
        logoutButton.setOnClickListener {
            val activity = view.context as MainActivity

            FirebaseAuth.getInstance().signOut()

            activity.startActivity(
                Intent(activity, LoginActivity::class.java)
            )

            activity.finish()
        }

        // Вкладки публикаций и сохранённого
        val postsTab = view.findViewById<TextView>(R.id.profilePostsTab)
        val savedTab = view.findViewById<TextView>(R.id.profileSavedTab)
        val emptyMessage = view.findViewById<TextView>(R.id.profileEmptyMessage)
        val emptyIcon = view.findViewById<TextView>(R.id.profilePostsEmpty)

        fun selectTab(showPosts: Boolean) {
            if (showPosts) {
                postsTab.setTextColor(android.graphics.Color.rgb(155, 122, 255))
                savedTab.setTextColor(android.graphics.Color.rgb(136, 136, 146))

                postsTab.setTypeface(null, Typeface.BOLD)
                savedTab.setTypeface(null, Typeface.NORMAL)

                emptyIcon.text = "📝"
                emptyMessage.text = "У тебя пока нет публикаций"
            } else {
                savedTab.setTextColor(android.graphics.Color.rgb(155, 122, 255))
                postsTab.setTextColor(android.graphics.Color.rgb(136, 136, 146))

                savedTab.setTypeface(null, Typeface.BOLD)
                postsTab.setTypeface(null, Typeface.NORMAL)

                emptyIcon.text = "🔖"
                emptyMessage.text = "Здесь пока ничего нет"
            }
        }

        postsTab.setOnClickListener {
            selectTab(true)
        }

        savedTab.setOnClickListener {
            selectTab(false)
        }

        // При открытии профиля показываем публикации
        selectTab(true)
    }


    private fun showEditDialog() {
        val usernameView = view.findViewById<TextView>(R.id.profileUsername)
        val tagView = view.findViewById<TextView>(R.id.profileTag)
        val bioView = view.findViewById<TextView>(R.id.profileBio)

        val fields = android.widget.LinearLayout(view.context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }

        val usernameInput = EditText(view.context).apply {
            hint = "Имя пользователя"
            setText(usernameView.text)
        }

        val tagInput = EditText(view.context).apply {
            hint = "@username"
            setText(tagView.text)
        }

        val bioInput = EditText(view.context).apply {
            hint = "О себе"
            setText(bioView.text)
            minLines = 3
            gravity = android.view.Gravity.TOP
        }

        fields.addView(usernameInput)
        fields.addView(tagInput)
        fields.addView(bioInput)

        AlertDialog.Builder(view.context)
            .setTitle("Редактировать профиль")
            .setView(fields)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить") { _, _ ->
                val username = usernameInput.text.toString().trim()
                val tag = tagInput.text.toString().trim()
                val bio = bioInput.text.toString().trim()

                if (username.isBlank() || tag.isBlank()) {
                    Toast.makeText(
                        view.context,
                        "Имя и тег не должны быть пустыми",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                val normalizedTag = if (tag.startsWith("@")) tag else "@$tag"

                usernameView.text = username
                tagView.text = normalizedTag
                bioView.text = bio

                val activity = view.context as MainActivity

                activity.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        repository.saveUserProfile(
                            UserProfile(
                                username = username,
                                tag = normalizedTag,
                                bio = bio
                            )
                        )
                    }

                    Toast.makeText(
                        view.context,
                        "Профиль сохранён",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }
}
