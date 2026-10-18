package com.example.gamerhub

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey
    val uid: String,

    val username: String = "PlayerOne",
    val tag: String = "@player_one",
    val bio: String = "Люблю игры 🎮",

    @ColumnInfo(defaultValue = "'Геймер'")
    val roles: String = "Геймер",

    @ColumnInfo(defaultValue = "''")
    val avatarUri: String = ""
)