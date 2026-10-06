package com.example.gamerhub

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
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

    private lateinit var pageContainer: FrameLayout

    // ============================================================
    // DATABASE
    // ============================================================

    private val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            GamerHubDatabase::class.java,
            "gamerhub_database"
        )
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

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        pageContainer = findViewById(R.id.pageContainer)

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
        ) { view, insets ->

            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }

        setupBottomNavigation()

        openHomePage()

        setActiveNav(
            findViewById(R.id.navHome)
        )
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

        val title = view.findViewById<TextView>(
            R.id.logoText
        )

        val text = "GamerHub"

        val spannable = SpannableString(text)

        spannable.setSpan(
            ForegroundColorSpan(Color.WHITE),
            0,
            5,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        spannable.setSpan(
            ForegroundColorSpan(
                Color.rgb(170, 80, 255)
            ),
            5,
            text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        title.text = spannable
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
        )

        recyclerView.layoutManager = GridLayoutManager(
            this,
            2
        )

        recyclerView.adapter = GameAdapter(games) { game ->
            openGameDetails(game)
        }
    }


    // ============================================================
    // SEARCH
    // ============================================================

    private fun openSearchPage() {

        openPage(R.layout.screen_search)

        val view = pageContainer.getChildAt(0)

        setupSearchPage(view)
    }


    private fun setupSearchPage(view: View) {

        val searchInput = view.findViewById<EditText>(
            R.id.searchGamesInput
        )

        val clearButton = view.findViewById<ImageView>(
            R.id.clearSearchButton
        )

        val resultsTitle = view.findViewById<TextView>(
            R.id.searchResultsTitle
        )

        val categoryAll = view.findViewById<TextView>(
            R.id.categoryAll
        )

        val categoryAction = view.findViewById<TextView>(
            R.id.categoryAction
        )

        val categoryRpg = view.findViewById<TextView>(
            R.id.categoryRpg
        )

        val categorySandbox = view.findViewById<TextView>(
            R.id.categorySandbox
        )

        val categoryOnline = view.findViewById<TextView>(
            R.id.categoryOnline
        )

        val trendingMinecraft = view.findViewById<TextView>(
            R.id.trendingMinecraft
        )

        val trendingGenshin = view.findViewById<TextView>(
            R.id.trendingGenshin
        )

        val trendingGta = view.findViewById<TextView>(
            R.id.trendingGta
        )


        // ========================================================
        // SEARCH RECYCLERVIEW
        // ========================================================

        val recyclerView = view.findViewById<RecyclerView>(
            R.id.searchGamesRecyclerView
        )

        recyclerView.layoutManager = GridLayoutManager(
            this,
            2
        )

        recyclerView.isNestedScrollingEnabled = false


        // ========================================================
        // INITIAL GAMES
        // ========================================================

        loadSearchGames(
            recyclerView,
            resultsTitle
        )


        // ========================================================
        // CATEGORIES
        // ========================================================

        categoryAll.setOnClickListener {

            updateCategoryButtons(
                categoryAll,
                categoryAction,
                categoryRpg,
                categorySandbox,
                categoryOnline,
                "Все"
            )

            loadSearchGames(
                recyclerView,
                resultsTitle
            )
        }


        categoryAction.setOnClickListener {

            updateCategoryButtons(
                categoryAll,
                categoryAction,
                categoryRpg,
                categorySandbox,
                categoryOnline,
                "Action"
            )

            filterGames(
                recyclerView,
                "Action"
            )
        }


        categoryRpg.setOnClickListener {

            updateCategoryButtons(
                categoryAll,
                categoryAction,
                categoryRpg,
                categorySandbox,
                categoryOnline,
                "RPG"
            )

            filterGames(
                recyclerView,
                "RPG"
            )
        }


        categorySandbox.setOnClickListener {

            updateCategoryButtons(
                categoryAll,
                categoryAction,
                categoryRpg,
                categorySandbox,
                categoryOnline,
                "Sandbox"
            )

            filterGames(
                recyclerView,
                "Sandbox"
            )
        }


        categoryOnline.setOnClickListener {

            updateCategoryButtons(
                categoryAll,
                categoryAction,
                categoryRpg,
                categorySandbox,
                categoryOnline,
                "Online"
            )

            filterGames(
                recyclerView,
                "Online"
            )
        }


        // ========================================================
        // SEARCH TEXT
        // ========================================================

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

                    val query = s
                        ?.toString()
                        ?.trim()
                        ?: ""

                    if (query.isBlank()) {

                        clearButton.visibility = View.GONE
                        resultsTitle.visibility = View.GONE

                        loadSearchGames(
                            recyclerView,
                            resultsTitle
                        )

                        return
                    }

                    clearButton.visibility = View.VISIBLE
                    resultsTitle.visibility = View.VISIBLE

                    lifecycleScope.launch(Dispatchers.IO) {

                        val games = repository.searchGames(
                            query
                        )

                        withContext(Dispatchers.Main) {

                            recyclerView.adapter =
                                GameAdapter(games) { game ->
                                    openGameDetails(game)
                                }

                            resultsTitle.text =
                                if (games.isEmpty()) {
                                    "Ничего не найдено"
                                } else {
                                    "Найдено: ${games.size}"
                                }
                        }
                    }
                }

                override fun afterTextChanged(
                    s: Editable?
                ) {
                }
            }
        )


        // ========================================================
        // CLEAR SEARCH
        // ========================================================

        clearButton.setOnClickListener {
            searchInput.text.clear()
            searchInput.requestFocus()
        }


        // ========================================================
        // TRENDING
        // ========================================================

        trendingMinecraft.setOnClickListener {

            searchInput.setText("Minecraft")

            searchInput.setSelection(
                searchInput.text.length
            )
        }


        trendingGenshin.setOnClickListener {

            searchInput.setText("Genshin Impact")

            searchInput.setSelection(
                searchInput.text.length
            )
        }


        trendingGta.setOnClickListener {

            searchInput.setText("GTA VI")

            searchInput.setSelection(
                searchInput.text.length
            )
        }
    }


    // ============================================================
    // LOAD SEARCH GAMES
    // ============================================================

    private fun loadSearchGames(
        recyclerView: RecyclerView,
        resultsTitle: TextView
    ) {

        lifecycleScope.launch(Dispatchers.IO) {

            repository.loadInitialGames()

            val games = repository.getGames()

            withContext(Dispatchers.Main) {

                recyclerView.adapter = GameAdapter(games) { game ->
                    openGameDetails(game)
                }

                resultsTitle.visibility = View.GONE
            }
        }
    }


    // ============================================================
    // FILTER GAMES
    // ============================================================

    private fun filterGames(
        recyclerView: RecyclerView,
        category: String
    ) {

        lifecycleScope.launch(Dispatchers.IO) {

            val games = repository
                .getGames()
                .filter {
                    it.genre.contains(
                        category,
                        ignoreCase = true
                    )
                }

            withContext(Dispatchers.Main) {

                recyclerView.adapter =
                    GameAdapter(games) { game ->
                        openGameDetails(game)
                    }
            }
        }
    }


    // ============================================================
    // CATEGORY BUTTONS
    // ============================================================

    private fun updateCategoryButtons(
        all: TextView,
        action: TextView,
        rpg: TextView,
        sandbox: TextView,
        online: TextView,
        selected: String
    ) {

        val buttons = listOf(
            all,
            action,
            rpg,
            sandbox,
            online
        )

        // Сначала делаем все категории неактивными
        buttons.forEach {
            it.setTextColor(
                Color.rgb(170, 170, 170)
            )

            it.setBackgroundResource(
                R.drawable.bg_category
            )
        }

        // Определяем выбранную категорию
        val selectedButton = when (selected) {

            "Action" -> action
            "RPG" -> rpg
            "Sandbox" -> sandbox
            "Online" -> online

            else -> all
        }

        // Делаем выбранную категорию активной
        selectedButton.setTextColor(
            Color.WHITE
        )

        selectedButton.setBackgroundResource(
            R.drawable.bg_category_active
        )
    }

    // ============================================================
    // GAME DETAILS
    // ============================================================

    private fun openGameDetails(game: Game) {

        openPage(
            R.layout.screen_game_details
        )

        val view = pageContainer.getChildAt(0)


        // --------------------------------------------------------
        // BACK
        // --------------------------------------------------------

        val backButton = view.findViewById<View>(
            R.id.backButton
        )

        backButton.setOnClickListener {

            openHomePage()

            setActiveNav(
                findViewById(R.id.navHome)
            )
        }


        // --------------------------------------------------------
        // IMAGE
        // --------------------------------------------------------

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


        // --------------------------------------------------------
        // TITLE
        // --------------------------------------------------------

        view.findViewById<TextView>(
            R.id.gameDetailsTitle
        ).text = game.title


        // --------------------------------------------------------
        // GENRE
        // --------------------------------------------------------

        view.findViewById<TextView>(
            R.id.gameDetailsGenre
        ).text = game.genre


        // --------------------------------------------------------
        // DESCRIPTION
        // --------------------------------------------------------

        view.findViewById<TextView>(
            R.id.gameDetailsDescription
        ).text = game.description
    }


    // ============================================================
    // BOTTOM NAVIGATION ACTIVE STATE
    // ============================================================

    private fun setActiveNav(
        selected: LinearLayout
    ) {

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