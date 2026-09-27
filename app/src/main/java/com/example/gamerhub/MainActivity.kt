package com.example.gamerhub

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import android.graphics.Color

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan

import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // Загружаем основной контейнер приложения
        setContentView(R.layout.activity_main)


        // ========================================
        // 1. Контейнер для страниц
        // ========================================

        val pageContainer = findViewById<FrameLayout>(
            R.id.pageContainer
        )


        // ========================================
        // 2. Список игр
        // ========================================

        val games = listOf(

            Game(
                title = "Minecraft",
                genre = "• Sandbox\n• Open World",
                image = R.drawable.mincraft
            ),

            Game(
                title = "World of Tanks",
                genre = "• Tanks\n• Tactic",
                image = R.drawable.world_of_tanks
            ),

            Game(
                title = "Baldur's Gate 3",
                genre = "• RPG\n• Choices Matter",
                image = R.drawable.bal_gate
            ),

            Game(
                title = "GTA VI",
                genre = "• Action\n• Open World",
                image = R.drawable.gta
            ),

            Game(
                title = "Genshin Impact",
                genre = "• RPG\n• Open World",
                image = R.drawable.genshin_impact
            ),

            Game(
                title = "Portal 2",
                genre = "• Puzzle\n• Adventure",
                image = R.drawable.portal
            ),

            Game(
                title = "Wuthering Waves",
                genre = "• RPG\n• Open World",
                image = R.drawable.wuwa
            )
        )


        // ========================================
        // 3. Обычное переключение страниц
        // ========================================

        fun openPage(layoutId: Int) {

            // Удаляем предыдущую страницу
            pageContainer.removeAllViews()

            // Загружаем XML выбранной страницы
            val page = LayoutInflater.from(this)
                .inflate(
                    layoutId,
                    pageContainer,
                    false
                )

            // Добавляем страницу в контейнер
            pageContainer.addView(page)
        }


        // ========================================
        // 4. Открытие главной страницы
        // ========================================

        fun openHomePage() {

            // Удаляем предыдущую страницу
            pageContainer.removeAllViews()

            // Загружаем screen_home.xml
            val homeView = LayoutInflater.from(this)
                .inflate(
                    R.layout.screen_home,
                    pageContainer,
                    false
                )

            // Добавляем главную в контейнер
            pageContainer.addView(homeView)


            // ------------------------------------
            // Логотип GamerHub
            // ------------------------------------

            // Ищем логотип именно внутри Home
            val logoText = homeView.findViewById<TextView>(
                R.id.logoText
            )

            // Создаём строку GamerHub
            val logo = SpannableString("GamerHub")

            // Gamer — белый
            logo.setSpan(
                ForegroundColorSpan(Color.WHITE),
                0,
                5,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // Hub — фиолетовый
            logo.setSpan(
                ForegroundColorSpan(
                    Color.rgb(145, 90, 255)
                ),
                5,
                8,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // Применяем оформление
            logoText.text = logo


            // ------------------------------------
            // RecyclerView с играми
            // ------------------------------------

            // Находим список игр внутри Home
            val recyclerView = homeView.findViewById<RecyclerView>(
                R.id.gamesRecyclerView
            )

            // Создаём сетку из двух колонок
            recyclerView.layoutManager = GridLayoutManager(
                this,
                2
            )

            // Передаём игры адаптеру
            recyclerView.adapter = GameAdapter(games)
        }


        // ========================================
        // 5. Подсветка активной кнопки
        // ========================================

        fun setActiveNav(activeId: Int) {

            // Все пункты нижней навигации
            val navItems = listOf(
                R.id.navHome,
                R.id.navSearch,
                R.id.navCreate,
                R.id.navChats,
                R.id.navProfile
            )

            // Фиолетовый и серый цвета
            val activeColor = Color.rgb(
                139, 92, 246
            )

            val inactiveColor = Color.rgb(
                119, 119, 128
            )

            // Перебираем все кнопки
            for (id in navItems) {

                // Находим контейнер кнопки
                val item = findViewById<LinearLayout>(
                    id
                )

                // Находим иконку
                val icon = item.getChildAt(0) as ImageView

                // Находим подпись
                val text = item.getChildAt(1) as TextView

                // Определяем, активна ли кнопка
                val color = if (id == activeId) {
                    activeColor
                } else {
                    inactiveColor
                }

                // Меняем цвет текста
                text.setTextColor(color)

                // Меняем цвет иконки
                icon.setColorFilter(color)
            }
        }


        // ========================================
        // 6. Нижняя навигация
        // ========================================

        // Главная
        findViewById<LinearLayout>(
            R.id.navHome
        ).setOnClickListener {

            openHomePage()

            setActiveNav(R.id.navHome)
        }


        // Поиск
        findViewById<LinearLayout>(
            R.id.navSearch
        ).setOnClickListener {

            openPage(R.layout.screen_search)

            setActiveNav(R.id.navSearch)
        }


        // Создать
        findViewById<LinearLayout>(
            R.id.navCreate
        ).setOnClickListener {

            openPage(R.layout.screen_create)

            setActiveNav(R.id.navCreate)
        }


        // Чаты
        findViewById<LinearLayout>(
            R.id.navChats
        ).setOnClickListener {

            openPage(R.layout.screen_chats)

            setActiveNav(R.id.navChats)
        }


        // Профиль
        findViewById<LinearLayout>(
            R.id.navProfile
        ).setOnClickListener {

            openPage(R.layout.screen_profile)

            setActiveNav(R.id.navProfile)
        }


        // ========================================
        // 7. Начальная страница
        // ========================================

        // При запуске открываем Home
        openHomePage()

        // Подсвечиваем Главную
        setActiveNav(R.id.navHome)


        // ========================================
        // 8. Системные отступы
        // ========================================

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
    }
}