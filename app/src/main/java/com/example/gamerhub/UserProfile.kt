
package com.example.gamerhub

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey
    val id: Int = 1,

    val username: String = "PlayerOne",
    val tag: String = "@player_one",
    val bio: String = "Люблю игры 🎮"
)
