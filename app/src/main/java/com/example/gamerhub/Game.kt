package com.example.gamerhub

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Ignore
@Entity(tableName = "games")
data class Game(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val title: String,

    val genre: String,

    val image: String,

    val description: String,

    var liked: Boolean = false
){
    // ID документа в Firestore.
    // @Ignore сообщает Room, что это поле не нужно сохранять в таблицу.
    @Ignore
    var firestoreId: String = ""
}