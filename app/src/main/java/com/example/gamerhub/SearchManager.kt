
package com.example.gamerhub

import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
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
    // Кэш всего каталога
    private var cachedGames: List<Game>? = null

    // Кэш восьми популярных игр
    private var cachedPopularGames: List<Game>? = null

    private var searchJob: Job? = null
    private var searchVersion = 0
    private var selectedCategory = "Все"

    fun setup(view: View) {
        val searchInput =
            view.findViewById<EditText>(R.id.searchGamesInput)
        val clearButton =
            view.findViewById<ImageView>(R.id.clearSearchButton)
        val resultsTitle =
            view.findViewById<TextView>(R.id.searchResultsTitle)

        val categoryAll =
            view.findViewById<TextView>(R.id.categoryAll)
        val categoryAction =
            view.findViewById<TextView>(R.id.categoryAction)
        val categoryRpg =
            view.findViewById<TextView>(R.id.categoryRpg)
        val categorySandbox =
            view.findViewById<TextView>(R.id.categorySandbox)
        val categoryOnline =
            view.findViewById<TextView>(R.id.categoryOnline)

        val trendingMinecraft =
            view.findViewById<TextView>(R.id.trendingMinecraft)
        val trendingGenshin =
            view.findViewById<TextView>(R.id.trendingGenshin)
        val trendingGta =
            view.findViewById<TextView>(R.id.trendingGta)

        val recyclerView =
            view.findViewById<RecyclerView>(R.id.searchGamesRecyclerView)

        recyclerView.layoutManager = GridLayoutManager(activity, 2)
        recyclerView.isNestedScrollingEnabled = false

        loadSearchGames(recyclerView, resultsTitle)

        fun selectCategory(category: String) {
            selectedCategory = category

            updateCategoryButtons(
                categoryAll,
                categoryAction,
                categoryRpg,
                categorySandbox,
                categoryOnline
            )

            showFilteredGames(
                recyclerView,
                resultsTitle,
                searchInput.text.toString()
            )
        }

        categoryAll.setOnClickListener {
            selectCategory("Все")
        }

        categoryAction.setOnClickListener {
            selectCategory("Action")
        }

        categoryRpg.setOnClickListener {
            selectCategory("RPG")
        }

        categorySandbox.setOnClickListener {
            selectCategory("Sandbox")
        }

        categoryOnline.setOnClickListener {
            selectCategory("Online")
        }

        searchInput.addTextChangedListener(object : TextWatcher {
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
                    if (query.isEmpty()) View.GONE else View.VISIBLE

                searchJob?.cancel()
                searchVersion++
                val currentVersion = searchVersion

                if (query.isBlank()) {
                    resultsTitle.visibility = View.GONE

                    showFilteredGames(
                        recyclerView,
                        resultsTitle,
                        ""
                    )
                    return
                }

                resultsTitle.visibility = View.VISIBLE
                resultsTitle.text = "Поиск..."

                searchJob = activity.lifecycleScope.launch {
                    delay(250)

                    val games = cachedGames ?: getGamesSafely().also {
                        cachedGames = it
                    }

                    val filteredGames = withContext(Dispatchers.Default) {
                        filterGamesList(
                            games,
                            query,
                            selectedCategory
                        )
                    }

                    if (currentVersion != searchVersion) return@launch

                    displayGames(recyclerView, filteredGames)

                    resultsTitle.text =
                        if (filteredGames.isEmpty()) {
                            "Ничего не найдено"
                        } else {
                            "Найдено: ${filteredGames.size}"
                        }
                }
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        clearButton.setOnClickListener {
            searchInput.text.clear()
        }

        trendingMinecraft.setOnClickListener {
            selectedCategory = "Все"
            updateCategoryButtons(
                categoryAll,
                categoryAction,
                categoryRpg,
                categorySandbox,
                categoryOnline
            )
            searchInput.setText("Minecraft")
        }

        trendingGenshin.setOnClickListener {
            selectedCategory = "Все"
            updateCategoryButtons(
                categoryAll,
                categoryAction,
                categoryRpg,
                categorySandbox,
                categoryOnline
            )
            searchInput.setText("Genshin Impact")
        }

        trendingGta.setOnClickListener {
            selectedCategory = "Все"
            updateCategoryButtons(
                categoryAll,
                categoryAction,
                categoryRpg,
                categorySandbox,
                categoryOnline
            )
            searchInput.setText("GTA")
        }
    }

    private fun loadSearchGames(
        recyclerView: RecyclerView,
        resultsTitle: TextView
    ) {
        // Если популярная восьмёрка уже загружена,
        // отображаем её сразу.
        cachedPopularGames?.let { popularGames ->
            displayGames(recyclerView, popularGames)
            resultsTitle.visibility = View.GONE
            return
        }

        // Если весь каталог есть в кэше,
        // вычисляем популярные игры без обращения к Firestore.
        cachedGames?.let { games ->
            val popularGames = getPopularGames(games)
            cachedPopularGames = popularGames

            displayGames(recyclerView, popularGames)
            resultsTitle.visibility = View.GONE
            return
        }

        // Первый запуск: загружаем каталог.
        activity.lifecycleScope.launch {
            try {
                val games = getGamesSafely()
                cachedGames = games

                val popularGames = getPopularGames(games)
                cachedPopularGames = popularGames

                displayGames(recyclerView, popularGames)
                resultsTitle.visibility = View.GONE
            } catch (e: Exception) {
                Log.e(
                    "GamerHubSearch",
                    "Ошибка загрузки каталога",
                    e
                )

                resultsTitle.visibility = View.VISIBLE
                resultsTitle.text = "Не удалось загрузить игры"
            }
        }
    }

    // Получаем восемь игр с наибольшим количеством лайков.
    private fun getPopularGames(games: List<Game>): List<Game> {
        return games
            .sortedWith(
                compareByDescending<Game> { it.likesCount }
                    .thenBy { it.title.lowercase() }
            )
            .take(8)
    }

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

        // При пустом поиске и выбранной категории «Все»
        // показываем только популярную восьмёрку.
        if (query.isBlank() && selectedCategory == "Все") {
            val popularGames = cachedPopularGames ?: getPopularGames(games).also {
                cachedPopularGames = it
            }

            displayGames(recyclerView, popularGames)
            resultsTitle.visibility = View.GONE
            return
        }

        // Поиск и остальные категории работают по всему каталогу.
        val filteredGames = filterGamesList(
            games,
            query,
            selectedCategory
        )

        displayGames(recyclerView, filteredGames)

        if (query.isBlank()) {
            resultsTitle.visibility = View.GONE
        } else {
            resultsTitle.visibility = View.VISIBLE
            resultsTitle.text =
                if (filteredGames.isEmpty()) {
                    "Ничего не найдено"
                } else {
                    "Найдено: ${filteredGames.size}"
                }
        }
    }

    private fun filterGamesList(
        games: List<Game>,
        query: String,
        category: String
    ): List<Game> {
        return games.filter { game ->
            val matchesQuery =
                query.isBlank() ||
                        game.title.contains(query, ignoreCase = true)

            val matchesCategory =
                category == "Все" ||
                        game.genre.contains(category, ignoreCase = true)

            matchesQuery && matchesCategory
        }
    }

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


    private fun updateCategoryButtons(
        categoryAll: TextView,
        categoryAction: TextView,
        categoryRpg: TextView,
        categorySandbox: TextView,
        categoryOnline: TextView
    ) {
        val buttons = mapOf(
            "Все" to categoryAll,
            "Action" to categoryAction,
            "RPG" to categoryRpg,
            "Sandbox" to categorySandbox,
            "Online" to categoryOnline
        )

        buttons.forEach { (category, button) ->
            val selected = category == selectedCategory

            button.setTextColor(
                if (selected) Color.WHITE else Color.LTGRAY
            )

            button.setBackgroundResource(
                if (selected) {
                    R.drawable.bg_category_active
                } else {
                    R.drawable.bg_category
                }
            )
        }
    }

    private suspend fun getGamesSafely(): List<Game> {
        return try {
            withContext(Dispatchers.IO) {
                repository.getGames()
            }
        } catch (e: Exception) {
            Log.e(
                "GamerHubSearch",
                "Ошибка загрузки игр из Firestore",
                e
            )

            try {
                withContext(Dispatchers.IO) {
                    repository.getLocalGames()
                }
            } catch (localException: Exception) {
                Log.e(
                    "GamerHubSearch",
                    "Ошибка загрузки локальных игр",
                    localException
                )
                emptyList()
            }
        }
    }
}
