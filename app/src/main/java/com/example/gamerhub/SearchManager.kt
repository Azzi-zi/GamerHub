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

// Менеджер отвечает за экран поиска игр.
//
// Он выполняет следующие задачи:
// 1. Загружает каталог игр.
// 2. Показывает популярные игры.
// 3. Выполняет поиск по названию.
// 4. Фильтрует игры по категориям.
// 5. Кэширует загруженные данные, чтобы не обращаться к базе повторно.
// 6. Обрабатывает нажатия на карточки игр.
class SearchManager(
    private val activity: MainActivity,
    private val repository: GameRepository,
    private val pageContainer: FrameLayout,
    private val onGameClick: (Game) -> Unit
) {

    // Кэш всего каталога игр.
    // Если данные уже загружены, повторно обращаться к базе не нужно.
    private var cachedGames: List<Game>? = null

    // Кэш восьми популярных игр.
    // Используется для первоначального отображения на экране поиска.
    private var cachedPopularGames: List<Game>? = null

    // Задача корутины, которая выполняет поиск.
    // Нужна, чтобы отменять предыдущий поиск при вводе нового текста.
    private var searchJob: Job? = null

    // Версия текущего поиска.
    // Помогает не показывать результаты устаревшего запроса.
    private var searchVersion = 0

    // Текущая выбранная категория.
    // По умолчанию отображаются все игры.
    private var selectedCategory = "Все"

    // Настройка экрана поиска.
    fun setup(view: View) {

        // Поле ввода названия игры.
        val searchInput =
            view.findViewById<EditText>(R.id.searchGamesInput)

        // Кнопка очистки поисковой строки.
        val clearButton =
            view.findViewById<ImageView>(R.id.clearSearchButton)

        // Текст над результатами поиска.
        val resultsTitle =
            view.findViewById<TextView>(R.id.searchResultsTitle)

        // Кнопки категорий.
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

        // Популярные поисковые запросы.
        val trendingMinecraft =
            view.findViewById<TextView>(R.id.trendingMinecraft)

        val trendingGenshin =
            view.findViewById<TextView>(R.id.trendingGenshin)

        val trendingGta =
            view.findViewById<TextView>(R.id.trendingGta)

        // RecyclerView, в котором будут отображаться найденные игры.
        val recyclerView =
            view.findViewById<RecyclerView>(R.id.searchGamesRecyclerView)

        // Размещаем карточки игр в две колонки.
        recyclerView.layoutManager = GridLayoutManager(activity, 2)

        // Отключаем самостоятельную прокрутку RecyclerView.
        // Это полезно, если он находится внутри другого прокручиваемого контейнера.
        recyclerView.isNestedScrollingEnabled = false

        // Загружаем игры для первоначального отображения.
        loadSearchGames(recyclerView, resultsTitle)

        // Выбирает категорию и обновляет список игр.
        fun selectCategory(category: String) {

            // Запоминаем выбранную категорию.
            selectedCategory = category

            // Обновляем внешний вид кнопок категорий.
            updateCategoryButtons(
                categoryAll,
                categoryAction,
                categoryRpg,
                categorySandbox,
                categoryOnline
            )

            // Показываем игры, подходящие под выбранную категорию
            // и текущий поисковый запрос.
            showFilteredGames(
                recyclerView,
                resultsTitle,
                searchInput.text.toString()
            )
        }

        // Обработчики нажатий на категории.

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

        // Отслеживаем изменения текста в поисковой строке.
        searchInput.addTextChangedListener(object : TextWatcher {

            // Вызывается перед изменением текста.
            // Здесь нам ничего делать не нужно.
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            // Вызывается после изменения текста.
            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {

                // Получаем введённый текст и убираем пробелы по краям.
                val query = s?.toString()?.trim().orEmpty()

                // Показываем кнопку очистки только тогда,
                // когда поисковая строка не пустая.
                clearButton.visibility =
                    if (query.isEmpty()) View.GONE else View.VISIBLE

                // Отменяем предыдущий поиск, если пользователь
                // продолжает вводить текст.
                searchJob?.cancel()

                // Увеличиваем номер текущего поиска.
                searchVersion++

                // Сохраняем номер именно этого запроса.
                val currentVersion = searchVersion

                // Если строка пустая, возвращаем стандартное отображение.
                if (query.isBlank()) {

                    resultsTitle.visibility = View.GONE

                    showFilteredGames(
                        recyclerView,
                        resultsTitle,
                        ""
                    )

                    return
                }

                // Показываем заголовок результатов поиска.
                resultsTitle.visibility = View.VISIBLE
                resultsTitle.text = "Поиск..."

                // Запускаем поиск в корутине, привязанной
                // к жизненному циклу MainActivity.
                searchJob = activity.lifecycleScope.launch {

                    // Небольшая задержка перед поиском.
                    // Она позволяет не запускать поиск после каждой буквы.
                    delay(250)

                    // Получаем каталог из кэша.
                    // Если кэша нет, загружаем игры из репозитория.
                    val games = cachedGames ?: getGamesSafely().also {
                        cachedGames = it
                    }

                    // Выполняем фильтрацию в фоновом потоке,
                    // чтобы не блокировать интерфейс приложения.
                    val filteredGames = withContext(Dispatchers.Default) {

                        filterGamesList(
                            games,
                            query,
                            selectedCategory
                        )
                    }

                    // Если за время поиска пользователь ввёл другой запрос,
                    // не показываем результаты старого поиска.
                    if (currentVersion != searchVersion) return@launch

                    // Отображаем найденные игры.
                    displayGames(recyclerView, filteredGames)

                    // Обновляем заголовок с количеством результатов.
                    resultsTitle.text =
                        if (filteredGames.isEmpty()) {
                            "Ничего не найдено"
                        } else {
                            "Найдено: ${filteredGames.size}"
                        }
                }
            }

            // Вызывается после полного изменения текста.
            // Дополнительные действия здесь не нужны.
            override fun afterTextChanged(s: Editable?) = Unit
        })

        // Очищаем поисковую строку при нажатии на крестик.
        clearButton.setOnClickListener {
            searchInput.text.clear()
        }

        // Популярный запрос Minecraft.
        trendingMinecraft.setOnClickListener {

            // Сбрасываем категорию на «Все».
            selectedCategory = "Все"

            updateCategoryButtons(
                categoryAll,
                categoryAction,
                categoryRpg,
                categorySandbox,
                categoryOnline
            )

            // Вставляем название игры в поисковую строку.
            searchInput.setText("Minecraft")
        }

        // Популярный запрос Genshin Impact.
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

        // Популярный запрос GTA.
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

    // Загружает игры для первоначального отображения на экране.
    private fun loadSearchGames(
        recyclerView: RecyclerView,
        resultsTitle: TextView
    ) {

        // Если список популярных игр уже находится в кэше,
        // сразу отображаем его.
        cachedPopularGames?.let { popularGames ->

            displayGames(recyclerView, popularGames)

            resultsTitle.visibility = View.GONE

            return
        }

        // Если весь каталог уже загружен,
        // вычисляем популярные игры без обращения к базе данных.
        cachedGames?.let { games ->

            val popularGames = getPopularGames(games)

            cachedPopularGames = popularGames

            displayGames(recyclerView, popularGames)

            resultsTitle.visibility = View.GONE

            return
        }

        // Если кэша ещё нет, загружаем каталог.
        activity.lifecycleScope.launch {

            try {

                // Получаем список игр.
                val games = getGamesSafely()

                // Сохраняем каталог в кэш.
                cachedGames = games

                // Получаем восемь наиболее популярных игр.
                val popularGames = getPopularGames(games)

                // Сохраняем популярные игры отдельно.
                cachedPopularGames = popularGames

                // Отображаем популярные игры.
                displayGames(recyclerView, popularGames)

                resultsTitle.visibility = View.GONE

            } catch (e: Exception) {

                // Записываем ошибку в Logcat.
                Log.e(
                    "GamerHubSearch",
                    "Ошибка загрузки каталога",
                    e
                )

                // Показываем сообщение об ошибке.
                resultsTitle.visibility = View.VISIBLE
                resultsTitle.text = "Не удалось загрузить игры"
            }
        }
    }

    // Получаем восемь игр с наибольшим количеством лайков.
    private fun getPopularGames(games: List<Game>): List<Game> {

        return games

            // Сначала сортируем игры по количеству лайков
            // от большего к меньшему.
            .sortedWith(
                compareByDescending<Game> { it.likesCount }

                    // Если количество лайков одинаковое,
                    // сортируем игры по названию.
                    .thenBy { it.title.lowercase() }
            )

            // Оставляем только первые восемь игр.
            .take(8)
    }

    // Показывает игры с учётом поискового запроса и категории.
    private fun showFilteredGames(
        recyclerView: RecyclerView,
        resultsTitle: TextView,
        query: String
    ) {

        // Получаем каталог из кэша.
        val games = cachedGames

        // Если каталог ещё не загружен,
        // запускаем его загрузку.
        if (games == null) {

            loadSearchGames(recyclerView, resultsTitle)

            return
        }

        // Если поиск пустой и выбрана категория «Все»,
        // показываем только восемь популярных игр.
        if (query.isBlank() && selectedCategory == "Все") {

            // Используем готовый кэш популярных игр,
            // либо рассчитываем его из общего каталога.
            val popularGames = cachedPopularGames ?: getPopularGames(games).also {
                cachedPopularGames = it
            }

            displayGames(recyclerView, popularGames)

            resultsTitle.visibility = View.GONE

            return
        }

        // Фильтруем весь каталог по запросу и категории.
        val filteredGames = filterGamesList(
            games,
            query,
            selectedCategory
        )

        // Отображаем результат фильтрации.
        displayGames(recyclerView, filteredGames)

        // Если поисковая строка пустая,
        // заголовок результатов не нужен.
        if (query.isBlank()) {

            resultsTitle.visibility = View.GONE

        } else {

            // Если пользователь что-то ищет,
            // показываем количество найденных игр.
            resultsTitle.visibility = View.VISIBLE

            resultsTitle.text =
                if (filteredGames.isEmpty()) {
                    "Ничего не найдено"
                } else {
                    "Найдено: ${filteredGames.size}"
                }
        }
    }

    // Фильтрует список игр по названию и жанру.
    private fun filterGamesList(
        games: List<Game>,
        query: String,
        category: String
    ): List<Game> {

        return games.filter { game ->

            // Проверяем, подходит ли игра под поисковый запрос.
            // Если запрос пустой, подходят все игры.
            val matchesQuery =
                query.isBlank() ||
                        game.title.contains(query, ignoreCase = true)

            // Проверяем, подходит ли игра под выбранную категорию.
            // Если выбрана категория «Все», подходят все жанры.
            val matchesCategory =
                category == "Все" ||
                        game.genre.contains(category, ignoreCase = true)

            // Игра должна соответствовать обоим условиям.
            matchesQuery && matchesCategory
        }
    }

    // Отображает переданный список игр в RecyclerView.
    private fun displayGames(
        recyclerView: RecyclerView,
        games: List<Game>
    ) {

        // Создаём адаптер для карточек игр.
        // GameAdapter отвечает за заполнение карточек данными.
        recyclerView.adapter = GameAdapter(
            games,
            repository,
            activity
        ) { game ->

            // Передаём выбранную игру в обработчик,
            // который был передан при создании SearchManager.
            onGameClick(game)
        }
    }

    // Обновляет внешний вид кнопок категорий.
    private fun updateCategoryButtons(
        categoryAll: TextView,
        categoryAction: TextView,
        categoryRpg: TextView,
        categorySandbox: TextView,
        categoryOnline: TextView
    ) {

        // Связываем название категории с соответствующей кнопкой.
        val buttons = mapOf(
            "Все" to categoryAll,
            "Action" to categoryAction,
            "RPG" to categoryRpg,
            "Sandbox" to categorySandbox,
            "Online" to categoryOnline
        )

        // Проходим по всем кнопкам категорий.
        buttons.forEach { (category, button) ->

            // Проверяем, выбрана ли текущая категория.
            val selected = category == selectedCategory

            // Меняем цвет текста в зависимости от выбора.
            button.setTextColor(
                if (selected) Color.WHITE else Color.LTGRAY
            )

            // Меняем фон выбранной и невыбранной кнопки.
            button.setBackgroundResource(
                if (selected) {
                    R.drawable.bg_category_active
                } else {
                    R.drawable.bg_category
                }
            )
        }
    }

    // Безопасно загружает игры из репозитория.
    //
    // Сначала пытается получить данные из основного источника.
    // Если это не получается, пробует загрузить локальные игры.
    private suspend fun getGamesSafely(): List<Game> {

        return try {

            // Работа с базой данных выполняется в IO-потоке.
            withContext(Dispatchers.IO) {

                repository.getGames()
            }

        } catch (e: Exception) {

            // Записываем ошибку загрузки основного каталога.
            Log.e(
                "GamerHubSearch",
                "Ошибка загрузки игр из Firestore",
                e
            )

            try {

                // Если основной источник недоступен,
                // пробуем получить игры из локальной базы.
                withContext(Dispatchers.IO) {

                    repository.getLocalGames()
                }

            } catch (localException: Exception) {

                // Записываем ошибку локальной загрузки.
                Log.e(
                    "GamerHubSearch",
                    "Ошибка загрузки локальных игр",
                    localException
                )

                // Если оба источника недоступны,
                // возвращаем пустой список.
                emptyList()
            }
        }
    }
}