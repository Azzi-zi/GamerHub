package com.example.gamerhub

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface GameDao {

    @Query("SELECT * FROM games")
    suspend fun getAllGames(): List<Game>

    @Query("SELECT COUNT(*) FROM games")
    suspend fun getGameCount(): Int

    @Query("""
        SELECT * FROM games
        WHERE title LIKE '%' || :query || '%'
    """)
    suspend fun searchGames(query: String): List<Game>

    @Insert
    suspend fun insertGame(game: Game)

    @Insert
    suspend fun insertGames(games: List<Game>)

    @Query("UPDATE games SET liked = :liked WHERE id = :gameId")
    suspend fun updateLiked(gameId: Int, liked: Boolean)

    // Получаем профиль конкретного аккаунта
    @Query("SELECT * FROM user_profiles WHERE uid = :uid")
    suspend fun getUserProfile(uid: String): UserProfile?

    // Сохраняем профиль; если UID уже есть, обновляем его
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfile)
}