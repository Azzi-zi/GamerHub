package com.example.gamerhub

import android.widget.GridLayout
import android.text.Editable
import android.text.TextWatcher
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : AppCompatActivity() {

    // =========================================================
    // Контейнер, в котором открываются разные экраны
    // =========================================================

    private lateinit var pageContainer: FrameLayout


    // =========================================================
    // Подключение базы данных Room
    // =========================================================

    private val database by lazy {

        Room.databaseBuilder(
            applicationContext,
            GamerHubDatabase::class.java,
            "gamerhub_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }


    // =========================================================
    // Repository
    // =========================================================

    private val repository by lazy {

        GameRepository(
            database.gameDao()
        )
    }


    // =========================================================
    // Создание Activity
    // =========================================================

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(R.layout.activity_main)


        // Находим контейнер страниц

        pageContainer = findViewById(
            R.id.pageContainer
        )


        // =====================================================
        // Системные отступы
        // =====================================================

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


        // =====================================================
        // Нижняя навигация
        // =====================================================


        // Главная

        findViewById<LinearLayout>(
            R.id.navHome
        ).setOnClickListener {

            openHomePage()

            setActiveNav(
                R.id.navHome
            )
        }


        // Поиск

        findViewById<LinearLayout>(
            R.id.navSearch
        ).setOnClickListener {

            openSearchPage()

            setActiveNav(
                R.id.navSearch
            )
        }


        // Создать

        findViewById<LinearLayout>(
            R.id.navCreate
        ).setOnClickListener {

            openPage(
                R.layout.screen_create
            )

            setActiveNav(
                R.id.navCreate
            )
        }


        // Чаты

        findViewById<LinearLayout>(
            R.id.navChats
        ).setOnClickListener {

            openPage(
                R.layout.screen_chats
            )

            setActiveNav(
                R.id.navChats
            )
        }


        // Профиль

        findViewById<LinearLayout>(
            R.id.navProfile
        ).setOnClickListener {

            openPage(
                R.layout.screen_profile
            )

            setActiveNav(
                R.id.navProfile
            )
        }


        // =====================================================
        // Открываем главную страницу при запуске
        // =====================================================

        openHomePage()

        setActiveNav(
            R.id.navHome
        )
    }


    // =========================================================
    // Открытие обычной страницы
    // =========================================================

    private fun openPage(layoutId: Int) {

        // Удаляем предыдущий экран

        pageContainer.removeAllViews()


        // Загружаем XML

        val view = LayoutInflater.from(this)
            .inflate(
                layoutId,
                pageContainer,
                false
            )


        // Добавляем экран

        pageContainer.addView(view)
    }


    // =========================================================
    // Главная страница
    // =========================================================

    private fun openHomePage() {

        // Очищаем предыдущий экран

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


        // =====================================================
        // Логотип GamerHub
        // =====================================================

        val logoText = view.findViewById<TextView>(
            R.id.logoText
        )


        val logo = "GamerHub"

        val spannable = SpannableString(
            logo
        )


        // Gamer — белый

        spannable.setSpan(
            ForegroundColorSpan(
                Color.WHITE
            ),
            0,
            4,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )


        // Hub — фиолетовый

        spannable.setSpan(
            ForegroundColorSpan(
                Color.rgb(
                    145,
                    90,
                    255
                )
            ),
            4,
            8,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )


        logoText.text = spannable


        // =====================================================
        // Загружаем игры
        // =====================================================

        loadGames()
    }


    // =========================================================
    // Загрузка игр из Room
    // =========================================================

    private fun loadGames() {

        lifecycleScope.launch(
            Dispatchers.IO
        ) {

            // Если база пустая —
            // добавляем наши первоначальные игры

            repository.loadInitialGames()


            // Получаем игры

            val games = repository.getGames()


            // Возвращаемся в главный поток

            withContext(
                Dispatchers.Main
            ) {

                showGames(games)
            }
        }
    }


    // =========================================================
    // Показываем игры на главной
    // =========================================================

    private fun showGames(
        games: List<Game>
    ) {

        val recyclerView =
            pageContainer.findViewById<RecyclerView>(
                R.id.gamesRecyclerView
            )


        recyclerView.layoutManager =
            GridLayoutManager(
                this,
                2
            )


        recyclerView.adapter =
            GameAdapter(games) { game ->

                openGameDetails(game)
            }
    }


    // =========================================================
    // Экран поиска
    // =========================================================

    private fun openSearchPage() {

        // Очищаем предыдущий экран

        pageContainer.removeAllViews()


        // Загружаем screen_search.xml

        val view = LayoutInflater.from(this)
            .inflate(
                R.layout.screen_search,
                pageContainer,
                false
            )


        // Добавляем экран

        pageContainer.addView(view)


        // Настраиваем поиск

        setupSearchPage(view)
    }


    // =========================================================
// Настройка поиска
// =========================================================

    private fun setupSearchPage(view: View) {

        val searchInput =
            view.findViewById<EditText>(R.id.searchGamesInput)

        val clearButton =
            view.findViewById<ImageView>(R.id.clearSearchButton)

        // Первоначальные популярные игры
        val gamesRecyclerView =
            view.findViewById<RecyclerView>(R.id.searchGamesRecyclerView)

        // Результаты поиска
        val searchResultsRecyclerView =
            view.findViewById<RecyclerView>(R.id.searchResultsRecyclerView)

        val resultsTitle =
            view.findViewById<TextView>(R.id.searchResultsTitle)

        // Адаптер для первоначальных игр
        val gamesAdapter =
            GameAdapter(emptyList()) { game ->
                openGameDetails(game)
            }

        gamesRecyclerView.layoutManager =
            GridLayoutManager(this, 2)

        gamesRecyclerView.adapter = gamesAdapter

        // Загружаем игры из Room
        lifecycleScope.launch(Dispatchers.IO) {

            repository.loadInitialGames()

            val games =
                repository.getGames()

            withContext(Dispatchers.Main) {
                gamesAdapter.updateGames(games)
            }
        }

        // Адаптер результатов поиска
        val searchAdapter =
            SearchGameAdapter(emptyList()) { game ->
                openGameDetails(game)
            }

        searchResultsRecyclerView.layoutManager =
            GridLayoutManager(this, 2)

        searchResultsRecyclerView.adapter =
            searchAdapter

        // Поиск при вводе текста
        searchInput.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {

                    val query =
                        s?.toString()?.trim() ?: ""

                    if (query.isBlank()) {

                        clearButton.visibility = View.GONE

                        gamesRecyclerView.visibility =
                            View.VISIBLE

                        resultsTitle.visibility =
                            View.GONE

                        searchResultsRecyclerView.visibility =
                            View.GONE

                        searchAdapter.updateGames(
                            emptyList()
                        )

                        return
                    }

                    clearButton.visibility =
                        View.VISIBLE

                    gamesRecyclerView.visibility =
                        View.GONE

                    resultsTitle.visibility =
                        View.VISIBLE

                    searchResultsRecyclerView.visibility =
                        View.VISIBLE

                    resultsTitle.text =
                        "Поиск: $query"

                    searchGames(
                        query,
                        searchAdapter,
                        resultsTitle
                    )
                }

                override fun afterTextChanged(
                    s: Editable?
                ) {
                }
            }
        )

        // Кнопка очистки
        clearButton.setOnClickListener {

            searchInput.text.clear()

            searchInput.requestFocus()
        }
    }


    // =========================================================
