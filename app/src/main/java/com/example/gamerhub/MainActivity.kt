
package com.example.gamerhub

import androidx.lifecycle.lifecycleScope

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var notificationsManager: NotificationsManager

    private lateinit var pageContainer: FrameLayout
    private lateinit var homeManager: HomeManager

    private lateinit var postsManager: PostsManager

    // Менеджер мессенджера
    private lateinit var chatsManager: ChatsManager

    // Менеджер создания публикаций
    private lateinit var createPostManager: CreatePostManager

    // Обработчик выбора изображения для профиля
    private var avatarPickCallback: ((Uri) -> Unit)? = null

    private val avatarPicker =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )

                    avatarPickCallback?.invoke(uri)
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        "Не удалось открыть фотографию",
                        Toast.LENGTH_SHORT
                    ).show()
                } finally {
                    avatarPickCallback = null
                }
            }
        }

    // Эту функцию вызывает ProfileManager
    fun openAvatarPicker(onPicked: (Uri) -> Unit) {
        avatarPickCallback = onPicked
        avatarPicker.launch(arrayOf("image/*"))
    }

    // ============================================================
    // DATABASE
    // ============================================================

    companion object {
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS user_profiles_new (
                        uid TEXT NOT NULL,
                        username TEXT NOT NULL,
                        tag TEXT NOT NULL,
                        bio TEXT NOT NULL,
                        PRIMARY KEY(uid)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT OR IGNORE INTO user_profiles_new
                        (uid, username, tag, bio)
                    SELECT '', username, tag, bio
                    FROM user_profiles
                    WHERE id = 1
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE user_profiles")
                db.execSQL(
                    "ALTER TABLE user_profiles_new RENAME TO user_profiles"
                )
            }
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE user_profiles ADD COLUMN roles TEXT NOT NULL DEFAULT 'Геймер'"
            )
            db.execSQL(
                "ALTER TABLE user_profiles ADD COLUMN avatarUri TEXT NOT NULL DEFAULT ''"
            )
        }
    }

    private val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            GamerHubDatabase::class.java,
            "gamerhub_database"
        )
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
            .fallbackToDestructiveMigration()
            .build()
    }

    private val repository by lazy {
        GameRepository(database.gameDao())
    }

    // ============================================================
    // ON CREATE
    // ============================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Проверяем авторизацию
        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        notificationsManager = NotificationsManager()

        pageContainer = findViewById(R.id.pageContainer)

        postsManager = PostsManager(this)
        postsManager.setOnPostClickListener { post ->
            openPostDetails(post)
        }
        postsManager.setOnAuthorClickListener { authorId ->
            if (authorId.isNotBlank()) {
                openPublicProfile(authorId)
            }
        }

        // Создаём менеджеры после инициализации контейнера

        chatsManager = ChatsManager(
            activity = this,
            openPage = { layoutId ->
                openPage(layoutId)
            },
            openPublicProfile = { userId, onBack ->
                this@MainActivity.openPublicProfile(userId, onBack)
            }
        )


        createPostManager = CreatePostManager(
            this
        )
        homeManager = HomeManager(
            activity = this,
            repository = repository,
            postsManager = postsManager,
            onGameClick = { game ->
                openGameDetails(game)
            },
            onNotificationsClick = {
                openPage(R.layout.screen_notifications)
            }
        )

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
        ) { view, insets ->

            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            val ime = insets.getInsets(
                WindowInsetsCompat.Type.ime()
            )

            val bottomInset = maxOf(
                systemBars.bottom,
                ime.bottom
            )

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                bottomInset
            )

            insets
        }


        setupBottomNavigation()

