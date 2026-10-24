
package com.example.gamerhub

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "games")
data class Game(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val title: String,

    val genre: String,

    val image: String,

    val description: String,

    // Оставляем для совместимости со старым кодом.
    // Для новых лайков это поле больше не используется.
    var liked: Boolean = false

) {
    // ID документа игры в Firestore.
    @Ignore
    var firestoreId: String = ""

    // Общее количество лайков игры.
    @Ignore
    var likesCount: Int = 0

    // Поставил ли текущий пользователь лайк.
    @Ignore
    var isLikedByCurrentUser: Boolean = false
}
