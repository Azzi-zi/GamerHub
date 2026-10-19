
package com.example.gamerhub

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GameRepository(
    private val gameDao: GameDao
) {

    // Подключаем коллекцию игр в Firestore
    private val firestore = FirebaseFirestore.getInstance()
    private val gamesCollection = firestore.collection("games")

    // Загружаем игры из Firebase
    suspend fun getGames(): List<Game> = withContext(Dispatchers.IO) {

        val snapshot = Tasks.await(gamesCollection.get())

        snapshot.documents.mapNotNull { document ->

            val title = document.getString("title") ?: return@mapNotNull null

            Game(
                id = document.getLong("id")?.toInt()
                    ?: document.id.hashCode(),
                title = title,
                genre = document.getString("genre") ?: "Неизвестный жанр",
                image = document.getString("image") ?: "",
                description = document.getString("description") ?: "",
                liked = document.getBoolean("liked") ?: false
            ).apply {
                // Запоминаем настоящий ID документа Firebase
                firestoreId = document.id
            }
        }
    }
    // Резервная загрузка из локальной базы Room
    suspend fun getLocalGames(): List<Game> {
        return gameDao.getAllGames()
    }

    // Создаём начальные документы, только если их ещё нет
    suspend fun loadInitialGames() = withContext(Dispatchers.IO) {

        val initialGames = listOf(
            "minecraft" to Game(
                title = "Minecraft",
                genre = "Sandbox • Open World",
                image = "mincraft",
                description = "Песочница с огромным открытым миром, где можно исследовать мир, добывать ресурсы и строить сооружения."
            ),

            "world_of_tanks" to Game(
                title = "World of Tanks",
                genre = "Online • Action",
                image = "world_of_tanks",
                description = "Онлайн-игра о танковых сражениях, в которой игроки управляют боевыми машинами."
            ),

            "baldurs_gate_3" to Game(
                title = "Baldur's Gate 3",
                genre = "RPG • Adventure",
                image = "bal_gate",
                description = "Ролевая игра с исследованием мира, диалогами и решениями, влияющими на приключение."
            ),

            "gta_vi" to Game(
                title = "GTA VI",
                genre = "Action • Open World",
                image = "gta",
                description = "Приключенческая игра с открытым миром в криминальном сеттинге."
            ),

            "genshin_impact" to Game(
                title = "Genshin Impact",
                genre = "RPG • Open World",
                image = "genshin_impact",
                description = "Ролевая игра с открытым миром, исследованием локаций и игровыми персонажами."
            ),

            "portal_2" to Game(
                title = "Portal 2",
                genre = "Puzzle • Adventure",
                image = "portal",
                description = "Головоломка от первого лица, в которой портальная пушка помогает решать пространственные задачи."
            ),

            "wuthering_waves" to Game(
                title = "Wuthering Waves",
                genre = "RPG • Action",
                image = "wuwa",
                description = "Экшен-RPG с исследованием мира и динамическими сражениями."
            )
        )

        initialGames.forEachIndexed { index, entry ->

            val documentId = entry.first
            val game = entry.second
            val document = gamesCollection.document(documentId)
            val snapshot = Tasks.await(document.get())

            if (snapshot.exists()) {
                // Документ уже есть: не перезаписываем его данные.
                // Добавляем только порядковый ID, если его нет.
                if (!snapshot.contains("id")) {
                    Tasks.await(
                        document.set(
                            mapOf("id" to index + 1),
                            SetOptions.merge()
                        )
                    )
                }
            } else {
                // Документа ещё нет — создаём его целиком
                val gameData = mapOf(
                    "id" to index + 1,
                    "title" to game.title,
                    "genre" to game.genre,
                    "image" to game.image,
                    "description" to game.description,
                    "liked" to false
                )

                Tasks.await(document.set(gameData))
            }
        }
    }

    // Сохраняем лайк в Firebase
    suspend fun updateLiked(
        firestoreId: String,
        liked: Boolean
    ) = withContext(Dispatchers.IO) {

        if (firestoreId.isNotBlank()) {
            Tasks.await(
                gamesCollection.document(firestoreId)
                    .update("liked", liked)
            )
        }
    }

    // Старый метод Room оставляем для совместимости
    suspend fun updateLiked(
        gameId: Int,
        liked: Boolean
    ) {
        gameDao.updateLiked(gameId, liked)
    }

    // Поиск по загруженному каталогу
    suspend fun searchGames(query: String): List<Game> {
        return getGames().filter {
            it.title.contains(query, ignoreCase = true)
        }
    }

    // Старые методы Room пока сохраняем
    suspend fun addGames(games: List<Game>) {
        gameDao.insertGames(games)
    }

    suspend fun getGameCount(): Int {
        return gameDao.getGameCount()
    }

    // Поиск через RAWG пока оставляем, но каталог от него не зависит
    suspend fun searchGamesOnline(query: String): List<Game> {
        val response = RetrofitInstance.api.searchGames(
            apiKey = "YOUR_API_KEY",
            search = query
        )

        return response.results.map { rawgGame ->
            convertToGame(rawgGame)
        }
    }

    private fun convertToGame(rawgGame: RawgGame): Game {
        val genre = rawgGame.genres
            ?.joinToString(" • ") { it.name }
            ?: "Неизвестный жанр"

        return Game(
            title = rawgGame.name,
            genre = genre,
            image = rawgGame.background_image ?: "",
            description = "Рейтинг игры: ${rawgGame.rating}"
        )
    }

    // Профили по-прежнему хранятся в Room
    suspend fun getUserProfile(uid: String): UserProfile? {
        return gameDao.getUserProfile(uid)
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        gameDao.insertUserProfile(profile)
    }
}
