package com.example.gamerhub

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Game::class],
    version = 3,
    exportSchema = false
)
abstract class GamerHubDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
}