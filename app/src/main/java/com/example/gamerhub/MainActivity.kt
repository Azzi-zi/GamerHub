package com.example.gamerhub

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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : AppCompatActivity() {

    // Контейнер, в котором будут открываться разные экраны
    private lateinit var pageContainer: FrameLayout

    // Подключение базы данных Room
    private val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            GamerHubDatabase::class.java,
            "gamerhub_database"
        ).build()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

        // Находим контейнер страниц
        pageContainer = findViewById(R.id.pageContainer)


        // Обработка системных отступов
        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
        ) { v, insets ->

            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }


        // Кнопка "Главная"
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {

            openHomePage()

            setActiveNav(R.id.navHome)
        }


        // Кнопка "Поиск"
        findViewById<LinearLayout>(R.id.navSearch).setOnClickListener {

            openPage(R.layout.screen_search)

            setActiveNav(R.id.navSearch)
        }


        // Кнопка "Создать"
        findViewById<LinearLayout>(R.id.navCreate).setOnClickListener {

            openPage(R.layout.screen_create)

            setActiveNav(R.id.navCreate)
        }


        // Кнопка "Чаты"
        findViewById<LinearLayout>(R.id.navChats).setOnClickListener {

            openPage(R.layout.screen_chats)

            setActiveNav(R.id.navChats)
        }


        // Кнопка "Профиль"
        findViewById<LinearLayout>(R.id.navProfile).setOnClickListener {

            openPage(R.layout.screen_profile)

            setActiveNav(R.id.navProfile)
        }


        // Открываем главный экран при запуске приложения
        openHomePage()

        setActiveNav(R.id.navHome)
    }


    // =========================================================
    // Открытие обычной страницы
    // =========================================================

    private fun openPage(layoutId: Int) {

        // Очищаем предыдущую страницу
        pageContainer.removeAllViews()

        // Загружаем XML
        val view = LayoutInflater.from(this)
            .inflate(
                layoutId,
                pageContainer,
                false
            )

        // Добавляем страницу в контейнер
        pageContainer.addView(view)
    }


    // =========================================================
    // Главная страница
    // =========================================================

    private fun openHomePage() {

        // Очищаем старую страницу
        pageContainer.removeAllViews()

        // Загружаем screen_home.xml
        val view = LayoutInflater.from(this)
            .inflate(
                R.layout.screen_home,
                pageContainer,
                false
            )

        // Добавляем экран
        pageContainer.addView(view)


        // -----------------------------------------------------
        // Логотип GamerHub
        // -----------------------------------------------------

        val logoText = view.findViewById<TextView>(
            R.id.logoText
        )

        val logo = "GamerHub"

        val spannable = SpannableString(logo)


        // Gamer — белый
        spannable.setSpan(
            ForegroundColorSpan(Color.WHITE),
            0,
            4,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )


        // Hub — фиолетовый
        spannable.setSpan(
            ForegroundColorSpan(
                Color.rgb(145, 90, 255)
            ),
            4,
            8,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )


        logoText.text = spannable


        // -----------------------------------------------------
        // Загружаем игры из Room
        // -----------------------------------------------------

        loadGames()
    }


    // =========================================================
    // Загрузка игр из базы данных
    // =========================================================

    private fun loadGames() {

        // Работа с базой выполняется в отдельном потоке
        lifecycleScope.launch(Dispatchers.IO) {

            val dao = database.gameDao()


            // Проверяем, есть ли уже игры
            if (dao.getGameCount() == 0) {

                // Первоначальные данные
                val initialGames = listOf(

                    Game(
                        title = "Minecraft",
                        genre = "Sandbox • Open World",
                        image = "mincraft"
                    ),

                    Game(
                        title = "World of Tanks",
                        genre = "Online • Action",
                        image = "world_of_tanks"
                    ),

                    Game(
                        title = "Baldur's Gate 3",
                        genre = "RPG • Adventure",
                        image = "bal_gate"
                    ),

                    Game(
                        title = "GTA VI",
                        genre = "Action • Open World",
                        image = "gta"
                    ),

                    Game(
                        title = "Genshin Impact",
                        genre = "RPG • Open World",
                        image = "genshin_impact"
                    ),

                    Game(
                        title = "Portal 2",
                        genre = "Puzzle • Adventure",
                        image = "portal"
                    ),

                    Game(
                        title = "Wuthering Waves",
                        genre = "RPG • Action",
                        image = "wuwa"
                    )
                )


                // Добавляем игры в базу
                dao.insertGames(initialGames)
            }


            // Получаем игры из базы
            val games = dao.getAllGames()


            // Возвращаемся в главный поток
            withContext(Dispatchers.Main) {

                showGames(games)
            }
        }
    }


    // =========================================================
    // Показываем игры в RecyclerView
    // =========================================================
    private fun openGameDetails(game: Game) {

        pageContainer.removeAllViews()

        val view = LayoutInflater.from(this)
            .inflate(
                R.layout.screen_game_details,
                pageContainer,
                false
            )

        pageContainer.addView(view)


        val image = view.findViewById<ImageView>(
            R.id.gameDetailsImage
        )

        val title = view.findViewById<TextView>(
            R.id.gameDetailsTitle
        )

        val genre = view.findViewById<TextView>(
            R.id.gameDetailsGenre
        )

        val description = view.findViewById<TextView>(
            R.id.gameDetailsDescription
        )

        val backButton = view.findViewById<TextView>(
            R.id.backButton
        )


        // Загружаем картинку

        val imageId = resources.getIdentifier(
            game.image,
            "drawable",
            packageName
        )

        image.setImageResource(imageId)


        // Загружаем данные игры

        title.text = game.title

        genre.text = game.genre

        description.text =
            "Информация об игре ${game.title} появится здесь."


        // Кнопка назад

        backButton.setOnClickListener {

            openHomePage()

            setActiveNav(R.id.navHome)
        }
    }
    private fun showGames(games: List<Game>) {

        val recyclerView = pageContainer.findViewById<RecyclerView>(
            R.id.gamesRecyclerView
        )

        recyclerView.layoutManager = GridLayoutManager(
            this,
            2
        )

        recyclerView.adapter = GameAdapter(games) { game ->

            openGameDetails(game)
        }
    }


    // =========================================================
    // Нижняя навигация
    // =========================================================

    private fun setActiveNav(activeId: Int) {

        // Все кнопки нижней навигации
        val navIds = listOf(
            R.id.navHome,
            R.id.navSearch,
            R.id.navCreate,
            R.id.navChats,
            R.id.navProfile
        )


        // Цвет активной кнопки
        val activeColor = Color.rgb(
            139,
            92,
            246
        )


        // Цвет неактивной кнопки
        val inactiveColor = Color.rgb(
            119,
            119,
            128
        )


        // Перебираем все кнопки
        for (navId in navIds) {

            val navigationItem = findViewById<LinearLayout>(
                navId
            )


            // Первый элемент — иконка
            val icon = navigationItem.getChildAt(0) as ImageView


            // Второй элемент — текст
            val text = navigationItem.getChildAt(1) as TextView


            if (navId == activeId) {

                // Активная кнопка
                text.setTextColor(activeColor)

                icon.setColorFilter(activeColor)

            } else {

                // Неактивная кнопка
                text.setTextColor(inactiveColor)

                icon.setColorFilter(inactiveColor)
            }
        }
    }
}