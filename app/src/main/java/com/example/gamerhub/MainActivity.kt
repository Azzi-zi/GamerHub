package com.example.gamerhub

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }
        val logoText = findViewById<TextView>(R.id.logoText)

        val logo = SpannableString("GamerHub")
        logo.setSpan(
            ForegroundColorSpan(android.graphics.Color.rgb(145,90,225)),
            5,
            8,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        logoText.text = logo


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

        val recyclerView = findViewById<RecyclerView>(
            R.id.gamesRecyclerView
        )

        recyclerView.layoutManager = GridLayoutManager(
            this,
            2
        )

        recyclerView.adapter = GameAdapter(games)
    }
}