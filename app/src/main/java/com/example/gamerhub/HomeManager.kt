
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
    private val onGameClick: (Game) -> Unit
) {

    // Настраиваем главную страницу
    fun setup(view: View) {
        setupHomeLogo(view)
        setupHomeCategories(view)
        loadGames(view)
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

    // Загружаем игры из основного источника
    private fun loadGames(view: View) {
        activity.lifecycleScope.launch {
            try {
                val games = withContext(Dispatchers.IO) {
                    repository.getGames()
                }

                showGames(view, games)

            } catch (e: Exception) {
                android.util.Log.e(
                    "HomeManager",
                    "Ошибка загрузки игр из Firebase",
                    e
                )

                val localGames = try {
                    withContext(Dispatchers.IO) {
                        repository.getLocalGames()
                    }
                } catch (localError: Exception) {
                    android.util.Log.e(
                        "HomeManager",
                        "Ошибка загрузки игр из Room",
                        localError
                    )
                    emptyList()
                }

                showGames(view, localGames)

                if (localGames.isEmpty()) {
                    Toast.makeText(
                        activity,
                        "Не удалось загрузить каталог игр",
                        Toast.LENGTH_LONG
                    ).show()
                }
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

        recyclerView.adapter = GameAdapter(
            games,
            repository,
            activity
        ) { game ->
            onGameClick(game)
        }
    }
}