// Поиск игр в Room
// =========================================================

    private fun searchGames(
        query: String,
        adapter: SearchGameAdapter,
        resultsTitle: TextView
    ) {

        if (query.isBlank()) {
            adapter.updateGames(emptyList())
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {

            val games =
                repository.searchGames(query)

            withContext(Dispatchers.Main) {

                adapter.updateGames(games)

                resultsTitle.text =
                    if (games.isEmpty()) {
                        "Ничего не найдено"
                    } else {
                        "Найдено: ${games.size}"
                    }
            }
        }
    }

    // =========================================================
    // Экран информации об игре
    // =========================================================

    private fun openGameDetails(
        game: Game
    ) {

        // Очищаем предыдущий экран

        pageContainer.removeAllViews()


        // Загружаем XML

        val view = LayoutInflater.from(this)
            .inflate(
                R.layout.screen_game_details,
                pageContainer,
                false
            )


        // Добавляем экран

        pageContainer.addView(view)


        // =====================================================
        // Находим элементы
        // =====================================================

        val image =
            view.findViewById<ImageView>(
                R.id.gameDetailsImage
            )


        val title =
            view.findViewById<TextView>(
                R.id.gameDetailsTitle
            )


        val genre =
            view.findViewById<TextView>(
                R.id.gameDetailsGenre
            )


        val description =
            view.findViewById<TextView>(
                R.id.gameDetailsDescription
            )


        val backButton =
            view.findViewById<TextView>(
                R.id.backButton
            )


        // =====================================================
        // Загружаем картинку
        // =====================================================

        if (
            game.image.startsWith("http")
        ) {

            // Картинка из интернета

            Glide.with(image)
                .load(game.image)
                .into(image)

        } else {

            // Локальная картинка из drawable

            val imageId =
                resources.getIdentifier(
                    game.image,
                    "drawable",
                    packageName
                )


            image.setImageResource(
                imageId
            )
        }


        // =====================================================
        // Загружаем информацию об игре
        // =====================================================

        title.text = game.title

        genre.text = game.genre

        description.text =
            game.description


        // =====================================================
        // Кнопка "Назад"
        // =====================================================

        backButton.setOnClickListener {

            openHomePage()

            setActiveNav(
                R.id.navHome
            )
        }
    }


    // =========================================================
    // Нижняя навигация
    // =========================================================

    private fun setActiveNav(
        activeId: Int
    ) {

        // Все элементы навигации

        val navIds = listOf(

            R.id.navHome,

            R.id.navSearch,

            R.id.navCreate,

            R.id.navChats,

            R.id.navProfile
        )


        // Цвет активного элемента

        val activeColor =
            Color.rgb(
                139,
                92,
                246
            )


        // Цвет неактивного элемента

        val inactiveColor =
            Color.rgb(
                119,
                119,
                128
            )


        // Перебираем элементы

        for (navId in navIds) {

            val navigationItem =
                findViewById<LinearLayout>(
                    navId
                )


            // Первый элемент —
            // ImageView

            val icon =
                navigationItem.getChildAt(
                    0
                ) as ImageView


            // Второй элемент —
            // TextView

            val text =
                navigationItem.getChildAt(
                    1
                ) as TextView


            if (
                navId == activeId
            ) {

                // Активный

                text.setTextColor(
                    activeColor
                )

                icon.setColorFilter(
                    activeColor
                )

            } else {

                // Неактивный

                text.setTextColor(
                    inactiveColor
                )

                icon.setColorFilter(
                    inactiveColor
                )
            }
        }
    }
}