// Автоматически добавляем недостающие игры в Firebase.
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.loadInitialGames()
                }
            } catch (e: Exception) {
                android.util.Log.e(
                    "MainActivity",
                    "Ошибка заполнения каталога игр",
                    e
                )
            }
        }

        openHomePage()
        setActiveNav(findViewById(R.id.navHome))
    }

    // ============================================================
    // BOTTOM NAVIGATION
    // ============================================================

    private fun setupBottomNavigation() {
        val navHome = findViewById<LinearLayout>(R.id.navHome)
        val navSearch = findViewById<LinearLayout>(R.id.navSearch)
        val navCreate = findViewById<LinearLayout>(R.id.navCreate)
        val navChats = findViewById<LinearLayout>(R.id.navChats)
        val navProfile = findViewById<LinearLayout>(R.id.navProfile)

        navHome.setOnClickListener {
            openHomePage()
            setActiveNav(navHome)
        }

        navSearch.setOnClickListener {
            openSearchPage()
            setActiveNav(navSearch)
        }

        navCreate.setOnClickListener {
            openPage(R.layout.screen_create)
            setActiveNav(navCreate)
        }

        navChats.setOnClickListener {
            openPage(R.layout.screen_chats)
            setActiveNav(navChats)
        }



        navProfile.setOnClickListener {
            openPage(R.layout.screen_profile)

            val profileView = pageContainer.getChildAt(0)

            val userId = FirebaseAuth.getInstance().currentUser?.uid

            ProfileManager(profileView, postsManager).setup()

            if (userId != null) {
                postsManager.loadUserPosts(profileView, userId)
            }

            setActiveNav(navProfile)
        }


    }

    // ============================================================
    // OPEN PAGE
    // ============================================================

    private fun openPage(layoutId: Int) {
        pageContainer.removeAllViews()

        val view = LayoutInflater.from(this).inflate(
            layoutId,
            pageContainer,
            false
        )

        pageContainer.addView(view)

        // Настройка экранов мессенджера
        chatsManager.setupPage(layoutId, view)

        // Настройка экрана создания публикации
        if (layoutId == R.layout.screen_create) {
            createPostManager.setup(view)
        }

        if (layoutId == R.layout.screen_notifications) {
            notificationsManager.setup(view)
        }
    }

    // ============================================================
    // HOME
    // ============================================================

    private fun setupHomeCategories(view: View) {
        val categories = mapOf(
            R.id.category_all to "Все",
            R.id.category_games to "Игры",
            R.id.category_guides to "Гайды",
            R.id.category_news to "Новости",
            R.id.category_lore to "Лор"
        )

        fun selectCategory(selectedId: Int) {
            categories.keys.forEach { categoryId ->
                val category = view.findViewById<TextView>(categoryId)
                    ?: return@forEach

                val isSelected = categoryId == selectedId

                category.setBackgroundResource(
                    if (isSelected) {
                        R.drawable.bg_category_active
                    } else {
                        R.drawable.bg_category
                    }
                )

                category.setTextColor(
                    if (isSelected) {
                        Color.WHITE
                    } else {
                        Color.rgb(170, 170, 170)
                    }
                )
            }

            val selectedCategory = categories[selectedId] ?: return

            when (selectedCategory) {
                "Все", "Игры" -> {
                    view.findViewById<RecyclerView>(
                        R.id.gamesRecyclerView
                    )?.visibility = View.VISIBLE

                    loadGames()
                }

                "Гайды", "Новости", "Лор" -> {
                    Toast.makeText(
                        this,
                        "Категория «$selectedCategory». " +
                                "Подключим публикации следующим этапом.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        categories.keys.forEach { categoryId ->
            view.findViewById<TextView>(categoryId)
                ?.setOnClickListener {
                    selectCategory(categoryId)
                }
        }

        selectCategory(R.id.category_all)
    }

    private fun openPostDetails(post: Post) {
        openPage(R.layout.screen_post_details)

        val view = pageContainer.getChildAt(0)

        PostDetailsManager(
            activity = this,
            post = post,
            onBack = {
                openHomePage()
                setActiveNav(findViewById(R.id.navHome))
            },
            onAuthorClick = { authorId ->
                if (authorId.isNotBlank()) {
                    openPublicProfile(authorId) {
                        openPostDetails(post)
                    }
                } else {
                    Toast.makeText(
                        this,
                        "Профиль автора недоступен",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        ).setup(view)
    }

    fun openPublicProfile(
        userId: String,
        onBack: () -> Unit = {
            openHomePage()
            setActiveNav(findViewById(R.id.navHome))
        }
    ) {
        openPage(R.layout.screen_public_profile)

        val view = pageContainer.getChildAt(0)

        PublicProfileManager(
            activity = this,
            userId = userId,
            postsManager = postsManager,
            onBack = onBack
        ).setup(view)
    }



    private fun openHomePage() {
        openPage(R.layout.screen_home)

        val view = pageContainer.getChildAt(0)

        homeManager.setup(view)
    }


    private fun setupHomeLogo(view: View) {
        val title = view.findViewById<TextView>(R.id.logoText)

        val text = "GamerHub"
        val spannable = SpannableString(text)

        spannable.setSpan(
            ForegroundColorSpan(Color.WHITE),
            0,
            5,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        spannable.setSpan(
            ForegroundColorSpan(Color.rgb(170, 80, 255)),
            5,
            text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        title.text = spannable

        title.setOnClickListener {
            startActivity(
                Intent(this, RegisterActivity::class.java)
            )
        }
    }

    // ============================================================
    // LOAD GAMES
    // ============================================================

    private fun loadGames() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val games = repository.getGames()

                withContext(Dispatchers.Main) {
                    showHomeGames(games)
                }
            } catch (e: Exception) {
                android.util.Log.e(
                    "GamerHub",
                    "Ошибка загрузки каталога из Firebase",
                    e
                )

                val localGames = try {
                    repository.getLocalGames()
                } catch (localError: Exception) {
                    android.util.Log.e(
                        "GamerHub",
                        "Ошибка загрузки каталога из Room",
                        localError
                    )
                    emptyList()
                }

                withContext(Dispatchers.Main) {
                    showHomeGames(localGames)

                    if (localGames.isEmpty()) {
                        Toast.makeText(
                            this@MainActivity,
                            "Не удалось загрузить каталог игр",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    // ============================================================
    // HOME GAMES
    // ============================================================

    private fun showHomeGames(games: List<Game>) {
        val recyclerView = pageContainer.findViewById<RecyclerView>(
            R.id.gamesRecyclerView
        ) ?: return

        recyclerView.layoutManager = GridLayoutManager(this, 2)

        recyclerView.adapter = GameAdapter(
            games,
            repository,
            this
        ) { game ->
            openGameDetails(game)
        }
    }

    // ============================================================
    // SEARCH
    // ============================================================

    private fun openSearchPage() {
        openPage(R.layout.screen_search)

        val view = pageContainer.getChildAt(0)

        SearchManager(
            this,
            repository,
            pageContainer
        ) { game ->
            openGameDetails(game)
        }.setup(view)
    }

    // ============================================================
    // GAME DETAILS
    // ============================================================

    private fun openGameDetails(game: Game) {
        openPage(R.layout.screen_game_details)

        val view = pageContainer.getChildAt(0)

        val backButton = view.findViewById<View>(
            R.id.backButton
        )

        backButton.setOnClickListener {
            openHomePage()
            setActiveNav(findViewById(R.id.navHome))
        }

        val image = view.findViewById<ImageView>(
            R.id.gameDetailsImage
        )

        if (game.image.startsWith("http")) {
            Glide.with(this)
                .load(game.image)
                .into(image)
        } else {
            val imageId = resources.getIdentifier(
                game.image,
                "drawable",
                packageName
            )

            if (imageId != 0) {
                image.setImageResource(imageId)
            }
        }

        view.findViewById<TextView>(
            R.id.gameDetailsTitle
        ).text = game.title

        view.findViewById<TextView>(
            R.id.gameDetailsGenre
        ).text = game.genre

        view.findViewById<TextView>(
            R.id.gameDetailsDescription
        ).text = game.description

        postsManager.loadPostsForGame(
            view = view,
            gameTitle = game.title
        )
    }

    // ============================================================
    // BOTTOM NAVIGATION ACTIVE STATE
    // ============================================================

    private fun setActiveNav(selected: LinearLayout) {
        val navigation = findViewById<LinearLayout>(
            R.id.navigation
        )

        for (i in 0 until navigation.childCount) {
            val item = navigation.getChildAt(i)

            if (item !is LinearLayout) {
                continue
            }

            val image = item.getChildAt(0)
            val text = item.getChildAt(1)

            val color = if (item == selected) {
                Color.rgb(180, 90, 255)
            } else {
                Color.rgb(150, 150, 150)
            }

            if (image is ImageView) {
                image.setColorFilter(color)
            }

            if (text is TextView) {
                text.setTextColor(color)
            }
        }
    }
}
