package com.example.gamerhub

// Модель данных одной публикации GamerHub.
//
// data class автоматически создаёт полезные методы:
// equals(), hashCode(), toString(), copy() и другие.
//
// Эта модель используется для передачи и хранения
// информации о публикациях, в том числе в Firestore.
data class Post(

    // Уникальный идентификатор публикации.
    // По умолчанию пустой, например до сохранения в Firestore.
    val id: String = "",

    // Заголовок публикации.
    val title: String = "",

    // Основной текст публикации.
    val content: String = "",

    // Название игры, к которой относится публикация.
    val game: String = "",

    // Тип публикации.
    // Например: статья, гайд, новость или другой формат.
    val postType: String = "",

    // Ссылка на изображение публикации.
    // Обычно здесь хранится URL изображения в облачном хранилище.
    val imageUrl: String = "",

    // UID автора из Firebase Authentication.
    // Позволяет определить, кому принадлежит публикация.
    val authorId: String = "",

    // Отображаемое имя автора.
    val authorName: String = "",

    // Список тегов с названиями игр.
    // Например: Minecraft, Terraria, CS2.
    val gameTags: List<String> = emptyList(),

    // Список тематических тегов.
    // Например: выживание, прохождение, советы.
    val topicTags: List<String> = emptyList(),

    // Общий список тегов публикации.
    // Может использоваться для поиска и фильтрации.
    val tags: List<String> = emptyList(),

    // Ссылка на аватар автора публикации.
    val authorAvatarUrl: String = ""
)