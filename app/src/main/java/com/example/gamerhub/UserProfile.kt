
package com.example.gamerhub

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfile(
    // UID уникален для каждого аккаунта Firebase
    @PrimaryKey
    val uid: String,

    val username: String = "PlayerOne",
    val tag: String = "@player_one",
    val bio: String = "Люблю игры 🎮"
)
