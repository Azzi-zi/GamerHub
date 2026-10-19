package com.example.gamerhub

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import android.net.Uri
import android.widget.ImageView
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileManager(
    private val view: View,
    private val repository: GameRepository
) {
    private val availableRoles = listOf(
        "🎮 Геймер",
        "📚 Лоровед",
        "✍️ Писатель",
        "💻 Разработчик",
        "🎨 Художник",
        "🎥 Контент-мейкер",
        "🧪 Тестировщик",
        "🎵 Музыкант"
    )
    private fun renderRoles(rolesString: String) {
        val container = view.findViewById<android.widget.LinearLayout>(
            R.id.profileRolesContainer
        )

        // Удаляем старые роли перед отображением новых
        container.removeAllViews()

        // Преобразуем строку из базы в список ролей

        val roles = rolesString
            .split('|')
            .map { it.trim() }
            .filter { it.isNotBlank() }


        for (role in roles) {
            val roleView = TextView(view.context).apply {
                text = role
                textSize = 12f
                setTextColor(android.graphics.Color.WHITE)
                setPadding(32, 16, 32, 16)
                setBackgroundResource(R.drawable.bg_category_active)
            }

            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )

            params.marginEnd = 16
            container.addView(roleView, params)
        }
    }

    // Загружаем профиль текущего пользователя

    private fun loadProfile() {
        val activity = view.context as MainActivity
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val uid = user.uid

        activity.lifecycleScope.launch {
            try {
                val profile = withContext(Dispatchers.IO) {
                    // Сначала ищем уже существующий локальный профиль.
                    val localProfile = repository.getUserProfile(uid)

                    if (localProfile != null) {
                        localProfile
                    } else {
                        // Если его нет, берём имя из регистрации в Firestore.
                        val document = Tasks.await(
                            FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(uid)
                                .get()
                        )

                        val username = document.getString("username")
                            ?: throw IllegalStateException(
                                "Имя пользователя не найдено в базе"
                            )

                        val tag = document.getString("tag")
                            ?: "@${username.lowercase().replace(" ", "_")}"

                        // Создаём локальный профиль на основе данных регистрации.
                        val newProfile = UserProfile(
                            uid = uid,
                            username = username,
                            tag = tag,
                            bio = document.getString("bio") ?: "",
                            roles = document.getString("roles") ?: "",
                            avatarUri = document.getString("avatarUri") ?: ""
                        )

                        repository.saveUserProfile(newProfile)
                        newProfile
                    }
                }

                view.findViewById<TextView>(R.id.profileUsername).text =
                    profile.username

                view.findViewById<TextView>(R.id.profileTag).text =
                    profile.tag

                view.findViewById<TextView>(R.id.profileBio).text =
                    profile.bio

                renderRoles(profile.roles)

                val avatarView =
                    view.findViewById<ImageView>(R.id.profileAvatar)

                if (profile.avatarUri.isNotBlank()) {
                    try {
                        avatarView.setImageURI(Uri.parse(profile.avatarUri))
                    } catch (_: Exception) {
                        // Оставляем стандартный аватар.
                    }
                }

            } catch (e: Exception) {
                Toast.makeText(
                    view.context,
                    "Не удалось загрузить профиль: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }


    private fun saveAvatar(uri: Uri) {
        val activity = view.context as MainActivity
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        activity.lifecycleScope.launch {
            try {
                // Сохраняем адрес фотографии в Room
                withContext(Dispatchers.IO) {
                    val oldProfile = repository.getUserProfile(uid)
                        ?: UserProfile(uid = uid)

                    repository.saveUserProfile(
                        oldProfile.copy(
                            avatarUri = uri.toString()
                        )
                    )
                }

                // Сразу показываем выбранную фотографию
                val avatarView = view.findViewById<ImageView>(
                    R.id.profileAvatar
                )
                avatarView.setImageURI(uri)

                Toast.makeText(
                    view.context,
                    "Аватар обновлён",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                Toast.makeText(
                    view.context,
                    "Не удалось сохранить аватар",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    fun setup() {
        loadProfile()

        // Кнопка с гаечным ключом открывает настройки
        val settingsButton = view.findViewById<View>(
            R.id.profileSettings
        )

        settingsButton.setOnClickListener {
            showSettingsDialog()
        }


        // Нажатие на аватарку открывает галерею
        val avatarContainer = view.findViewById<View>(
            R.id.profileAvatarContainer
        )

        avatarContainer.setOnClickListener {
            val activity = view.context as MainActivity

            activity.openAvatarPicker { uri ->
                saveAvatar(uri)
            }
        }



        // Кнопка редактирования профиля
        val editButton =
            view.findViewById<View>(R.id.editProfileButton)

        editButton.setOnClickListener {
            showEditDialog()
        }

        // Отображаем почту текущего пользователя
        val emailView =
            view.findViewById<TextView>(R.id.profileEmail)

        val currentUser = FirebaseAuth.getInstance().currentUser
        emailView.text = currentUser?.email ?: "Аккаунт не найден"

        // Кнопка выхода из аккаунта
        val logoutButton =
            view.findViewById<View>(R.id.logoutButton)

        logoutButton.setOnClickListener {
            val activity = view.context as MainActivity

            FirebaseAuth.getInstance().signOut()

            activity.startActivity(
                Intent(activity, LoginActivity::class.java)
            )

            activity.finish()
        }

        // Вкладки публикаций и сохранённого
        val postsTab =
            view.findViewById<TextView>(R.id.profilePostsTab)

        val savedTab =
            view.findViewById<TextView>(R.id.profileSavedTab)

        val emptyMessage =
            view.findViewById<TextView>(R.id.profileEmptyMessage)

        val emptyIcon =
            view.findViewById<TextView>(R.id.profilePostsEmpty)

        fun selectTab(showPosts: Boolean) {
            if (showPosts) {
                postsTab.setTextColor(
                    android.graphics.Color.rgb(155, 122, 255)
                )
                savedTab.setTextColor(
                    android.graphics.Color.rgb(136, 136, 146)
                )

                postsTab.setTypeface(null, Typeface.BOLD)
                savedTab.setTypeface(null, Typeface.NORMAL)

                emptyIcon.text = "📝"
                emptyMessage.text = "У тебя пока нет публикаций"
            } else {
                savedTab.setTextColor(
                    android.graphics.Color.rgb(155, 122, 255)
                )
                postsTab.setTextColor(
                    android.graphics.Color.rgb(136, 136, 146)
                )

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

        // По умолчанию открываем публикации
        selectTab(true)
    }


    private fun styleDialog(dialog: AlertDialog) {
        val purple = android.graphics.Color.rgb(155, 122, 255)
        val white = android.graphics.Color.WHITE
        val gray = android.graphics.Color.rgb(170, 170, 170)
        val dark = android.graphics.Color.rgb(26, 26, 32)

        // Делаем заголовок белым
        val titleId = view.resources.getIdentifier(
            "alertTitle",
            "id",
            "android"
        )

        if (titleId != 0) {
            dialog.findViewById<TextView>(titleId)
                ?.setTextColor(android.graphics.Color.WHITE)
        }
        // Скруглённый тёмный фон
        val background = android.graphics.drawable.GradientDrawable().apply {
            setColor(dark)
            cornerRadius = 20 * view.resources.displayMetrics.density
        }

        dialog.window?.setBackgroundDrawable(background)



        // Текст сообщения, если он есть
        dialog.findViewById<TextView>(android.R.id.message)
            ?.setTextColor(gray)

        // Список пунктов, например в окне настроек
        dialog.listView?.let { list ->
            list.setBackgroundColor(dark)
            list.divider = android.graphics.drawable.ColorDrawable(
                android.graphics.Color.rgb(48, 48, 57)
            )
            list.dividerHeight = 1

            // Перекрашиваем видимые пункты списка
            for (i in 0 until list.childCount) {
                (list.getChildAt(i) as? TextView)?.setTextColor(white)
            }
        }

        // Кнопки
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(purple)

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            ?.setTextColor(purple)

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            ?.setTextColor(purple)
    }



    private fun showSettingsDialog() {
        val options = arrayOf(
            "Редактировать профиль",
            "Изменить аватар",
            "Информация о приложении"
        )

        val white = android.graphics.Color.WHITE
        val purple = android.graphics.Color.rgb(155, 122, 255)
        val dark = android.graphics.Color.rgb(26, 26, 32)

        val dialog = AlertDialog.Builder(view.context)
            .setTitle("Настройки GamerHub")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditDialog()

                    1 -> {
                        val activity = view.context as MainActivity
                        activity.openAvatarPicker { uri ->
                            saveAvatar(uri)
                        }
                    }

                    2 -> {
                        val aboutDialog = AlertDialog.Builder(view.context)
                            .setTitle("О GamerHub")
                            .setMessage(
                                "GamerHub — место для общения игроков, " +
                                        "обсуждения игр и обмена игровым опытом."
                            )
                            .setPositiveButton("Понятно", null)
                            .create()

                        aboutDialog.setOnShowListener {
                            aboutDialog.window?.setBackgroundDrawable(
                                android.graphics.drawable.ColorDrawable(dark)
                            )

                            aboutDialog.findViewById<TextView>(
                                android.R.id.message
                            )?.setTextColor(white)

                            aboutDialog.getButton(
                                AlertDialog.BUTTON_POSITIVE
                            )?.setTextColor(purple)
                        }

                        aboutDialog.show()
                    }
                }
            }
            .setNegativeButton("Закрыть", null)
            .create()

        dialog.setOnShowListener {
            val titleId = view.resources.getIdentifier(
                "alertTitle",
                "id",
                "android"
            )

            if (titleId != 0) {
                dialog.findViewById<TextView>(titleId)
                    ?.setTextColor(android.graphics.Color.WHITE)
            }
            dialog.window?.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(dark)
            )

            // Красим все пункты списка в белый цвет
            val list = dialog.listView
            list.setBackgroundColor(dark)

            for (i in 0 until list.childCount) {
                (list.getChildAt(i) as? TextView)?.setTextColor(white)
            }

            dialog.getButton(
                AlertDialog.BUTTON_NEGATIVE
            )?.setTextColor(purple)
        }

        dialog.show()
    }



    // Окно редактирования профиля

    private fun showEditDialog() {
        val usernameView = view.findViewById<TextView>(R.id.profileUsername)
        val tagView = view.findViewById<TextView>(R.id.profileTag)
        val bioView = view.findViewById<TextView>(R.id.profileBio)

        val activity = view.context as MainActivity

        val fields = android.widget.LinearLayout(view.context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }
        fields.setBackgroundColor(
            android.graphics.Color.rgb(26, 26, 32)
        )

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

        val purple = android.graphics.Color.rgb(155, 122, 255)
        val white = android.graphics.Color.WHITE
        val hintGray = android.graphics.Color.rgb(170, 170, 170)

        // Делаем все поля ввода подходящими для тёмной темы
        listOf(usernameInput, tagInput, bioInput).forEach { input ->
            input.setTextColor(white)
            input.setHintTextColor(hintGray)

            // Фиолетовый цвет линии поля ввода
            input.backgroundTintList =
                android.content.res.ColorStateList.valueOf(purple)
        }


        fields.addView(usernameInput)
        fields.addView(tagInput)
        fields.addView(bioInput)

        // Заголовок списка ролей
        val rolesTitle = TextView(view.context).apply {
            text = "Мои роли"
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 24, 0, 8)
        }
        fields.addView(rolesTitle)

        // Загружаем роли, которые уже отображаются в профиле
        val rolesContainer = view.findViewById<android.widget.LinearLayout>(
            R.id.profileRolesContainer
        )

        val currentRoles = (0 until rolesContainer.childCount)
            .map { rolesContainer.getChildAt(it) }
            .map { (it as TextView).text.toString() }
            .toSet()

        val roleCheckboxes = mutableMapOf<String, android.widget.CheckBox>()


        for (role in availableRoles) {
            val checkBox = android.widget.CheckBox(view.context).apply {
                text = role
                setTextColor(android.graphics.Color.WHITE)
                buttonTintList =
                    android.content.res.ColorStateList.valueOf(purple)
                isChecked = role in currentRoles
            }

            roleCheckboxes[role] = checkBox
            fields.addView(checkBox)
        }


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

                val selectedRoles = roleCheckboxes
                    .filter { it.value.isChecked }
                    .keys
                    .joinToString("|")

                if (selectedRoles.isBlank()) {
                    Toast.makeText(
                        view.context,
                        "Выбери хотя бы одну роль",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                activity.lifecycleScope.launch {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid

                    if (uid == null) {
                        Toast.makeText(
                            view.context,
                            "Не удалось определить аккаунт",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }

                    try {
                        withContext(Dispatchers.IO) {
                            val oldProfile = repository.getUserProfile(uid)

                            // Сохраняем профиль на устройстве в Room
                            repository.saveUserProfile(
                                UserProfile(
                                    uid = uid,
                                    username = username,
                                    tag = normalizedTag,
                                    bio = bio,
                                    roles = selectedRoles,
                                    avatarUri = oldProfile?.avatarUri ?: ""
                                )
                            )

                            // Подготавливаем обновлённые данные для Firebase
                            val db = FirebaseFirestore.getInstance()
                            val batch = db.batch()

                            val privateProfile = hashMapOf(
                                "uid" to uid,
                                "username" to username,
                                "tag" to normalizedTag,
                                "bio" to bio,
                                "roles" to selectedRoles,
                                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            )

                            val publicProfile = hashMapOf(
                                "uid" to uid,
                                "username" to username,
                                "tag" to normalizedTag
                            )

                            // Обновляем оба документа, не удаляя остальные поля
                            batch.set(
                                db.collection("users").document(uid),
                                privateProfile,
                                SetOptions.merge()
                            )

                            batch.set(
                                db.collection("publicProfiles").document(uid),
                                publicProfile,
                                SetOptions.merge()
                            )

                            // Ждём завершения записи в Firebase
                            Tasks.await(batch.commit())
                        }

                        // Обновляем отображение профиля
                        usernameView.text = username
                        tagView.text = normalizedTag
                        bioView.text = bio
                        renderRoles(selectedRoles)

                        Toast.makeText(
                            view.context,
                            "Профиль сохранён",
                            Toast.LENGTH_SHORT
                        ).show()

                    } catch (e: Exception) {
                        android.util.Log.e(
                            "GamerHubProfile",
                            "Ошибка сохранения профиля",
                            e
                        )

                        Toast.makeText(
                            view.context,
                            "Ошибка: ${e.message ?: e.javaClass.simpleName}",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                }
            }
            .show()
            .also { styleDialog(it) }
    }

}
