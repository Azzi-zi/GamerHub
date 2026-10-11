package com.example.gamerhub

import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class SearchManager(
    private val activity: MainActivity,
    private val repository: GameRepository,
    private val pageContainer: FrameLayout,
    private val onGameClick: (Game) -> Unit
) {

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


        // ========================================================
        // SEARCH RECYCLERVIEW
        // ========================================================

        val recyclerView = view.findViewById<RecyclerView>(
            R.id.searchGamesRecyclerView
        )

        recyclerView.layoutManager = GridLayoutManager(
            activity,
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


                    activity.lifecycleScope.launch(
                        Dispatchers.IO
                    ) {

                        val games = repository.searchGames(
                            query
                        )

                        withContext(Dispatchers.Main) {

                            recyclerView.adapter =
                                GameAdapter(
                                    games,
                                    repository,
                                    activity
                                ) { game ->
                                    onGameClick(game)
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

        activity.lifecycleScope.launch(
            Dispatchers.IO
        ) {

            repository.loadInitialGames()

            val games = repository.getGames()

            withContext(Dispatchers.Main) {

                recyclerView.adapter =
                    GameAdapter(
                        games,
                        repository,
                        activity
                    ) { game ->
                        onGameClick(game)
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

        activity.lifecycleScope.launch(
            Dispatchers.IO
        ) {

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
                    GameAdapter(
                        games,
                        repository,
                        activity
                    ) { game ->
                        onGameClick(game)
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
}