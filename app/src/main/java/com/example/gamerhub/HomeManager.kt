
package com.example.gamerhub

import android.content.Intent
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
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
    // Кэш каталога, чтобы не загружать игры при каждом переходе.
    private var cachedGames: List<Game>? = null

    // Не допускаем одновременную загрузку одного каталога.
    private var isLoadingGames = false

    // Настраиваем главную страницу
    fun setup(view: View) {
        setupHomeLogo(view)
        setupNotificationsButton(view)
        setupHomeCategories(view)
        loadGames(view)
    }

    // Открываем уведомления при нажатии на колокольчик
    private fun setupNotificationsButton(view: View) {
        view.findViewById<View>(R.id.notifications)?.setOnClickListener {
            onNotificationsClick()
        }
    }

    // Оформляем логотип
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

        // Сохраняем прежнее поведение нажатия на логотип
        title.setOnClickListener {
            activity.startActivity(
                Intent(activity, RegisterActivity::class.java)
            )
        }
    }

    // Настраиваем категории главной страницы


    private fun setupHomeCategories(view: View) {
        val categories = mapOf(
            R.id.category_all to "Все",
            R.id.category_games to "Игры",
            R.id.category_guides to "Гайды",
            R.id.category_news to "Новости",
            R.id.category_lore to "Лор"
        )

        categories.forEach { (categoryId, categoryName) ->
            view.findViewById<TextView>(categoryId)?.setOnClickListener {

                // Подсвечиваем выбранную категорию
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
                        if (selected) Color.WHITE
                        else Color.rgb(170, 170, 170)
                    )
                }

                val gamesRecycler =
                    view.findViewById<RecyclerView>(R.id.gamesRecyclerView)

                val postsScroll =
                    view.findViewById<android.widget.ScrollView>(
                        R.id.postsScrollView
                    )

                when (categoryName) {
                    "Все" -> {
                        gamesRecycler?.visibility = View.GONE
                        postsScroll?.visibility = View.VISIBLE

                        postsManager.loadPosts(view, "Все")
                    }

                    "Игры" -> {
                        gamesRecycler?.visibility = View.VISIBLE
                        postsScroll?.visibility = View.GONE

                        loadGames(view)
                    }

                    "Гайды", "Новости", "Лор" -> {
                        gamesRecycler?.visibility = View.GONE
                        postsScroll?.visibility = View.VISIBLE

                        postsManager.loadPosts(view, categoryName)
                    }
                }
            }
        }
    }


    // Загружаем игры напрямую из Firestore

    // Загружаем игры вместе с количеством лайков из Firebase.

    private fun loadGames(view: View) {
        // Если каталог уже загружен, показываем сохранённые данные.
        val cached = cachedGames

        if (cached != null) {
            showGames(view, cached)
            return
        }

        // Не запускаем повторную загрузку, пока первая не закончилась.
        if (isLoadingGames) return

        isLoadingGames = true

        activity.lifecycleScope.launch {
            try {
                val games = withContext(Dispatchers.IO) {
                    repository.getGames()
                }

                cachedGames = games
                showGames(view, games)

                if (games.isEmpty()) {
                    Toast.makeText(
                        activity,
                        "Каталог игр пуст",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                android.util.Log.e(
                    "HomeManager",
                    "Ошибка загрузки игр из Firebase",
                    e
                )

                Toast.makeText(
                    activity,
                    "Не удалось загрузить каталог игр: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isLoadingGames = false
            }
        }
    }



    // Показываем игры в сетке из двух колонок
    private fun showGames(
        view: View,
        games: List<Game>
    ) {
        val recyclerView = view.findViewById<RecyclerView>(
            R.id.gamesRecyclerView
        ) ?: return

        recyclerView.layoutManager = GridLayoutManager(activity, 2)

        if (recyclerView.layoutManager == null) {
            recyclerView.layoutManager = GridLayoutManager(activity, 2)
        }

        recyclerView.adapter = GameAdapter(
            games,
            repository,
            activity
        ) { game ->
            onGameClick(game)
        }
    }
}
