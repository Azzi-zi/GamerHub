package com.example.gamerhub

import android.graphics.Color
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PublicProfileManager(
    private val activity: AppCompatActivity,
    private val userId: String,
    private val postsManager: PostsManager,
    private val onBack: () -> Unit
) {

    // Подключение к Cloud Firestore.
    private val db = FirebaseFirestore.getInstance()

    // Получение информации о текущем авторизованном пользователе.
    private val auth = FirebaseAuth.getInstance()

    // Слушатели Firestore.
    // Они позволяют автоматически получать изменения данных без повторной загрузки страницы.

    // Слушатель количества подписчиков этого пользователя.
    private var followersListener: ListenerRegistration? = null

    // Слушатель количества пользователей, на которых подписан этот пользователь.
    private var followingListener: ListenerRegistration? = null

    // Слушатель, проверяющий, подписан ли текущий пользователь на открытый профиль.
    private var myFollowListener: ListenerRegistration? = null

    // Защита от повторного нажатия на кнопку подписки,
    // пока предыдущий запрос ещё выполняется.
    private var requestInProgress = false


    // =========================================================
    // НАСТРОЙКА ПУБЛИЧНОГО ПРОФИЛЯ
    // =========================================================

    fun setup(view: View) {

        // Кнопка возвращения на предыдущую страницу.
        view.findViewById<View>(
            R.id.publicProfileBackButton
        ).setOnClickListener {
            onBack()
        }

        // Загружаем информацию о пользователе.
        loadProfile(view)

        // Загружаем публикации выбранного пользователя.
        postsManager.loadUserPosts(view, userId)

        // Настраиваем подписки и их счётчики.
        setupFollow(view)
    }


    // =========================================================
    // ПОДПИСКИ И СЧЁТЧИКИ
    // =========================================================

    private fun setupFollow(view: View) {

        // UID текущего пользователя Firebase.
        val currentUid = auth.currentUser?.uid

        // Кнопка подписки на открытый профиль.
        val followButton = view.findViewById<Button>(
            R.id.publicProfileFollowButton
        )

        // Нельзя подписаться на собственный профиль.
        if (currentUid == userId) {

            // Скрываем кнопку подписки.
            followButton.visibility = View.GONE

        } else if (currentUid == null) {

            // Если пользователь не авторизован,
            // отключаем кнопку и предлагаем войти в аккаунт.
            followButton.isEnabled = false
            followButton.text = "Войди, чтобы подписаться"

        } else {

            // Обрабатываем нажатие на кнопку подписки.
            followButton.setOnClickListener {
                toggleFollow(view, currentUid)
            }

            // Следим за тем, существует ли подписка.
            // Если документ существует, значит пользователь подписан.
            myFollowListener = db.collection("users")
                .document(userId)
                .collection("followers")
                .document(currentUid)
                .addSnapshotListener { document, error ->

                    // Проверяем, не возникла ли ошибка.
                    if (error != null || document == null) {

                        Log.e(
                            "PublicProfileManager",
                            "Не удалось проверить подписку",
                            error
                        )

                        return@addSnapshotListener
                    }

                    // Проверяем наличие документа подписки.
                    val isFollowing = document.exists()

                    // Меняем надпись на кнопке.
                    followButton.text = if (isFollowing) {
                        "Отписаться"
                    } else {
                        "Подписаться"
                    }

                    // Меняем цвет кнопки в зависимости от состояния подписки.
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


        // -----------------------------------------------------
        // ПОДСЧЁТ ПОДПИСЧИКОВ
        // -----------------------------------------------------

        // Подключаем слушатель к коллекции followers.
        // При добавлении или удалении подписчика число обновится автоматически.
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

                // Отображаем количество подписчиков.
                view.findViewById<TextView>(
                    R.id.publicProfileFollowersCount
                ).text = (snapshot?.size() ?: 0).toString()
            }


        // -----------------------------------------------------
        // ПОДСЧЁТ ПОДПИСОК
        // -----------------------------------------------------

        // Следим за коллекцией following.
        // Здесь хранятся пользователи, на которых подписан владелец профиля.
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

                // Отображаем количество подписок.
                view.findViewById<TextView>(
                    R.id.publicProfileFollowingCount
                ).text = (snapshot?.size() ?: 0).toString()
            }
    }


    // =========================================================
    // ПОДПИСКА И ОТПИСКА
    // =========================================================

    private fun toggleFollow(view: View, currentUid: String) {

        // Не разрешаем отправлять несколько запросов одновременно.
        if (requestInProgress) return

        // Дополнительная проверка: нельзя подписаться на себя.
        if (currentUid == userId) return

        // Получаем кнопку подписки.
        val button = view.findViewById<Button>(
            R.id.publicProfileFollowButton
        )

        // Отмечаем, что запрос начался.
        requestInProgress = true

        // Временно отключаем кнопку.
        button.isEnabled = false


        // Запускаем корутину.
        // Она позволяет выполнять запросы к Firebase без блокировки интерфейса.
        activity.lifecycleScope.launch {

            try {

                // Документ подписчика в профиле другого пользователя.
                val followerRef = db.collection("users")
                    .document(userId)
                    .collection("followers")
                    .document(currentUid)

                // Документ подписки в профиле текущего пользователя.
                val followingRef = db.collection("users")
                    .document(currentUid)
                    .collection("following")
                    .document(userId)

                // Проверяем, существует ли уже подписка.
                val existingFollow = followerRef.get().await()


                if (existingFollow.exists()) {

                    // -------------------------------------------------
                    // ОТПИСКА
                    // -------------------------------------------------

                    // Удаляем связь сразу из двух коллекций:
                    // followers у другого пользователя;
                    // following у текущего пользователя.
                    db.runBatch { batch ->

                        batch.delete(followerRef)

                        batch.delete(followingRef)

                    }.await()

                    // Показываем сообщение об успешной отписке.
                    Toast.makeText(
                        activity,
                        "Ты отписался от пользователя",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {

                    // -------------------------------------------------
                    // ПОДПИСКА
                    // -------------------------------------------------

                    // Получаем имя текущего пользователя.
                    // Оно понадобится для уведомления.
                    val actorName = getCurrentUsername(currentUid)

                    // Создаём новый документ уведомления.
                    val notificationRef = db.collection("users")
                        .document(userId)
                        .collection("notifications")
                        .document()


                    // Создаём подписку и уведомление одним пакетом.
                    // Это позволяет выполнить все изменения вместе.
                    db.runBatch { batch ->

                        // Добавляем текущего пользователя
                        // в список подписчиков открытого профиля.
                        batch.set(
                            followerRef,
                            mapOf(
                                "uid" to currentUid,
                                "username" to actorName,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        )

                        // Добавляем открытый профиль
                        // в список подписок текущего пользователя.
                        batch.set(
                            followingRef,
                            mapOf(
                                "uid" to userId,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        )

                        // Создаём уведомление о новой подписке.
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

                    // Показываем сообщение об успешной подписке.
                    Toast.makeText(
                        activity,
                        "Ты подписался на пользователя",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {

                // Записываем ошибку в Logcat.
                Log.e(
                    "PublicProfileManager",
                    "Ошибка изменения подписки",
                    e
                )

                // Показываем сообщение об ошибке.
                Toast.makeText(
                    activity,
                    "Не удалось изменить подписку. Проверь правила Firestore.",
                    Toast.LENGTH_LONG
                ).show()

            } finally {

                // В любом случае разрешаем снова нажимать кнопку.
                requestInProgress = false

                button.isEnabled = true
            }
        }
    }


    // =========================================================
    // ПОЛУЧЕНИЕ ИМЕНИ ТЕКУЩЕГО ПОЛЬЗОВАТЕЛЯ
    // =========================================================

    private suspend fun getCurrentUsername(uid: String): String {

        // Сначала пытаемся получить имя из публичного профиля.
        val publicProfile = db.collection("publicProfiles")
            .document(uid)
            .get()
            .await()

        val publicName = publicProfile.getString("username")

        // Если имя найдено и оно не пустое, возвращаем его.
        if (!publicName.isNullOrBlank()) {
            return publicName
        }

        // Если публичного имени нет, пробуем получить его
        // из основной коллекции пользователей.
        val userProfile = db.collection("users")
            .document(uid)
            .get()
            .await()

        // Если имя всё равно отсутствует,
        // используем стандартное название.
        return userProfile.getString("username")
            ?.takeIf { it.isNotBlank() }
            ?: "Игрок GamerHub"
    }


    // =========================================================
    // ЗАГРУЗКА ИНФОРМАЦИИ О ПОЛЬЗОВАТЕЛЕ
    // =========================================================

    private fun loadProfile(view: View) {

        // Загружаем данные профиля в отдельной корутине.
        activity.lifecycleScope.launch {

            try {

                // Получаем публичный профиль пользователя из Firestore.
                val document = db.collection("publicProfiles")
                    .document(userId)
                    .get()
                    .await()

                // Если документа нет, сообщаем об этом.
                if (!document.exists()) {

                    Toast.makeText(
                        activity,
                        "Публичный профиль пользователя не найден",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@launch
                }


                // -------------------------------------------------
                // ИМЯ И ТЕГ
                // -------------------------------------------------

                // Получаем имя пользователя.
                // Если оно отсутствует, используем стандартное.
                val username = document.getString("username")
                    ?.takeIf { it.isNotBlank() }
                    ?: "Игрок GamerHub"

                // Отображаем имя.
                view.findViewById<TextView>(
                    R.id.publicProfileUsername
                ).text = username

                // Получаем тег пользователя.
                // Если тег не указан, формируем его автоматически.
                view.findViewById<TextView>(
                    R.id.publicProfileTag
                ).text = document.getString("tag")
                    ?: "@${username.lowercase().replace(" ", "_")}"


                // -------------------------------------------------
                // ОПИСАНИЕ ПРОФИЛЯ
                // -------------------------------------------------

                // Получаем описание пользователя.
                // Если оно пустое, отображаем стандартный текст.
                view.findViewById<TextView>(
                    R.id.publicProfileBio
                ).text = document.getString("bio").orEmpty()
                    .ifBlank {
                        "Пользователь пока ничего не рассказал о себе."
                    }


                // Плотность экрана.
                // Она нужна для правильного отображения отступов в пикселях.
                val density = activity.resources.displayMetrics.density


                // -------------------------------------------------
                // ИГРОВЫЕ РОЛИ
                // -------------------------------------------------

                // Контейнер, в котором будут отображаться роли.
                val rolesContainer = view.findViewById<LinearLayout>(
                    R.id.publicProfileRoles
                )

                // Удаляем старые элементы перед добавлением новых.
                rolesContainer.removeAllViews()

                // Получаем роли из Firestore.
                // В базе они хранятся в одной строке через символ |.
                val roles = document.getString("roles")
                    .orEmpty()
                    .split("|")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

                if (roles.isEmpty()) {

                    // Если ролей нет, показываем соответствующий текст.
                    rolesContainer.addView(
                        TextView(activity).apply {

                            text = "Роли не указаны"

                            textSize = 12f

                            setTextColor(Color.LTGRAY)
                        }
                    )

                } else {

                    // Создаём отдельный элемент для каждой роли.
                    roles.forEach { role ->

                        val roleView = TextView(activity).apply {

                            text = role

                            textSize = 12f

                            setTextColor(Color.WHITE)

                            // Внутренние отступы элемента.
                            setPadding(
                                (12 * density).toInt(),
                                (6 * density).toInt(),
                                (12 * density).toInt(),
                                (6 * density).toInt()
                            )

                            // Фон роли.
                            setBackgroundResource(
                                R.drawable.bg_category_active
                            )
                        }

                        // Параметры размещения роли в контейнере.
                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {

                            // Расстояние справа от элемента.
                            marginEnd = (8 * density).toInt()
                        }

                        // Добавляем роль на экран.
                        rolesContainer.addView(roleView, params)
                    }
                }


                // -------------------------------------------------
                // ЛЮБИМЫЕ ИГРЫ
                // -------------------------------------------------

                // Получаем список любимых игр из Firestore.
                val games = (
                        document.get("favoriteGames") as? List<*>
                        ).orEmpty().filterIsInstance<String>()

                // Контейнер для отображения любимых игр.
                val gamesContainer = view.findViewById<LinearLayout>(
                    R.id.profileFavoriteGames
                )

                // Удаляем предыдущие элементы.
                gamesContainer.removeAllViews()

                if (games.isEmpty()) {

                    // Если игр нет, показываем подсказку.
                    gamesContainer.addView(
                        TextView(activity).apply {

                            text = "Пока не добавлено ни одной игры"

                            textSize = 13f

                            setTextColor(Color.LTGRAY)

                            setPadding(0, 4, 0, 4)
                        }
                    )

                } else {

                    // Создаём отдельный элемент для каждой любимой игры.
                    games.forEach { game ->

                        val gameView = TextView(activity).apply {

                            text = game

                            textSize = 13f

                            setTextColor(Color.WHITE)

                            // Внутренние отступы.
                            setPadding(
                                (12 * density).toInt(),
                                (9 * density).toInt(),
                                (12 * density).toInt(),
                                (9 * density).toInt()
                            )

                            // Создаём закруглённый тёмный фон.
                            background =
                                android.graphics.drawable.GradientDrawable().apply {

                                    cornerRadius = 12 * density

                                    setColor(Color.rgb(26, 26, 32))

                                    // Тонкая рамка вокруг элемента.
                                    setStroke(
                                        (1 * density).toInt(),
                                        Color.rgb(55, 52, 72)
                                    )
                                }
                        }

                        // Настраиваем отступы между играми.
                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {

                            marginEnd = (8 * density).toInt()
                        }

                        // Добавляем игру в контейнер.
                        gamesContainer.addView(gameView, params)
                    }
                }


                // -------------------------------------------------
                // АВАТАР ПОЛЬЗОВАТЕЛЯ
                // -------------------------------------------------

                // Сначала пытаемся получить avatarUrl.
                // Если его нет, используем старое поле avatarUri.
                val avatarUrl = document.getString("avatarUrl")
                    ?: document.getString("avatarUri")
                    ?: ""

                // Получаем ImageView аватара.
                val avatar = view.findViewById<ImageView>(
                    R.id.publicProfileAvatar
                )

                if (avatarUrl.startsWith("https://")) {

                    // Если ссылка корректная, загружаем изображение через Glide.
                    Glide.with(avatar)
                        .load(avatarUrl)
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .error(R.drawable.ic_launcher_foreground)
                        .into(avatar)

                } else {

                    // Если ссылки нет, отображаем стандартную картинку.
                    avatar.setImageResource(
                        R.drawable.ic_launcher_foreground
                    )
                }


                // -------------------------------------------------
                // ОБЛОЖКА ПРОФИЛЯ
                // -------------------------------------------------

                // Получаем ImageView обложки.
                val cover = view.findViewById<ImageView>(
                    R.id.publicProfileCover
                )

                // Получаем ссылку на обложку из Firestore.
                val coverUrl = document.getString("coverUrl").orEmpty()

                if (coverUrl.startsWith("https://")) {

                    // Загружаем обложку.
                    // centerCrop обрезает изображение так,
                    // чтобы оно полностью заполняло ImageView.
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

                    // Если обложки нет, очищаем старое изображение.
                    Glide.with(cover).clear(cover)

                    // Устанавливаем обычный тёмный фон.
                    cover.setImageDrawable(
                        android.graphics.drawable.ColorDrawable(
                            Color.rgb(26, 26, 32)
                        )
                    )
                }


                // -------------------------------------------------
                // КОЛИЧЕСТВО ПУБЛИКАЦИЙ
                // -------------------------------------------------

                // Получаем все публикации, созданные этим пользователем.
                val posts = db.collection("posts")
                    .whereEqualTo("authorId", userId)
                    .get()
                    .await()

                // Отображаем количество публикаций.
                view.findViewById<TextView>(
                    R.id.profilePostsCount
                ).text = posts.size().toString()


                // -------------------------------------------------
                // ОБЩЕЕ КОЛИЧЕСТВО ЛАЙКОВ
                // -------------------------------------------------

                // Переменная для хранения суммы всех лайков.
                var totalLikes = 0

                // Перебираем все публикации пользователя.
                for (post in posts.documents) {

                    // Получаем коллекцию лайков конкретной публикации.
                    val likes = db.collection("posts")
                        .document(post.id)
                        .collection("likes")
                        .get()
                        .await()

                    // Добавляем количество лайков к общей сумме.
                    totalLikes += likes.size()
                }

                // Отображаем общее количество лайков.
                view.findViewById<TextView>(
                    R.id.publicProfileLikesCount
                ).text = totalLikes.toString()

            } catch (e: Exception) {

                // Записываем подробную информацию об ошибке.
                Log.e(
                    "PublicProfileManager",
                    "Ошибка загрузки профиля $userId",
                    e
                )

                // Сообщаем пользователю о проблеме.
                Toast.makeText(
                    activity,
                    "Не удалось загрузить профиль",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


    // =========================================================
    // ОСТАНОВКА СЛУШАТЕЛЕЙ FIRESTORE
    // =========================================================

    fun stopListening() {

        // Удаляем слушатель количества подписчиков.
        followersListener?.remove()

        // Удаляем слушатель количества подписок.
        followingListener?.remove()

        // Удаляем слушатель состояния подписки.
        myFollowListener?.remove()

        // Обнуляем ссылки на слушатели.
        // Это помогает избежать повторного использования старых слушателей.
        followersListener = null
        followingListener = null
        myFollowListener = null
    }
}