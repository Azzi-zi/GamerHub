
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
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
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
        setupFollowCounts()
    }

    // Настройка счётчиков подписчиков и подписок.
    private fun setupFollowCounts() {
        val userId = auth.currentUser?.uid ?: return

        val followersCount = view.findViewById<TextView>(
            R.id.profileFriendsCount
        )

        val followingCount = view.findViewById<TextView>(
            R.id.profileFollowingCount
        )

        followersCount.setOnClickListener {
            FollowListManager(activity) { targetUid ->
                activity.openPublicProfile(targetUid)
            }.showFollowers(userId)
        }

        followingCount.setOnClickListener {
            FollowListManager(activity) { targetUid ->
                activity.openPublicProfile(targetUid)
            }.showFollowing(userId)
        }

        db.collection("users")
            .document(userId)
            .collection("followers")
            .get()
            .addOnSuccessListener { documents ->
                followersCount.text = documents.size().toString()
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Ошибка загрузки подписчиков", error)
            }

        db.collection("users")
            .document(userId)
            .collection("following")
            .get()
            .addOnSuccessListener { documents ->
                followingCount.text = documents.size().toString()
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Ошибка загрузки подписок", error)
            }
    }

    // Загружает профиль из Firestore.
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

                val favoriteGames = (
                        document.get("favoriteGames") as? List<*>
                        ).orEmpty().filterIsInstance<String>()

                renderFavoriteGames(favoriteGames)

                val avatarUrl = document.getString("avatarUrl")
                    ?: document.getString("avatarUri")
                    ?: ""

                showAvatar(avatarUrl)

                val coverUrl = document.getString("coverUrl").orEmpty()
                showCover(coverUrl)

            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки профиля", e)
                showToast(
                    "Не удалось загрузить профиль: " +
                            (e.localizedMessage ?: "неизвестная ошибка")
                )
            }
        }
    }

    // Отображает аватар пользователя.
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

    // Отображает обложку профиля.
    private fun showCover(url: String) {
        val cover = view.findViewById<ImageView>(
            R.id.profileCover
        )

        if (url.startsWith("https://")) {
            Glide.with(cover)
                .load(url)
                .placeholder(ColorDrawable(dark))
                .error(ColorDrawable(dark))
                .centerCrop()
                .into(cover)
        } else {
            cover.setImageDrawable(ColorDrawable(dark))
        }
    }

    // Отображает игровые роли пользователя.
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

                    val density = resources.displayMetrics.density
                    setPadding(
                        (12 * density).toInt(),
                        (6 * density).toInt(),
                        (12 * density).toInt(),
                        (6 * density).toInt()
                    )

                    setBackgroundResource(
                        R.drawable.bg_category_active
                    )
                }

                val density = view.resources.displayMetrics.density

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = (8 * density).toInt()
                }

                container.addView(roleView, params)
            }
    }

    // Отображает любимые игры без значка геймпада.
    private fun renderFavoriteGames(games: List<String>) {
        val container = view.findViewById<LinearLayout>(
            R.id.profileFavoriteGames
        ) ?: return

        container.removeAllViews()

        val density = view.resources.displayMetrics.density

        if (games.isEmpty()) {
            container.addView(
                TextView(view.context).apply {
                    text = "Пока не добавлено ни одной игры"
                    textSize = 13f
                    setTextColor(lightGray)
                    setPadding(0, 4, 0, 4)
                }
            )
            return
        }

        games.forEach { game ->
            val gameView = TextView(view.context).apply {
                text = game
                textSize = 13f
                setTextColor(white)

                setPadding(
                    (12 * density).toInt(),
                    (9 * density).toInt(),
                    (12 * density).toInt(),
                    (9 * density).toInt()
                )

                background = GradientDrawable().apply {
                    cornerRadius = 12 * density
                    setColor(dark)
                    setStroke(
                        (1 * density).toInt(),
                        android.graphics.Color.rgb(55, 52, 72)
                    )
                }
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = (8 * density).toInt()
            }

            container.addView(gameView, params)
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
            R.id.changeCoverButton
        ).setOnClickListener {
            activity.openAvatarPicker { uri ->
                saveCover(uri)
            }
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
    }

    // Настройка переключения вкладок публикаций и сохранённого.
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

            val postsLayout = postsContainer as? LinearLayout

            postsEmpty.visibility =
                if (showPosts && (postsLayout?.childCount ?: 0) == 0) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

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

        require(fileBytes.size <= MAX_IMAGE_SIZE) {
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
                writeText(output, "--$boundary\r\n")

                writeText(
                    output,
                    "Content-Disposition: form-data; " +
                            "name=\"file\"; filename=\"image.$extension\"\r\n"
                )

                writeText(
                    output,
                    "Content-Type: $mimeType\r\n\r\n"
                )

                output.write(fileBytes)
                writeText(output, "\r\n--$boundary--\r\n")
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
                error("Ошибка загрузки ($responseCode): $response")
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
        output.write(text.toByteArray(Charsets.UTF_8))
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
                showToast("Загружаем аватар...")

                val avatarUrl = uploadAvatarToSupabase(uri)

                withContext(Dispatchers.IO) {
                    val batch = db.batch()

                    batch.set(
                        db.collection("users").document(user.uid),
                        mapOf(
                            "avatarUrl" to avatarUrl,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )

                    batch.set(
                        db.collection("publicProfiles").document(user.uid),
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
                    "Не удалось загрузить аватар: " +
                            (e.localizedMessage ?: "неизвестная ошибка")
                )
            }
        }
    }

    // Сохраняет обложку отдельно от аватара.
    private fun saveCover(uri: Uri) {
        val user = auth.currentUser

        if (user == null) {
            showToast("Сначала войди в аккаунт")
            return
        }

        activity.lifecycleScope.launch {
            try {
                showToast("Загружаем обложку...")

                val coverUrl = uploadAvatarToSupabase(uri)

                withContext(Dispatchers.IO) {
                    val batch = db.batch()

                    batch.set(
                        db.collection("users").document(user.uid),
                        mapOf(
                            "coverUrl" to coverUrl,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )

                    batch.set(
                        db.collection("publicProfiles").document(user.uid),
                        mapOf("coverUrl" to coverUrl),
                        SetOptions.merge()
                    )

                    Tasks.await(batch.commit())
                }

                showCover(coverUrl)
                showToast("Обложка обновлена")

            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки обложки", e)

                showToast(
                    "Не удалось установить обложку: " +
                            (e.localizedMessage ?: "неизвестная ошибка")
                )
            }
        }
    }

    // Подтверждение выхода из аккаунта.
    private fun confirmLogout() {
        val dialog = AlertDialog.Builder(view.context)
            .setTitle("Выход из аккаунта")
            .setMessage("Ты действительно хочешь выйти?")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Выйти") { _, _ ->
                auth.signOut()

                activity.startActivity(
                    Intent(activity, LoginActivity::class.java)
                )

                activity.finish()
            }
            .create()

        dialog.setOnShowListener {
            styleDialog(dialog)
        }

        dialog.show()
    }

    // Настройки профиля.
    private fun showSettingsDialog() {
        val options = arrayOf(
            "Редактировать профиль",
            "Изменить аватар",
            "Изменить обложку",
            "Информация о приложении",
            "Выйти из аккаунта"
        )

        val dialog = AlertDialog.Builder(view.context)
            .setTitle("Настройки GamerHub")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditDialog()

                    1 -> activity.openAvatarPicker { uri ->
                        saveAvatar(uri)
                    }

                    2 -> activity.openAvatarPicker { uri ->
                        saveCover(uri)
                    }

                    3 -> showAboutDialog()

                    4 -> confirmLogout()
                }
            }
            .setNegativeButton("Закрыть", null)
            .create()

        dialog.setOnShowListener {
            styleDialog(dialog)
            dialog.window?.setBackgroundDrawable(ColorDrawable(dark))
        }

        dialog.show()
    }

    // Информация о приложении.
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
            styleDialog(dialog)

            dialog.findViewById<TextView>(
                android.R.id.message
            )?.setTextColor(lightGray)
        }

        dialog.show()
    }

    // Оформление диалоговых окон.
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

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(purple)

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            ?.setTextColor(purple)

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            ?.setTextColor(purple)
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

        val user = auth.currentUser

        if (user == null) {
            showToast("Не удалось определить аккаунт")
            return
        }

        activity.lifecycleScope.launch {
            try {
                // Получаем профиль и каталог игр из Firestore.
                val data = withContext(Dispatchers.IO) {
                    val profile = Tasks.await(
                        db.collection("users")
                            .document(user.uid)
                            .get()
                    )

                    val gamesSnapshot = Tasks.await(
                        db.collection("games").get()
                    )

                    val games = gamesSnapshot.documents
                        .mapNotNull { it.getString("title") }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()

                    val favorites = (
                            profile.get("favoriteGames") as? List<*>
                            ).orEmpty()
                        .filterIsInstance<String>()
                        .toSet()

                    Triple(profile, games, favorites)
                }

                val gameTitles = data.second
                val selectedFavoriteGames = data.third.toMutableSet()

                // Прокручиваемое содержимое диалога.
                val scrollView = android.widget.ScrollView(view.context)

                val fields = LinearLayout(view.context).apply {
                    orientation = LinearLayout.VERTICAL
                    val density = resources.displayMetrics.density
                    setPadding(
                        (20 * density).toInt(),
                        (8 * density).toInt(),
                        (20 * density).toInt(),
                        (12 * density).toInt()
                    )
                    setBackgroundColor(dark)
                }

                scrollView.addView(
                    fields,
                    android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )

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

                // Роли пользователя.
                fields.addView(
                    createSectionTitle("Мои роли")
                )

                val rolesContainer = view.findViewById<LinearLayout>(
                    R.id.profileRolesContainer
                )

                val currentRoles = (0 until rolesContainer.childCount)
                    .map { rolesContainer.getChildAt(it) }
                    .filterIsInstance<TextView>()
                    .map { it.text.toString() }
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

                // Любимые игры.
                fields.addView(
                    createSectionTitle("Любимые игры")
                )

                val selectedGamesContainer = LinearLayout(view.context).apply {
                    orientation = LinearLayout.VERTICAL
                }

                val gameSpinner = Spinner(view.context)

                val gameAdapter = ArrayAdapter(
                    activity,
                    android.R.layout.simple_spinner_item,
                    gameTitles
                ).apply {
                    setDropDownViewResource(
                        android.R.layout.simple_spinner_dropdown_item
                    )
                }

                gameSpinner.adapter = gameAdapter

                val addGameButton = TextView(view.context).apply {
                    text = "+ Добавить выбранную игру"
                    textSize = 14f
                    setTextColor(purple)
                    setPadding(0, 12, 0, 12)
                    gravity = Gravity.CENTER_VERTICAL
                    isClickable = true
                    isFocusable = true
                }

                fun renderSelectedGames() {
                    selectedGamesContainer.removeAllViews()

                    if (selectedFavoriteGames.isEmpty()) {
                        selectedGamesContainer.addView(
                            TextView(view.context).apply {
                                text = "Игры пока не выбраны"
                                textSize = 13f
                                setTextColor(lightGray)
                                setPadding(0, 8, 0, 8)
                            }
                        )
                        return
                    }

                    selectedFavoriteGames.toList().forEach { game ->
                        val gameRow = LinearLayout(view.context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(0, 4, 0, 4)
                        }

                        val gameName = TextView(view.context).apply {
                            text = game
                            textSize = 14f
                            setTextColor(white)
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            )
                        }

                        val removeButton = TextView(view.context).apply {
                            text = "✕"
                            textSize = 16f
                            setTextColor(purple)
                            setPadding(16, 8, 8, 8)

                            setOnClickListener {
                                selectedFavoriteGames.remove(game)
                                renderSelectedGames()
                            }
                        }

                        gameRow.addView(gameName)
                        gameRow.addView(removeButton)
                        selectedGamesContainer.addView(gameRow)
                    }
                }

                addGameButton.setOnClickListener {
                    val selectedGame = gameSpinner.selectedItem?.toString()

                    if (selectedGame.isNullOrBlank()) {
                        showToast("В каталоге нет игр")
                        return@setOnClickListener
                    }

                    if (!selectedFavoriteGames.add(selectedGame)) {
                        showToast("Эта игра уже добавлена")
                    } else {
                        renderSelectedGames()
                    }
                }

                if (gameTitles.isEmpty()) {
                    fields.addView(
                        TextView(view.context).apply {
                            text = "В каталоге games не найдено игр с полем title"
                            setTextColor(lightGray)
                            textSize = 13f
                            setPadding(0, 8, 0, 8)
                        }
                    )
                } else {
                    fields.addView(gameSpinner)
                    fields.addView(addGameButton)
                }

                fields.addView(selectedGamesContainer)
                renderSelectedGames()

                val dialog = AlertDialog.Builder(view.context)
                    .setTitle("Редактировать профиль")
                    .setView(scrollView)
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Сохранить", null)
                    .create()

                dialog.setOnShowListener {
                    styleDialog(dialog)

                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener {
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
                                showToast("Имя и тег не должны быть пустыми")
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
                                favoriteGames = selectedFavoriteGames.toList(),
                                onSuccess = {
                                    usernameView.text = username
                                    tagView.text = normalizedTag
                                    bioView.text = bio

                                    renderRoles(selectedRoles)
                                    renderFavoriteGames(
                                        selectedFavoriteGames.toList()
                                    )

                                    showToast("Профиль сохранён")
                                    dialog.dismiss()
                                }
                            )
                        }
                }

                dialog.show()

            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки редактора профиля", e)

                showToast(
                    "Не удалось загрузить профиль или игры: " +
                            (e.localizedMessage ?: "неизвестная ошибка")
                )
            }
        }
    }

    // Заголовок раздела в окне редактирования.
    private fun createSectionTitle(title: String): TextView {
        return TextView(view.context).apply {
            text = title
            textSize = 16f
            setTextColor(white)

            val density = resources.displayMetrics.density
            setPadding(
                0,
                (20 * density).toInt(),
                0,
                (8 * density).toInt()
            )
        }
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
            backgroundTintList = ColorStateList.valueOf(purple)
        }
    }

    // Сохраняет профиль в users и публичные данные в publicProfiles.
    private fun saveProfileChanges(
        username: String,
        tag: String,
        bio: String,
        roles: String,
        favoriteGames: List<String>,
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

                    val privateProfile = hashMapOf<String, Any>(
                        "uid" to uid,
                        "username" to username,
                        "tag" to tag,
                        "bio" to bio,
                        "roles" to roles,
                        "favoriteGames" to favoriteGames,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )

                    val publicProfile = hashMapOf<String, Any>(
                        "uid" to uid,
                        "username" to username,
                        "tag" to tag
                    )

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

                    Tasks.await(batch.commit())
                }

                onSuccess()

            } catch (e: Exception) {
                Log.e(TAG, "Ошибка сохранения профиля", e)

                showToast(
                    "Ошибка сохранения: " +
                            (e.localizedMessage ?: e.javaClass.simpleName)
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

        const val MAX_IMAGE_SIZE = 5 * 1024 * 1024

        const val SUPABASE_FUNCTION_URL =
            "https://rewxoagdwwstvuuiqtqc.supabase.co/functions/v1/funk"
    }
}
