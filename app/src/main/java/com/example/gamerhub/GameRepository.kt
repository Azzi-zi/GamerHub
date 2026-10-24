
package com.example.gamerhub

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GameRepository(
    private val gameDao: GameDao
) {

    private val firestore = FirebaseFirestore.getInstance()
    private val gamesCollection = firestore.collection("games")

    // Загружаем каталог и лайки из Firebase.
    suspend fun getGames(): List<Game> = withContext(Dispatchers.IO) {

        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("Сначала войдите в аккаунт.")

        val snapshot = Tasks.await(gamesCollection.get())

        snapshot.documents.mapNotNull { document ->

            val title = document.getString("title")
                ?: return@mapNotNull null

            val game = Game(
                id = document.getLong("id")?.toInt()
                    ?: document.id.hashCode(),
                title = title,
                genre = document.getString("genre")
                    ?: "Неизвестный жанр",
                image = document.getString("image") ?: "",
                description = document.getString("description") ?: "",
                // Это старое поле больше не определяет лайк пользователя.
                liked = document.get("liked") as? Boolean ?: false
            ).apply {
                firestoreId = document.id
            }

            // У каждой игры своя подколлекция likes.
            val likesCollection = document.reference.collection("likes")
            val likesSnapshot = Tasks.await(likesCollection.get())

            game.likesCount = likesSnapshot.size()
            game.isLikedByCurrentUser =
                likesSnapshot.documents.any { it.id == currentUid }

            game
        }
    }

    // Ставит или убирает лайк текущего пользователя.
    suspend fun setGameLike(
        firestoreId: String,
        shouldLike: Boolean
    ) = withContext(Dispatchers.IO) {

        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("Сначала войдите в аккаунт.")

        require(firestoreId.isNotBlank()) {
            "Не найден ID игры в Firebase."
        }

        val likeDocument = gamesCollection
            .document(firestoreId)
            .collection("likes")
            .document(uid)

        if (shouldLike) {
            Tasks.await(
                likeDocument.set(
                    mapOf(
                        "uid" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            )
        } else {
            Tasks.await(likeDocument.delete())
        }
    }

    // Резервная загрузка из локальной базы Room.
    suspend fun getLocalGames(): List<Game> {
        return gameDao.getAllGames()
    }

    // Создаём начальные документы, только если их ещё нет.
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
                if (!snapshot.contains("id")) {
                    Tasks.await(
                        document.set(
                            mapOf("id" to index + 1),
                            SetOptions.merge()
                        )
                    )
                }
            } else {
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

    // Старый метод оставлен для совместимости с другим кодом.
    // Новый интерфейс лайков его больше не использует.
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

    // Старые методы Room сохраняем.
    suspend fun updateLiked(gameId: Int, liked: Boolean) {
        gameDao.updateLiked(gameId, liked)
    }

    suspend fun searchGames(query: String): List<Game> {
        return getGames().filter {
            it.title.contains(query, ignoreCase = true)
        }
    }

    suspend fun addGames(games: List<Game>) {
        gameDao.insertGames(games)
    }

    suspend fun getGameCount(): Int {
        return gameDao.getGameCount()
    }

    // Поиск через RAWG оставляем без изменений.
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

    // Профили по-прежнему хранятся в Room.
    suspend fun getUserProfile(uid: String): UserProfile? {
        return gameDao.getUserProfile(uid)
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        gameDao.insertUserProfile(profile)
    }
}
