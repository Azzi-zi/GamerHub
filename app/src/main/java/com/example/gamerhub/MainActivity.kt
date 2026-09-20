package com.example.gamerhub

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.FrameLayout
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
        setContentView(R.layout.activity_main)

        // Находим контейнер для страниц
        val pageContainer = findViewById<FrameLayout>(
            R.id.pageContainer
        )

        // Загружаем главную страницу
        val homeView = LayoutInflater.from(this)
            .inflate(R.layout.screen_home, pageContainer, false)

        // Добавляем главную страницу в контейнер
        pageContainer.addView(homeView)

        // Список игр
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

        // Ищем RecyclerView уже внутри Home
        val recyclerView = homeView.findViewById<RecyclerView>(
            R.id.gamesRecyclerView
        )

        recyclerView.layoutManager = GridLayoutManager(
            this,
            2
        )

        recyclerView.adapter = GameAdapter(games)

        // Системные отступы
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