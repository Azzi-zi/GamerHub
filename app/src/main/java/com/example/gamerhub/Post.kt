
package com.example.gamerhub

data class Post(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val game: String = "",
    val postType: String = "",
    val imageUrl: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val gameTags: List<String> = emptyList(),
    val topicTags: List<String> = emptyList(),
    val tags: List<String> = emptyList()
)
