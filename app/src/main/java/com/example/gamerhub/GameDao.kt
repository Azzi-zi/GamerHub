package com.example.gamerhub

import androidx.room.Dao
import androidx.room.Insert
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
    suspend fun updateLiked(
        gameId: Int,
        liked: Boolean
    )
    @Query("SELECT * FROM user_profiles WHERE id = 1")
    suspend fun getUserProfile(): UserProfile?

    @Insert
    suspend fun insertUserProfile(profile: UserProfile)

    @Update
    suspend fun updateUserProfile(profile: UserProfile)
}