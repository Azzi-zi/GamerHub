
package com.example.gamerhub


import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Intent
import android.graphics.Color
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
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var pageContainer: FrameLayout

    // Менеджер мессенджера
    private lateinit var chatsManager: ChatsManager


    //обработчик галереи
    // Функция, которая получит выбранную фотографию
    private var avatarPickCallback: ((Uri) -> Unit)? = null

    // Открываем системный выбор изображения
    private val avatarPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->

            if (uri != null) {
                try {
                    // Разрешаем приложению читать изображение после перезапуска
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )

                    avatarPickCallback?.invoke(uri)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        this,
                        "Не удалось открыть фотографию",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } finally {
                    avatarPickCallback = null
                }
            }
        }

    // Эту функцию будет вызывать ProfileManager
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

                // Создаём новую таблицу профилей с UID вместо id
                db.execSQL("""
                CREATE TABLE IF NOT EXISTS user_profiles_new (
                    uid TEXT NOT NULL,
                    username TEXT NOT NULL,
                    tag TEXT NOT NULL,
                    bio TEXT NOT NULL,
                    PRIMARY KEY(uid)
                )
            """.trimIndent())

                // Сохраняем старый профиль как устаревшую запись.
                // Он не будет привязан к конкретному аккаунту.
                db.execSQL("""
                INSERT OR IGNORE INTO user_profiles_new
                    (uid, username, tag, bio)
                SELECT '', username, tag, bio
                FROM user_profiles
                WHERE id = 1
            """.trimIndent())

                db.execSQL("DROP TABLE user_profiles")
                db.execSQL(
                    "ALTER TABLE user_profiles_new RENAME TO user_profiles"
                )
            }
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

        pageContainer = findViewById(R.id.pageContainer)

        // Создаём менеджер после инициализации контейнера
        chatsManager = ChatsManager(this) { layoutId ->
            openPage(layoutId)
        }

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
        ) { view, insets ->

            // Отступы системных панелей: статус-бар и навигация
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            // Отступ клавиатуры
            val ime = insets.getInsets(
                WindowInsetsCompat.Type.ime()
            )

            // Берём большую нижнюю границу:
            // обычную системную панель или клавиатуру
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
            ProfileManager(profileView, repository).setup()

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

        // Вся настройка экранов мессенджера находится в ChatsManager
        chatsManager.setupPage(layoutId, view)
    }

    // ============================================================
    // HOME
    // ============================================================

    private fun openHomePage() {
        openPage(R.layout.screen_home)

        val view = pageContainer.getChildAt(0)

        setupHomeLogo(view)
        loadGames()
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
            repository.loadInitialGames()

            val games = repository.getGames()

            withContext(Dispatchers.Main) {
                showHomeGames(games)
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

        // Кнопка возврата
        val backButton = view.findViewById<View>(
            R.id.backButton
        )

        backButton.setOnClickListener {
            openHomePage()

            setActiveNav(
                findViewById(R.id.navHome)
            )
        }

        // Изображение игры
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

        // Название
        view.findViewById<TextView>(
            R.id.gameDetailsTitle
        ).text = game.title

        // Жанр
        view.findViewById<TextView>(
            R.id.gameDetailsGenre
        ).text = game.genre

        // Описание
        view.findViewById<TextView>(
            R.id.gameDetailsDescription
        ).text = game.description
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
