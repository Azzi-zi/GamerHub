package com.example.gamerhub

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

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
}