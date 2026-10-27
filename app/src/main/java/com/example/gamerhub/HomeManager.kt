
package com.example.gamerhub

import android.content.Intent
import android.graphics.Color
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeManager(
    private val activity: AppCompatActivity,
    private val repository: GameRepository,
    private val postsManager: PostsManager,
    private val onGameClick: (Game) -> Unit,
    private val onNotificationsClick: () -> Unit
) {
    private var cachedGames: List<Game>? = null
    private var isLoadingGames = false

    private var selectedCategory = "Все"
    private var currentQuery = ""

    fun setup(view: View) {
        setupHomeLogo(view)
        setupNotificationsButton(view)
        setupHomeCategories(view)
        setupSearch(view)
        loadGames(view)
    }

    private fun setupNotificationsButton(view: View) {
        view.findViewById<View>(R.id.notifications)
            ?.setOnClickListener {
                onNotificationsClick()
            }
    }

    private fun setupHomeLogo(view: View) {
        val title = view.findViewById<TextView>(R.id.logoText)
            ?: return

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
            activity.startActivity(
                Intent(activity, RegisterActivity::class.java)
            )
        }
    }

    private fun setupHomeCategories(view: View) {
        val categories = mapOf(
            R.id.category_all to "Все",
            R.id.category_games to "Игры",
            R.id.category_guides to "Гайды",
            R.id.category_news to "Новости",
            R.id.category_lore to "Лор"
        )

        categories.forEach { (categoryId, categoryName) ->
            view.findViewById<TextView>(categoryId)
                ?.setOnClickListener {

                    selectedCategory = categoryName

                    categories.forEach categoryLoop@{ (id, _) ->
                        val category = view.findViewById<TextView>(id)
                            ?: return@categoryLoop

                        val selected = id == categoryId

                        category.setBackgroundResource(
                            if (selected) {
                                R.drawable.bg_category_active
                            } else {
                                R.drawable.bg_category
                            }
                        )

                        category.setTextColor(
                            if (selected) {
                                Color.WHITE
                            } else {
                                Color.rgb(170, 170, 170)
                            }
                        )
                    }

                    updateContent(view)
                }
        }

        // При открытии страницы выбираем категорию «Все».
        updateContent(view)
    }

    private fun setupSearch(view: View) {
        val searchInput = view.findViewById<EditText>(R.id.search)
            ?: return

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
            ) = Unit

            override fun afterTextChanged(s: Editable?) {
                currentQuery = s?.toString()?.trim().orEmpty()
                updateContent(view)
            }
        })
    }

    private fun updateContent(view: View) {
        val gamesRecycler = view.findViewById<RecyclerView>(
            R.id.gamesRecyclerView
        ) ?: return

        val postsScroll = view.findViewById<ScrollView>(
            R.id.postsScrollView
        ) ?: return

        if (selectedCategory == "Игры") {
            gamesRecycler.visibility = View.VISIBLE
            postsScroll.visibility = View.GONE

            val games = cachedGames

            if (games != null) {
                showFilteredGames(view, games)
            } else {
                loadGames(view)
            }
        } else {
            gamesRecycler.visibility = View.GONE
            postsScroll.visibility = View.VISIBLE

            postsManager.loadPosts(
                view = view,
                category = if (selectedCategory == "Все") {
                    "Все"
                } else {
                    selectedCategory
                },
                query = currentQuery
            )
        }
    }

    private fun loadGames(view: View) {
        cachedGames?.let { games ->
            showFilteredGames(view, games)
            return
        }

        if (isLoadingGames) return

        isLoadingGames = true

        activity.lifecycleScope.launch {
            try {
                val games = withContext(Dispatchers.IO) {
                    try {
                        repository.getGames()
                    } catch (e: Exception) {
                        android.util.Log.e(
                            "HomeManager",
                            "Не удалось загрузить игры из Firebase",
                            e
                        )
                        repository.getLocalGames()
                    }
                }

                cachedGames = games

                // Показываем игры только если выбрана категория «Игры».
                if (selectedCategory == "Игры") {
                    showFilteredGames(view, games)
                }

                if (games.isEmpty()) {
                    Toast.makeText(
                        activity,
                        "Каталог игр пуст",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                android.util.Log.e(
                    "HomeManager",
                    "Ошибка загрузки каталога игр",
                    e
                )

                Toast.makeText(
                    activity,
                    "Не удалось загрузить каталог игр",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isLoadingGames = false
            }
        }
    }

    private fun showFilteredGames(
        view: View,
        games: List<Game>
    ) {
        val recyclerView = view.findViewById<RecyclerView>(
            R.id.gamesRecyclerView
        ) ?: return

        val query = currentQuery

        val filteredGames = if (query.isBlank()) {
            games
        } else {
            games.filter { game ->
                game.title.contains(query, ignoreCase = true) ||
                        game.genre.contains(query, ignoreCase = true) ||
                        game.description.contains(query, ignoreCase = true)
            }
        }

        recyclerView.layoutManager = GridLayoutManager(activity, 2)

        recyclerView.adapter = GameAdapter(
            filteredGames,
            repository,
            activity
        ) { game ->
            onGameClick(game)
        }

        if (filteredGames.isEmpty()) {
            Toast.makeText(
                activity,
                "Игры по запросу «$query» не найдены",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
