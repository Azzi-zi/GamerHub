package com.example.gamerhub

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

// Сущность Room, представляющая профиль пользователя.
// Данные этой модели будут храниться в локальной таблице user_profiles.
@Entity(tableName = "user_profiles")
data class UserProfile(

    // Уникальный идентификатор пользователя.
    // Используется как первичный ключ таблицы.
    // У каждого пользователя должен быть свой uid.
    @PrimaryKey
    val uid: String,

    // Имя пользователя, отображаемое в профиле.
    // Если имя не указано, используется PlayerOne.
    val username: String = "PlayerOne",

    // Уникальный тег пользователя.
    // Может использоваться для поиска и идентификации профиля.
    val tag: String = "@player_one",

    // Краткое описание пользователя.
    // Например, интересы, увлечения или любимые игры.
    val bio: String = "Люблю игры 🎮",

    // Роли пользователя в GamerHub.
    // Например: Геймер, Стример, Разработчик.
    // Хранятся в виде строки.
    //
    // defaultValue задаёт значение по умолчанию
    // на уровне структуры таблицы Room.
    @ColumnInfo(defaultValue = "'Геймер'")
    val roles: String = "Геймер",

    // URI аватара пользователя.
    // Может содержать локальный адрес изображения.
    // Если аватар не установлен, используется пустая строка.
    @ColumnInfo(defaultValue = "''")
    val avatarUri: String = ""
)