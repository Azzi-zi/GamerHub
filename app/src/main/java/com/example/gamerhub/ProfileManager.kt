package com.example.gamerhub

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class ProfileManager(
    private val view: View,
    private val postsManager: PostsManager
) {


    private val activity: MainActivity
        get() = view.context as MainActivity

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val purple = android.graphics.Color.rgb(155, 122, 255)
    private val white = android.graphics.Color.WHITE
    private val gray = android.graphics.Color.rgb(136, 136, 146)
    private val lightGray = android.graphics.Color.rgb(170, 170, 170)
    private val dark = android.graphics.Color.rgb(26, 26, 32)

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

    // Главная точка запуска менеджера профиля.
    fun setup() {
        loadProfile()
        loadProfileLikes()
        setupProfileActions()
        setupTabs()
    }

    // Загружает данные пользователя из Firestore.
    private fun loadProfile() {
        val user = auth.currentUser ?: return

        activity.lifecycleScope.launch {
            try {
                val document = withContext(Dispatchers.IO) {
                    Tasks.await(
                        db.collection("users")
                            .document(user.uid)
                            .get()
                    )
                }

                check(document.exists()) {
                    "Профиль не найден в Firestore"
                }

                val username = document.getString("username")
                    ?: error("Имя пользователя не найдено")

                val tag = document.getString("tag")
                    ?: "@${username.lowercase().replace(" ", "_")}"

                view.findViewById<TextView>(
                    R.id.profileUsername
                ).text = username

                view.findViewById<TextView>(
                    R.id.profileTag
                ).text = tag

                view.findViewById<TextView>(
                    R.id.profileBio
                ).text = document.getString("bio").orEmpty()

                renderRoles(
                    document.getString("roles").orEmpty()
                )

                val avatarUrl = document.getString("avatarUrl")
                    ?: document.getString("avatarUri")
                    ?: ""

                showAvatar(avatarUrl)

            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки профиля", e)

                showToast(
                    "Не удалось загрузить профиль: ${e.localizedMessage}"
                )
            }
        }
    }

    // Показывает аватар пользователя.
    private fun showAvatar(url: String) {
        val avatar = view.findViewById<ImageView>(
            R.id.profileAvatar
        )

        if (url.startsWith("https://")) {
            Glide.with(avatar)
                .load(url)
                .placeholder(R.drawable.ic_launcher_foreground)
                .error(R.drawable.ic_launcher_foreground)
                .into(avatar)
        } else {
            avatar.setImageResource(
                R.drawable.ic_launcher_foreground
            )
        }
    }

    // Отображает роли пользователя.
    // В Firestore роли хранятся в строке через символ |.
    private fun renderRoles(rolesString: String) {
        val container = view.findViewById<LinearLayout>(
            R.id.profileRolesContainer
        )

        container.removeAllViews()

        rolesString.split('|')
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach { role ->

                val roleView = TextView(view.context).apply {
                    text = role
                    textSize = 12f
                    setTextColor(white)
                    setPadding(32, 16, 32, 16)
                    setBackgroundResource(
                        R.drawable.bg_category_active
                    )
                }

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 16
                }

                container.addView(roleView, params)
            }
    }

    // Считает лайки всех публикаций текущего пользователя.
    private fun loadProfileLikes() {
        val uid = auth.currentUser?.uid ?: return

        activity.lifecycleScope.launch {
            try {
                val totalLikes = withContext(Dispatchers.IO) {
                    val posts = Tasks.await(
                        db.collection("posts")
                            .whereEqualTo("authorId", uid)
                            .get()
                    )

                    var total = 0

                    for (post in posts.documents) {
                        val likes = Tasks.await(
                            db.collection("posts")
                                .document(post.id)
                                .collection("likes")
                                .get()
                        )

                        total += likes.size()
                    }

                    total
                }

                view.findViewById<TextView>(
                    R.id.profileLikesCount
                ).text = totalLikes.toString()

            } catch (e: Exception) {
                Log.e(TAG, "Ошибка подсчёта лайков", e)
            }
        }
    }

    // Настраивает кнопки профиля.
    private fun setupProfileActions() {

        view.findViewById<View>(
            R.id.profileSettings
        ).setOnClickListener {
            showSettingsDialog()
        }

        view.findViewById<View>(
            R.id.profileAvatarContainer
        ).setOnClickListener {
            activity.openAvatarPicker { uri ->
                saveAvatar(uri)
            }
        }

        view.findViewById<View>(
            R.id.editProfileButton
        ).setOnClickListener {
            showEditDialog()
        }

        view.findViewById<TextView>(
            R.id.profileEmail
        ).text = auth.currentUser?.email ?: "Аккаунт не найден"

        view.findViewById<View>(
            R.id.logoutButton
        ).setOnClickListener {
            auth.signOut()

            activity.startActivity(
                Intent(activity, LoginActivity::class.java)
            )

            activity.finish()
        }
    }

    // Настраивает переключение вкладок публикаций и сохранённого.

    private fun setupTabs() {
        val postsContainer = view.findViewById<View>(
            R.id.profilePostsContainer
        )
        val postsEmpty = view.findViewById<View>(
            R.id.profileEmptyContainer
        )
        val savedSection = view.findViewById<View>(
            R.id.profileSavedSection
        )

        val postsTab = view.findViewById<TextView>(
            R.id.profilePostsTab
        )
        val savedTab = view.findViewById<TextView>(
            R.id.profileSavedTab
        )

        var savedLoaded = false

        fun selectTab(showPosts: Boolean) {
            postsTab.setTextColor(
                if (showPosts) purple else gray
            )
            savedTab.setTextColor(
                if (showPosts) gray else purple
            )

            postsTab.setTypeface(
                null,
                if (showPosts) Typeface.BOLD else Typeface.NORMAL
            )
            savedTab.setTypeface(
                null,
                if (showPosts) Typeface.NORMAL else Typeface.BOLD
            )

            postsContainer.visibility =
                if (showPosts) View.VISIBLE else View.GONE

            postsEmpty?.visibility =
                if (showPosts &&
                    (postsContainer as? LinearLayout)?.childCount == 0
                ) View.VISIBLE else View.GONE

            savedSection.visibility =
                if (showPosts) View.GONE else View.VISIBLE

            if (!showPosts && !savedLoaded) {
                savedLoaded = true
                postsManager.loadSavedPosts(view)
            }
        }

        postsTab.setOnClickListener {
            selectTab(true)
        }

        savedTab.setOnClickListener {
            selectTab(false)
        }

        selectTab(true)
    }


    // Загружает изображение в Supabase и возвращает публичный URL.
    private suspend fun uploadAvatarToSupabase(
        uri: Uri
    ): String = withContext(Dispatchers.IO) {

        val user = auth.currentUser
            ?: error("Пользователь не авторизован")

        val token = Tasks.await(
            user.getIdToken(false)
        ).token ?: error("Не удалось получить токен")

        val resolver = view.context.contentResolver

        val mimeType = resolver.getType(uri)
            ?: error("Не удалось определить формат изображения")

        val extension = when (mimeType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"

            else -> throw IllegalArgumentException(
                "Поддерживаются только JPG, PNG и WebP"
            )
        }

        val inputStream = resolver.openInputStream(uri)
            ?: error("Не удалось открыть изображение")

        val fileBytes = inputStream.use {
            it.readBytes()
        }

        require(fileBytes.size <= MAX_AVATAR_SIZE) {
            "Размер изображения не должен превышать 5 МБ"
        }

        val boundary = "----GamerHub${UUID.randomUUID()}"

        val connection = URL(SUPABASE_FUNCTION_URL)
            .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.doOutput = true

            connection.setRequestProperty(
                "Authorization",
                "Bearer $token"
            )

            connection.setRequestProperty(
                "Content-Type",
                "multipart/form-data; boundary=$boundary"
            )

            connection.outputStream.use { output ->

                writeText(
                    output,
                    "--$boundary\r\n"
                )

                writeText(
                    output,
                    "Content-Disposition: form-data; " +
                            "name=\"file\"; filename=\"avatar.$extension\"\r\n"
                )

                writeText(
                    output,
                    "Content-Type: $mimeType\r\n\r\n"
                )

                output.write(fileBytes)

                writeText(
                    output,
                    "\r\n--$boundary--\r\n"
                )
            }

            val responseCode = connection.responseCode

            val responseStream =
                if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val response = responseStream
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            if (responseCode !in 200..299) {
                error(
                    "Ошибка загрузки ($responseCode): $response"
                )
            }

            JSONObject(response).getString("url")

        } finally {
            connection.disconnect()
        }
    }

    // Записывает текст в поток загрузки.
    private fun writeText(
        output: OutputStream,
        text: String
    ) {
        output.write(
            text.toByteArray(Charsets.UTF_8)
        )
    }

    // Сохраняет аватар в Supabase и записывает URL в Firestore.
    private fun saveAvatar(uri: Uri) {
        val user = auth.currentUser

        if (user == null) {
            showToast("Сначала войди в аккаунт")
            return
        }

        activity.lifecycleScope.launch {
            try {
                val avatarUrl = uploadAvatarToSupabase(uri)

                withContext(Dispatchers.IO) {
                    val batch = db.batch()

                    val avatarData = mapOf(
                        "avatarUrl" to avatarUrl,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )

                    batch.set(
                        db.collection("users")
                            .document(user.uid),
                        avatarData,
                        SetOptions.merge()
                    )

                    batch.set(
                        db.collection("publicProfiles")
                            .document(user.uid),
                        mapOf("avatarUrl" to avatarUrl),
                        SetOptions.merge()
                    )

                    Tasks.await(batch.commit())
                }

                showAvatar(avatarUrl)
                showToast("Аватар обновлён")

            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки аватара", e)

                showToast(
                    "Не удалось загрузить аватар: ${e.localizedMessage}"
                )
            }
        }
    }

    // Показывает диалог настроек.
    private fun showSettingsDialog() {
        val options = arrayOf(
            "Редактировать профиль",
            "Изменить аватар",
            "Информация о приложении"
        )

        val dialog = AlertDialog.Builder(view.context)
            .setTitle("Настройки GamerHub")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditDialog()

                    1 -> activity.openAvatarPicker { uri ->
                        saveAvatar(uri)
                    }

                    2 -> showAboutDialog()
                }
            }
            .setNegativeButton("Закрыть", null)
            .create()

        dialog.setOnShowListener {
            styleDialog(dialog)

            dialog.window?.setBackgroundDrawable(
                ColorDrawable(dark)
            )

            for (i in 0 until dialog.listView.childCount) {
                (dialog.listView.getChildAt(i) as? TextView)
                    ?.setTextColor(white)
            }
        }

        dialog.show()
    }

    // Показывает информацию о приложении.
    private fun showAboutDialog() {
        val dialog = AlertDialog.Builder(view.context)
            .setTitle("О GamerHub")
            .setMessage(
                "GamerHub — место для общения игроков, " +
                        "обсуждения игр и обмена игровым опытом."
            )
            .setPositiveButton("Понятно", null)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(
                ColorDrawable(dark)
            )

            dialog.findViewById<TextView>(
                android.R.id.message
            )?.setTextColor(lightGray)

            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            )?.setTextColor(purple)

            val titleId = view.resources.getIdentifier(
                "alertTitle",
                "id",
                "android"
            )

            dialog.findViewById<TextView>(
                titleId
            )?.setTextColor(white)
        }

        dialog.show()
    }

    // Применяет оформление к диалогам.
    private fun styleDialog(dialog: AlertDialog) {
        val titleId = view.resources.getIdentifier(
            "alertTitle",
            "id",
            "android"
        )

        if (titleId != 0) {
            dialog.findViewById<TextView>(
                titleId
            )?.setTextColor(white)
        }

        val background = GradientDrawable().apply {
            setColor(dark)
            cornerRadius =
                20 * view.resources.displayMetrics.density
        }

        dialog.window?.setBackgroundDrawable(background)

        dialog.findViewById<TextView>(
            android.R.id.message
        )?.setTextColor(lightGray)

        dialog.listView?.let { list ->
            list.setBackgroundColor(dark)

            list.divider = ColorDrawable(
                android.graphics.Color.rgb(48, 48, 57)
            )

            list.dividerHeight = 1

            for (i in 0 until list.childCount) {
                (list.getChildAt(i) as? TextView)
                    ?.setTextColor(white)
            }
        }

        dialog.getButton(
            AlertDialog.BUTTON_POSITIVE
        )?.setTextColor(purple)

        dialog.getButton(
            AlertDialog.BUTTON_NEGATIVE
        )?.setTextColor(purple)

        dialog.getButton(
            AlertDialog.BUTTON_NEUTRAL
        )?.setTextColor(purple)
    }

    // Открывает окно редактирования профиля.
    private fun showEditDialog() {
        val usernameView = view.findViewById<TextView>(
            R.id.profileUsername
        )

        val tagView = view.findViewById<TextView>(
            R.id.profileTag
        )

        val bioView = view.findViewById<TextView>(
            R.id.profileBio
        )

        val fields = LinearLayout(view.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            setBackgroundColor(dark)
        }

        val usernameInput = createEditText(
            "Имя пользователя",
            usernameView.text.toString()
        )

        val tagInput = createEditText(
            "@username",
            tagView.text.toString()
        )

        val bioInput = createEditText(
            "О себе",
            bioView.text.toString()
        ).apply {
            minLines = 3
            gravity = Gravity.TOP
        }

        fields.addView(usernameInput)
        fields.addView(tagInput)
        fields.addView(bioInput)

        fields.addView(
            TextView(view.context).apply {
                text = "Мои роли"
                textSize = 16f
                setTextColor(white)
                setPadding(0, 24, 0, 8)
            }
        )

        val rolesContainer = view.findViewById<LinearLayout>(
            R.id.profileRolesContainer
        )

        val currentRoles = (0 until rolesContainer.childCount)
            .map { rolesContainer.getChildAt(it) }
            .map { (it as TextView).text.toString() }
            .toSet()

        val roleCheckboxes = mutableMapOf<String, CheckBox>()

        availableRoles.forEach { role ->
            val checkBox = CheckBox(view.context).apply {
                text = role
                setTextColor(white)
                buttonTintList = ColorStateList.valueOf(purple)
                isChecked = role in currentRoles
            }

            roleCheckboxes[role] = checkBox
            fields.addView(checkBox)
        }

        val dialog = AlertDialog.Builder(view.context)
            .setTitle("Редактировать профиль")
            .setView(fields)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить", null)
            .create()

        dialog.setOnShowListener {
            styleDialog(dialog)

            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                val username = usernameInput.text
                    .toString()
                    .trim()

                val tag = tagInput.text
                    .toString()
                    .trim()

                val bio = bioInput.text
                    .toString()
                    .trim()

                if (username.isBlank() || tag.isBlank()) {
                    showToast(
                        "Имя и тег не должны быть пустыми"
                    )
                    return@setOnClickListener
                }

                val normalizedTag =
                    if (tag.startsWith("@")) tag else "@$tag"

                val selectedRoles = roleCheckboxes
                    .filterValues { it.isChecked }
                    .keys
                    .joinToString("|")

                if (selectedRoles.isBlank()) {
                    showToast("Выбери хотя бы одну роль")
                    return@setOnClickListener
                }

                saveProfileChanges(
                    username = username,
                    tag = normalizedTag,
                    bio = bio,
                    roles = selectedRoles,
                    onSuccess = {
                        usernameView.text = username
                        tagView.text = normalizedTag
                        bioView.text = bio

                        renderRoles(selectedRoles)

                        showToast("Профиль сохранён")
                        dialog.dismiss()
                    }
                )
            }
        }

        dialog.show()
    }

    // Создаёт текстовое поле для редактирования.
    private fun createEditText(
        hintText: String,
        value: String
    ): EditText {
        return EditText(view.context).apply {
            hint = hintText
            setText(value)
            setTextColor(white)
            setHintTextColor(lightGray)

            backgroundTintList =
                ColorStateList.valueOf(purple)
        }
    }

    // Сохраняет изменения профиля в users и publicProfiles.
    private fun saveProfileChanges(
        username: String,
        tag: String,
        bio: String,
        roles: String,
        onSuccess: () -> Unit
    ) {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            showToast("Не удалось определить аккаунт")
            return
        }

        activity.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val batch = db.batch()

                    val privateProfile = hashMapOf(
                        "uid" to uid,
                        "username" to username,
                        "tag" to tag,
                        "bio" to bio,
                        "roles" to roles,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )

                    val publicProfile = hashMapOf(
                        "uid" to uid,
                        "username" to username,
                        "tag" to tag
                    )

                    batch.set(
                        db.collection("users")
                            .document(uid),
                        privateProfile,
                        SetOptions.merge()
                    )

                    batch.set(
                        db.collection("publicProfiles")
                            .document(uid),
                        publicProfile,
                        SetOptions.merge()
                    )

                    Tasks.await(batch.commit())
                }

                onSuccess()

            } catch (e: Exception) {
                Log.e(TAG, "Ошибка сохранения профиля", e)

                showToast(
                    "Ошибка: ${e.message ?: e.javaClass.simpleName}"
                )
            }
        }
    }

    // Показывает сообщение пользователю.
    private fun showToast(message: String) {
        Toast.makeText(
            view.context,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    private companion object {
        const val TAG = "GamerHubProfile"

        const val MAX_AVATAR_SIZE =
            5 * 1024 * 1024

        const val SUPABASE_FUNCTION_URL =
            "https://rewxoagdwwstvuuiqtqc.supabase.co/functions/v1/funk"
    }
}