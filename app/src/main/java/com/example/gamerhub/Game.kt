package com.example.gamerhub

// Импортируем аннотацию для обозначения класса как таблицы Room.
import androidx.room.Entity

// Импортируем аннотацию для полей, которые Room не должен сохранять в базу данных.
import androidx.room.Ignore

// Импортируем аннотацию для обозначения первичного ключа.
import androidx.room.PrimaryKey

// Объявляем сущность Game — она соответствует таблице "games" в базе Room.
@Entity(tableName = "games")
data class Game(

    // Первичный ключ записи в локальной базе данных.
    // Значение будет автоматически увеличиваться при добавлении новых игр.
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // Название игры.
    val title: String,

    // Жанр игры.
    val genre: String,

    // Путь или URL изображения игры.
    val image: String,

    // Описание игры.
    val description: String,

    // Оставляем поле для совместимости со старым кодом.
    // Для новой системы лайков это поле больше не используется.
    var liked: Boolean = false

) {
    // ID документа игры в Firestore.
    // Аннотация @Ignore указывает Room не сохранять это поле в локальную таблицу.
    @Ignore
    var firestoreId: String = ""

    // Общее количество лайков, поставленных игре.
    // Это поле используется в оперативной памяти, но не сохраняется в Room.
    @Ignore
    var likesCount: Int = 0

    // Показывает, поставил ли текущий пользователь лайк этой игре.
    // Также не сохраняется в локальную базу данных Room.
    @Ignore
    var isLikedByCurrentUser: Boolean = false
}