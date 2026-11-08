package com.example.gamerhub

// Intent используется для перехода между Activity.
import android.content.Intent

// Color позволяет задавать цвета текста.
import android.graphics.Color

// Editable используется при изменении текста в поле поиска.
import android.text.Editable

// SpannableString позволяет задавать разные стили отдельным частям текста.
import android.text.SpannableString

// Spanned содержит настройки отображения форматированного текста.
import android.text.Spanned

// TextWatcher отслеживает изменения текста в поле поиска.
import android.text.TextWatcher

// ForegroundColorSpan задаёт цвет отдельной части текста.
import android.text.style.ForegroundColorSpan

// View — базовый класс для элементов интерфейса Android.
import android.view.View

// EditText — поле ввода текста.
import android.widget.EditText

// ScrollView — прокручиваемая область интерфейса.
import android.widget.ScrollView

// TextView — элемент для отображения текста.
import android.widget.TextView

// Toast — небольшое всплывающее сообщение.
import android.widget.Toast

// AppCompatActivity — базовый класс Activity из библиотеки AndroidX.
import androidx.appcompat.app.AppCompatActivity

// lifecycleScope позволяет запускать корутины,
// жизненный цикл которых связан с Activity.
import androidx.lifecycle.lifecycleScope

// GridLayoutManager размещает элементы RecyclerView в виде сетки.
import androidx.recyclerview.widget.GridLayoutManager

// RecyclerView используется для отображения списка игр.
import androidx.recyclerview.widget.RecyclerView

// Dispatchers.IO предназначен для операций ввода-вывода,
// например загрузки данных из Firebase.
import kotlinx.coroutines.Dispatchers

// launch запускает корутину.
import kotlinx.coroutines.launch

// withContext позволяет переключить выполнение на другой поток.
import kotlinx.coroutines.withContext


