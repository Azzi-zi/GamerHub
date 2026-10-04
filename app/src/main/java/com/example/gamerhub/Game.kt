package com.example.gamerhub

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "games")
data class Game(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val genre: String,
    val image: String,
    val description: String
)