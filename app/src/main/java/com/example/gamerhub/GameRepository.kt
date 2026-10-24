
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

            ),
            "counter_strike_2" to Game(
                title = "Counter-Strike 2",
                genre = "FPS • Tactical Shooter",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/730/header.jpg",
                description = "Тактический многопользовательский шутер с командными сражениями."
            ),
            "dota_2" to Game(
                title = "Dota 2",
                genre = "MOBA • Strategy",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/570/header.jpg",
                description = "Командная стратегическая игра, в которой герои сражаются за уничтожение базы противника."
            ),
            "pubg_battlegrounds" to Game(
                title = "PUBG: Battlegrounds",
                genre = "Battle Royale • Shooter",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/578080/header.jpg",
                description = "Королевская битва, в которой игроки собирают снаряжение и борются за выживание."
            ),
            "apex_legends" to Game(
                title = "Apex Legends",
                genre = "Battle Royale • FPS",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/1172470/header.jpg",
                description = "Командный шутер с уникальными героями и динамичными сражениями."
            ),
            "cyberpunk_2077" to Game(
                title = "Cyberpunk 2077",
                genre = "RPG • Open World",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/1091500/header.jpg",
                description = "Ролевая игра в футуристическом открытом мире Найт-Сити."
            ),
            "elden_ring" to Game(
                title = "Elden Ring",
                genre = "Action RPG • Fantasy",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/1245620/header.jpg",
                description = "Фэнтезийная ролевая игра с исследованием мира и сложными сражениями."
            ),
            "terraria" to Game(
                title = "Terraria",
                genre = "Sandbox • Adventure",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/105600/header.jpg",
                description = "Двухмерная песочница с исследованием, строительством и сражениями."
            ),
            "stardew_valley" to Game(
                title = "Stardew Valley",
                genre = "Simulation • RPG",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/413150/header.jpg",
                description = "Симулятор фермы, где можно выращивать урожай, развивать хозяйство и знакомиться с жителями."
            ),

            "red_dead_redemption_2" to Game(
                title = "Red Dead Redemption 2",
                genre = "Action • Open World",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/1174180/header.jpg",
                description = "Приключение на Диком Западе с открытым миром и насыщенным сюжетом."
            ),
            "the_witcher_3" to Game(
                title = "The Witcher 3: Wild Hunt",
                genre = "RPG • Fantasy",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/292030/header.jpg",
                description = "Ролевая игра о ведьмаке Геральте, охотящемся на чудовищ."
            ),
            "left_4_dead_2" to Game(
                title = "Left 4 Dead 2",
                genre = "Co-op • Horror",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/550/header.jpg",
                description = "Кооперативный шутер о выживании во время зомби-апокалипсиса."
            ),
            "rust" to Game(
                title = "Rust",
                genre = "Survival • Multiplayer",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/252490/header.jpg",
                description = "Многопользовательская игра о выживании, строительстве и борьбе за ресурсы."
            ),
            "team_fortress_2" to Game(
                title = "Team Fortress 2",
                genre = "FPS • Multiplayer",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/440/header.jpg",
                description = "Командный шутер с разными классами персонажей и динамичными матчами."
            ),
            "warframe" to Game(
                title = "Warframe",
                genre = "Action • Online",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/230410/header.jpg",
                description = "Бесплатный онлайн-экшен с космическими ниндзя и кооперативными миссиями."
            ),
            "hades" to Game(
                title = "Hades",
                genre = "Roguelike • Action",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/1145360/header.jpg",
                description = "Динамичный экшен о попытках выбраться из подземного мира."
            ),
            "hollow_knight" to Game(
                title = "Hollow Knight",
                genre = "Metroidvania • Adventure",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/367520/header.jpg",
                description = "Приключение в подземном королевстве с тайнами и сложными боссами."
            ),
            "sekiro" to Game(
                title = "Sekiro: Shadows Die Twice",
                genre = "Action • Adventure",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/814380/header.jpg",
                description = "Экшен о воине-шиноби в фантастической версии Японии."
            ),
            "resident_evil_4" to Game(
                title = "Resident Evil 4",
                genre = "Survival Horror • Action",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/2050650/header.jpg",
                description = "Хоррор-экшен о спасательной миссии в опасной деревне."
            ),
            "the_sims_4" to Game(
                title = "The Sims 4",
                genre = "Simulation • Life",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/1222670/header.jpg",
                description = "Симулятор жизни, в котором можно создавать персонажей и строить дома."
            ),
            "forza_horizon_5" to Game(
                title = "Forza Horizon 5",
                genre = "Racing • Open World",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/1551360/header.jpg",
                description = "Гоночная игра с открытым миром, автомобилями и соревнованиями."
            ),
            "sea_of_thieves" to Game(
                title = "Sea of Thieves",
                genre = "Adventure • Multiplayer",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/1172620/header.jpg",
                description = "Многопользовательское пиратское приключение с морскими путешествиями."
            ),
            "no_mans_sky" to Game(
                title = "No Man's Sky",
                genre = "Exploration • Survival",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/275850/header.jpg",
                description = "Исследование процедурно генерируемой вселенной с множеством планет."
            ),
            "dont_starve_together" to Game(
                title = "Don't Starve Together",
                genre = "Survival • Co-op",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/322330/header.jpg",
                description = "Кооперативное выживание в необычном мире, полном опасностей."
            ),
            "phasmophobia" to Game(
                title = "Phasmophobia",
                genre = "Horror • Co-op",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/739630/header.jpg",
                description = "Кооперативный хоррор об исследовании мест с паранормальной активностью."
            ),
            "dead_by_daylight" to Game(
                title = "Dead by Daylight",
                genre = "Horror • Multiplayer",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/381210/header.jpg",
                description = "Асимметричный хоррор, в котором выжившие пытаются спастись от убийцы."
            ),
            "euro_truck_simulator_2" to Game(
                title = "Euro Truck Simulator 2",
                genre = "Simulation • Driving",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/227300/header.jpg",
                description = "Симулятор дальнобойщика с перевозками грузов по европейским дорогам."
            ),
            "fall_guys" to Game(
                title = "Fall Guys",
                genre = "Party • Platformer",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/1097150/header.jpg",
                description = "Многопользовательские соревнования с препятствиями и забавными испытаниями."
            ),
            "rainbow_six_siege" to Game(
                title = "Tom Clancy's Rainbow Six Siege",
                genre = "Tactical FPS • Online",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/359550/header.jpg",
                description = "Тактический командный шутер с операторами и разрушаемым окружением."
            )


        )

        // Получаем существующие игры один раз.
        val existingSnapshot = Tasks.await(gamesCollection.get())

        // Сохраняем названия без учёта регистра и пробелов по краям.
        val existingTitles = existingSnapshot.documents
            .mapNotNull { it.getString("title") }
            .map { it.trim().lowercase() }
            .toMutableSet()

        initialGames.forEachIndexed { index, entry ->
            val documentId = entry.first
            val game = entry.second

            val document = gamesCollection.document(documentId)
            val snapshot = Tasks.await(document.get())

            // Если документ уже существует, не перезаписываем его.
            if (snapshot.exists()) {
                existingTitles.add(game.title.trim().lowercase())

                if (!snapshot.contains("id")) {
                    Tasks.await(
                        document.set(
                            mapOf("id" to index + 1),
                            SetOptions.merge()
                        )
                    )
                }

                return@forEachIndexed
            }

            // Не создаём игру, если такое название уже есть под другим ID.
            val normalizedTitle = game.title.trim().lowercase()

            if (normalizedTitle in existingTitles) {
                android.util.Log.d(
                    "GameRepository",
                    "Пропускаем дубликат: ${game.title}"
                )
                return@forEachIndexed
            }

            val gameData = mapOf(
                "id" to index + 1,
                "title" to game.title,
                "genre" to game.genre,
                "image" to game.image,
                "description" to game.description,
                "liked" to false
            )

            Tasks.await(document.set(gameData))

            // Запоминаем добавленную игру, чтобы не создать её повторно
            // в рамках этого запуска.
            existingTitles.add(normalizedTitle)
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