// HomeManager управляет главной страницей приложения GamerHub.
//
// Здесь находятся:
// - настройка логотипа;
// - настройка кнопки уведомлений;
// - переключение категорий;
// - поиск игр и публикаций;
// - загрузка каталога игр;
// - отображение игр в RecyclerView.
class HomeManager(

    // Activity, в которой отображается главная страница.
    private val activity: AppCompatActivity,

    // Репозиторий отвечает за получение данных об играх.
    private val repository: GameRepository,

    // Менеджер публикаций отвечает за загрузку и отображение постов.
    private val postsManager: PostsManager,

    // Функция обратного вызова для открытия выбранной игры.
    private val onGameClick: (Game) -> Unit,

    // Функция обратного вызова для открытия страницы уведомлений.
    private val onNotificationsClick: () -> Unit

) {

    // Локальный кэш игр.
    // Если игры уже загружались, повторно обращаться к Firebase не потребуется.
    //
    // null означает, что каталог ещё не загружен.
    private var cachedGames: List<Game>? = null

    // Флаг показывает, выполняется ли сейчас загрузка каталога.
    // Нужен, чтобы не запускать несколько одинаковых запросов одновременно.
    private var isLoadingGames = false

    // Название выбранной категории.
    // При первом открытии главной страницы отображается категория «Все».
    private var selectedCategory = "Все"

    // Текущий поисковый запрос.
    // Изначально строка пустая, поэтому отображаются все элементы.
    private var currentQuery = ""


    // Основной метод настройки главной страницы.
    // Вызывается из Activity после создания или отображения главного экрана.
    fun setup(view: View) {

        // Настраиваем логотип GamerHub.
        setupHomeLogo(view)

        // Настраиваем кнопку перехода к уведомлениям.
        setupNotificationsButton(view)

        // Настраиваем переключение категорий.
        setupHomeCategories(view)

        // Настраиваем строку поиска.
        setupSearch(view)

        // Загружаем каталог игр.
        loadGames(view)
    }


    // Настраивает кнопку уведомлений.
    private fun setupNotificationsButton(view: View) {

        // Находим элемент с ID notifications.
        // Если элемента нет в XML, безопасно пропускаем настройку.
        view.findViewById<View>(R.id.notifications)

            // При нажатии вызываем функцию,
            // которую передала Activity при создании HomeManager.
            ?.setOnClickListener {
                onNotificationsClick()
            }
    }


    // Настраивает логотип GamerHub.
    private fun setupHomeLogo(view: View) {

        // Находим TextView с логотипом.
        // Если элемент отсутствует, прекращаем выполнение метода.
        val title = view.findViewById<TextView>(R.id.logoText)
            ?: return

        // Текст логотипа.
        val text = "GamerHub"

        // Создаём форматированную строку,
        // чтобы можно было раскрасить разные части логотипа.
        val spannable = SpannableString(text)

        // Первые пять символов — Gamer.
        // Для них устанавливаем белый цвет.
        spannable.setSpan(
            ForegroundColorSpan(Color.WHITE),
            0,
            5,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Остальная часть логотипа — Hub.
        // Для неё устанавливаем фиолетовый цвет.
        spannable.setSpan(
            ForegroundColorSpan(Color.rgb(170, 80, 255)),
            5,
            text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Устанавливаем форматированный текст в TextView.
        title.text = spannable

        // Обрабатываем нажатие на логотип.
        title.setOnClickListener {

            // Открываем RegisterActivity.
            activity.startActivity(
                Intent(activity, RegisterActivity::class.java)
            )
        }
    }


    // Настраивает кнопки категорий на главной странице.
    private fun setupHomeCategories(view: View) {

        // Создаём соответствие между ID кнопок в XML
        // и названиями категорий.
        //
        // mapOf — неизменяемая карта пар «ключ — значение».
        //
        // Например:
        // R.id.category_games -> "Игры".
        val categories = mapOf(
            R.id.category_all to "Все",
            R.id.category_games to "Игры",
            R.id.category_guides to "Гайды",
            R.id.category_news to "Новости",
            R.id.category_lore to "Лор"
        )

        // Перебираем все категории.
        //
        // categoryId — ID кнопки из XML.
        // categoryName — название соответствующей категории.
        categories.forEach { (categoryId, categoryName) ->

            // Находим TextView, соответствующий текущей категории.
            view.findViewById<TextView>(categoryId)

                // Если элемент найден, назначаем обработчик нажатия.
                ?.setOnClickListener {

                    // Запоминаем название выбранной категории.
                    selectedCategory = categoryName

                    // Повторно перебираем все категории,
                    // чтобы обновить внешний вид кнопок.
                    categories.forEach categoryLoop@{ (id, _) ->

                        // Находим кнопку текущей категории.
                        // Если она отсутствует, пропускаем только эту итерацию.
                        val category = view.findViewById<TextView>(id)
                            ?: return@categoryLoop

                        // Проверяем, является ли кнопка выбранной.
                        val selected = id == categoryId

                        // Меняем фон кнопки.
                        //
                        // Для выбранной категории используем активный фон.
                        // Для остальных — обычный фон.
                        category.setBackgroundResource(
                            if (selected) {
                                R.drawable.bg_category_active
                            } else {
                                R.drawable.bg_category
                            }
                        )

                        // Меняем цвет текста.
                        //
                        // Выбранная категория — белая.
                        // Остальные категории — серые.
                        category.setTextColor(
                            if (selected) {
                                Color.WHITE
                            } else {
                                Color.rgb(170, 170, 170)
                            }
                        )
                    }

                    // Обновляем содержимое главной страницы
                    // в соответствии с выбранной категорией.
                    updateContent(view)
                }
        }

        // При первом открытии страницы обновляем содержимое
        // с выбранной по умолчанию категорией «Все».
        updateContent(view)
    }


    // Настраивает поиск на главной странице.
    private fun setupSearch(view: View) {

        // Находим поле поиска в XML.
        // Если его нет, прекращаем настройку.
        val searchInput = view.findViewById<EditText>(R.id.search)
            ?: return

        // Добавляем наблюдатель за изменениями текста.
        searchInput.addTextChangedListener(object : TextWatcher {

            // Вызывается перед изменением текста.
            // В данном случае дополнительная обработка не нужна.
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            // Вызывается во время изменения текста.
            // Здесь также ничего не выполняем.
            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) = Unit

            // Вызывается после изменения текста.
            override fun afterTextChanged(s: Editable?) {

                // Получаем введённый текст.
                // trim() удаляет пробелы в начале и конце строки.
                // orEmpty() заменяет null на пустую строку.
                currentQuery = s?.toString()?.trim().orEmpty()

                // Обновляем содержимое страницы с учётом нового запроса.
                updateContent(view)
            }
        })
    }


    // Определяет, что именно показывать на главной странице:
    // игры или публикации.
    private fun updateContent(view: View) {

        // Находим RecyclerView, в котором отображаются игры.
        // Если элемента нет, прекращаем выполнение метода.
        val gamesRecycler = view.findViewById<RecyclerView>(
            R.id.gamesRecyclerView
        ) ?: return

        // Находим ScrollView, содержащий публикации.
        val postsScroll = view.findViewById<ScrollView>(
            R.id.postsScrollView
        ) ?: return

        // Проверяем, выбрана ли категория «Игры».
        if (selectedCategory == "Игры") {

            // Показываем список игр.
            gamesRecycler.visibility = View.VISIBLE

            // Скрываем область публикаций.
            postsScroll.visibility = View.GONE

            // Получаем игры из локального кэша.
            val games = cachedGames

            // Если игры уже загружены, сразу отображаем их.
            if (games != null) {
                showFilteredGames(view, games)
            } else {

                // Если кэша нет, запускаем загрузку каталога.
                loadGames(view)
            }

        } else {

            // Для остальных категорий скрываем каталог игр.
            gamesRecycler.visibility = View.GONE

            // Показываем область публикаций.
            postsScroll.visibility = View.VISIBLE

            // Загружаем публикации через PostsManager.
            postsManager.loadPosts(
                view = view,

                // Передаём выбранную категорию.
                category = if (selectedCategory == "Все") {
                    "Все"
                } else {
                    selectedCategory
                },

                // Передаём текущий поисковый запрос.
                query = currentQuery
            )
        }
    }


    // Загружает каталог игр из репозитория.
    private fun loadGames(view: View) {

        // Если игры уже есть в кэше,
        // повторная загрузка не требуется.
        cachedGames?.let { games ->

            // Сразу отображаем сохранённый список.
            showFilteredGames(view, games)

            // Завершаем метод.
            return
        }

        // Если загрузка уже выполняется,
        // не запускаем ещё одну.
        if (isLoadingGames) return

        // Устанавливаем флаг загрузки.
        isLoadingGames = true

        // Запускаем корутину, связанную с жизненным циклом Activity.
        // При уничтожении Activity её lifecycleScope будет отменён.
        activity.lifecycleScope.launch {

            try {

                // Выполняем загрузку данных в фоновом потоке.
                val games = withContext(Dispatchers.IO) {

                    try {

                        // Сначала пытаемся получить игры через репозиторий.
                        repository.getGames()

                    } catch (e: Exception) {

                        // Если загрузка из Firebase завершилась ошибкой,
                        // записываем подробную информацию в Logcat.
                        android.util.Log.e(
                            "HomeManager",
                            "Не удалось загрузить игры из Firebase",
                            e
                        )

                        // В качестве запасного варианта
                        // пытаемся получить игры из локальной базы Room.
                        repository.getLocalGames()
                    }
                }

                // Сохраняем полученный список в кэш.
                cachedGames = games

                // Показываем игры только в том случае,
                // если пользователь сейчас находится в категории «Игры».
                //
                // Это предотвращает отображение RecyclerView,
                // если пользователь успел переключиться на публикации.
                if (selectedCategory == "Игры") {
                    showFilteredGames(view, games)
                }

                // Если список пустой, показываем уведомление.
                if (games.isEmpty()) {
                    Toast.makeText(
                        activity,
                        "Каталог игр пуст",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {

                // Записываем ошибку загрузки в Logcat.
                android.util.Log.e(
                    "HomeManager",
                    "Ошибка загрузки каталога игр",
                    e
                )

                // Показываем пользователю сообщение об ошибке.
                Toast.makeText(
                    activity,
                    "Не удалось загрузить каталог игр",
                    Toast.LENGTH_LONG
                ).show()

            } finally {

                // Снимаем флаг загрузки независимо от результата.
                // Благодаря этому можно будет повторить загрузку.
                isLoadingGames = false
            }
        }
    }


    // Фильтрует игры по поисковому запросу
    // и отображает результат в RecyclerView.
    private fun showFilteredGames(
        view: View,
        games: List<Game>
    ) {

        // Находим RecyclerView для отображения карточек игр.
        val recyclerView = view.findViewById<RecyclerView>(
            R.id.gamesRecyclerView
        ) ?: return

        // Получаем текущий поисковый запрос.
        val query = currentQuery

        // Формируем отфильтрованный список.
        val filteredGames = if (query.isBlank()) {

            // Если запрос пустой или состоит из пробелов,
            // отображаем все игры.
            games

        } else {

            // Иначе оставляем только игры,
            // в которых найдено совпадение с запросом.
            games.filter { game ->

                // Проверяем название игры.
                game.title.contains(query, ignoreCase = true) ||

                        // Проверяем жанр игры.
                        game.genre.contains(query, ignoreCase = true) ||

                        // Проверяем описание игры.
                        game.description.contains(query, ignoreCase = true)
            }
        }

        // Устанавливаем GridLayoutManager с двумя колонками.
        // Карточки игр будут располагаться в виде сетки 2 × N.
        recyclerView.layoutManager = GridLayoutManager(activity, 2)

        // Создаём адаптер для отображения отфильтрованных игр.
        recyclerView.adapter = GameAdapter(
            filteredGames,   // Список игр, которые нужно показать.
            repository,      // Репозиторий для работы с лайками.
            activity         // Activity, необходимая адаптеру для работы с интерфейсом.
        ) { game ->

            // Передаём выбранную игру в Activity.
            // Дальнейшее открытие страницы игры выполняет переданная функция.
            onGameClick(game)
        }

        // Если после фильтрации ничего не осталось,
        // сообщаем пользователю об отсутствии результатов.
        if (filteredGames.isEmpty()) {
            Toast.makeText(
                activity,
                "Игры по запросу «$query» не найдены",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}