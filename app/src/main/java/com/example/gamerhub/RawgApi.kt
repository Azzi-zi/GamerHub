package com.example.gamerhub

import retrofit2.http.GET
import retrofit2.http.Query

interface RawgApi {

    @GET("games")
    suspend fun searchGames(
        @Query("key") apiKey: String,
        @Query("search") search: String
    ): RawgResponse
}