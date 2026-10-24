
package com.example.gamerhub

import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchManager(
    private val activity: MainActivity,
    private val repository: GameRepository,
    private val pageContainer: FrameLayout,
    private val onGameClick: (Game) -> Unit
) {

    // Кэш каталога: загружаем игры только один раз на экземпляр менеджера.
    private var cachedGames: List<Game>? = null

    // Задача текущего поиска.
    private var searchJob: Job? = null

    // Версия поиска защищает от устаревших результатов.
    private var searchVersion = 0

    // Текущая выбранная категория.
    private var selectedCategory = "Все"

    fun setup(view: View) {
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

        val recyclerView = view.findViewById<RecyclerView>(
            R.id.searchGamesRecyclerView
        )

        recyclerView.layoutManager = GridLayoutManager(activity, 2)
        recyclerView.isNestedScrollingEnabled = false

        // Загружаем каталог или используем кэш.
        loadSearchGames(recyclerView, resultsTitle)

        // Категория «Все».
        categoryAll.setOnClickListener {
            selectedCategory = "Все"

            updateCategoryButtons(
                categoryAll,
                categoryAction,
                categoryRpg,
                categorySandbox,
                categoryOnline,
                selectedCategory
            )

            showFilteredGames(
                recyclerView,
                resultsTitle,
                searchInput.text.toString()
            )
        }

        // Категория Action.
        categoryAction.setOnClickListener {
            selectedCategory = "Action"

            updateCategoryButtons(
                categoryAll,
                categoryAction,
                categoryRpg,
                categorySandbox,
                categoryOnline,
                selectedCategory
            )

            showFilteredGames(
                recyclerView,
                resultsTitle,
                searchInput.text.toString()
            )
        }

        // Категория RPG.
        categoryRpg.setOnClickListener {
            selectedCategory = "RPG"

            updateCategoryButtons(
                categoryAll,
                categoryAction,
                categoryRpg,
                categorySandbox,
                categoryOnline,
                selectedCategory
            )

            showFilteredGames(
                recyclerView,
                resultsTitle,
                searchInput.text.toString()
            )
        }

        // Категория Sandbox.
        categorySandbox.setOnClickListener {
            selectedCategory = "Sandbox"

            updateCategoryButtons(
                categoryAll,
                categoryAction,
                categoryRpg,
                categorySandbox,
                categoryOnline,
                selectedCategory
            )

            showFilteredGames(
                recyclerView,
                resultsTitle,
                searchInput.text.toString()
            )
        }

        // Категория Online.
        categoryOnline.setOnClickListener {
            selectedCategory = "Online"

            updateCategoryButtons(
                categoryAll,
                categoryAction,
                categoryRpg,
                categorySandbox,
                categoryOnline,
                selectedCategory
            )

            showFilteredGames(
                recyclerView,
                resultsTitle,
                searchInput.text.toString()
            )
        }

        // Поиск по названию игры.
        searchInput.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    val query = s?.toString()?.trim().orEmpty()

                    clearButton.visibility =
                        if (query.isBlank()) View.GONE else View.VISIBLE

                    if (query.isBlank()) {
                        searchJob?.cancel()
                        searchVersion++

                        resultsTitle.visibility = View.GONE

                        showFilteredGames(
                            recyclerView,
                            resultsTitle,
                            ""
                        )

                        return
                    }

                    resultsTitle.visibility = View.VISIBLE

                    // Отменяем предыдущий поиск.
                    searchJob?.cancel()
                    val currentVersion = ++searchVersion

                    searchJob = activity.lifecycleScope.launch {
                        // Небольшая пауза после ввода.
                        delay(250)

                        // Если каталог ещё загружается, загружаем его.
                        val games = cachedGames ?: getGamesSafely().also {
                            cachedGames = it
                        }

                        // Не применяем результат старого запроса.
                        if (currentVersion != searchVersion) {
                            return@launch
                        }

                        val filteredGames = withContext(Dispatchers.Default) {
                            filterGamesList(games, query, selectedCategory)
                        }

                        if (currentVersion != searchVersion) {
                            return@launch
                        }

                        displayGames(recyclerView, filteredGames)

                        resultsTitle.text = if (filteredGames.isEmpty()) {
                            "Ничего не найдено"
                        } else {
                            "Найдено: ${filteredGames.size}"
                        }
                    }
                }

                override fun afterTextChanged(s: Editable?) = Unit
            }
        )

        // Очистка поиска.
        clearButton.setOnClickListener {
            searchInput.text.clear()
            searchInput.requestFocus()
        }

        // Популярный запрос: Minecraft.
        trendingMinecraft.setOnClickListener {
            searchInput.setText("Minecraft")
            searchInput.setSelection(searchInput.text.length)
        }

        // Популярный запрос: Genshin Impact.
        trendingGenshin.setOnClickListener {
            searchInput.setText("Genshin Impact")
            searchInput.setSelection(searchInput.text.length)
        }

        // Популярный запрос: GTA VI.
        trendingGta.setOnClickListener {
            searchInput.setText("GTA VI")
            searchInput.setSelection(searchInput.text.length)
        }
    }

    // Загружаем каталог и сохраняем его в кэше.
    private fun loadSearchGames(
        recyclerView: RecyclerView,
        resultsTitle: TextView
    ) {
        val cached = cachedGames

        if (cached != null) {
            displayGames(recyclerView, cached)
            resultsTitle.visibility = View.GONE
            return
        }

        activity.lifecycleScope.launch {
            try {
                val games = getGamesSafely()
                cachedGames = games

                displayGames(recyclerView, games)
                resultsTitle.visibility = View.GONE

            } catch (e: Exception) {
                android.util.Log.e(
                    "GamerHubSearch",
                    "Ошибка загрузки каталога",
                    e
                )

                resultsTitle.visibility = View.VISIBLE
                resultsTitle.text = "Не удалось загрузить игры"
            }
        }
    }

    // Фильтруем уже загруженные игры без нового запроса к Firebase.
    private fun showFilteredGames(
        recyclerView: RecyclerView,
        resultsTitle: TextView,
        query: String
    ) {
        val games = cachedGames

        if (games == null) {
            loadSearchGames(recyclerView, resultsTitle)
            return
        }

        val filtered = filterGamesList(
            games,
            query,
            selectedCategory
        )

        displayGames(recyclerView, filtered)

        if (query.isBlank()) {
            resultsTitle.visibility = View.GONE
        } else {
            resultsTitle.visibility = View.VISIBLE
            resultsTitle.text = if (filtered.isEmpty()) {
                "Ничего не найдено"
            } else {
                "Найдено: ${filtered.size}"
            }
        }
    }

    // Общая фильтрация по названию и категории.
    private fun filterGamesList(
        games: List<Game>,
        query: String,
        category: String
    ): List<Game> {
        return games.filter { game ->
            val matchesQuery = query.isBlank() ||
                    game.title.contains(query, ignoreCase = true)

            val matchesCategory = category == "Все" ||
                    game.genre.contains(category, ignoreCase = true)

            matchesQuery && matchesCategory
        }
    }

    // Отображаем карточки игр.
    private fun displayGames(
        recyclerView: RecyclerView,
        games: List<Game>
    ) {
        recyclerView.adapter = GameAdapter(
            games,
            repository,
            activity
        ) { game ->
            onGameClick(game)
        }
    }

    // Обновляем оформление кнопок категорий.
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

        buttons.forEach {
            it.setTextColor(Color.rgb(170, 170, 170))
            it.setBackgroundResource(R.drawable.bg_category)
        }

        val selectedButton = when (selected) {
            "Action" -> action
            "RPG" -> rpg
            "Sandbox" -> sandbox
            "Online" -> online
            else -> all
        }

        selectedButton.setTextColor(Color.WHITE)
        selectedButton.setBackgroundResource(
            R.drawable.bg_category_active
        )
    }

    // Firebase — основной источник, Room — резервный.
    private suspend fun getGamesSafely(): List<Game> {
        return try {
            withContext(Dispatchers.IO) {
                repository.getGames()
            }
        } catch (e: Exception) {
            android.util.Log.e(
                "GamerHubSearch",
                "Ошибка загрузки каталога из Firebase",
                e
            )

            try {
                withContext(Dispatchers.IO) {
                    repository.getLocalGames()
                }
            } catch (localError: Exception) {
                android.util.Log.e(
                    "GamerHubSearch",
                    "Ошибка загрузки каталога из Room",
                    localError
                )

                emptyList()
            }
        }
    }
}
