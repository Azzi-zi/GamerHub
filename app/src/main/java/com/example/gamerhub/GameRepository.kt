package com.example.gamerhub

class GameRepository(
    private val gameDao: GameDao
) {

    suspend fun getGames(): List<Game> {
        return gameDao.getAllGames()
    }

    suspend fun addGames(games: List<Game>) {
        gameDao.insertGames(games)
    }

    suspend fun getGameCount(): Int {
        return gameDao.getGameCount()
    }

    suspend fun loadInitialGames() {

        if (gameDao.getGameCount() > 0) {
            return
        }

        val initialGames = listOf(
            Game(
                title = "Minecraft",
                genre = "Sandbox • Open World",
                image = "mincraft",
                description = "Песочница с огромным открытым миром, где игроки могут исследовать мир, добывать ресурсы, строить сооружения и создавать собственные приключения."
            ),

            Game(
                title = "World of Tanks",
                genre = "Online • Action",
                image = "world_of_tanks",
                description = "Онлайн-игра о танковых сражениях. Игроки управляют боевыми машинами и сражаются друг с другом в командных боях."
            ),

            Game(
                title = "Baldur's Gate 3",
                genre = "RPG • Adventure",
                image = "bal_gate",
                description = "Большая ролевая игра с исследованием мира, персонажами, диалогами и множеством решений, влияющих на развитие приключения."
            ),

            Game(
                title = "GTA VI",
                genre = "Action • Open World",
                image = "gta",
                description = "Приключенческая игра с открытым миром, события которой разворачиваются в современном криминальном мире."
            ),

            Game(
                title = "Genshin Impact",
                genre = "RPG • Open World",
                image = "genshin_impact",
                description = "Ролевая игра с открытым миром, исследованием локаций, сражениями и большим количеством игровых персонажей."
            ),

            Game(
                title = "Portal 2",
                genre = "Puzzle • Adventure",
                image = "portal",
                description = "Головоломка от первого лица, в которой игрок использует портальную пушку для решения пространственных задач."
            ),

            Game(
                title = "Wuthering Waves",
                genre = "RPG • Action",
                image = "wuwa",
                description = "Экшен-RPG с открытым миром, исследованием локаций и динамическими сражениями."
            )
        )

        gameDao.insertGames(initialGames)
    }
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

    suspend fun searchGames(query: String): List<Game> {
        return gameDao.searchGames(query)
    }
}