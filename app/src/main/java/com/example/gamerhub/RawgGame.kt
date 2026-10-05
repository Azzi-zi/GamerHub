package com.example.gamerhub

data class RawgGame(
    val id: Int,
    val name: String,
    val released: String?,
    val rating: Double,
    val background_image: String?,
    val genres: List<RawgGenre>?
)

data class RawgGenre(
    val id: Int,
    val name: String
)