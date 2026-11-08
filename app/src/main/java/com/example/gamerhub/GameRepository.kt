package com.example.gamerhub

// Mutex позволяет блокировать выполнение определённого участка кода,
// чтобы несколько корутин не выполняли одну операцию одновременно.
import kotlinx.coroutines.sync.Mutex

// withLock позволяет выполнить код внутри блокировки Mutex.
// После завершения блока блокировка автоматически освобождается.
import kotlinx.coroutines.sync.withLock

// async запускает асинхронную задачу и возвращает Deferred с результатом.
import kotlinx.coroutines.async

// awaitAll ожидает завершения всех переданных асинхронных задач.
import kotlinx.coroutines.awaitAll

// Позволяет создавать область выполнения корутин.
import kotlinx.coroutines.coroutineScope

// Tasks.await позволяет ожидать завершения Google Task внутри suspend-функции.
import com.google.android.gms.tasks.Tasks

// Firebase Authentication используется для получения текущего пользователя.
import com.google.firebase.auth.FirebaseAuth

// FieldValue позволяет использовать специальные значения Firestore,
// например серверное время создания записи.
import com.google.firebase.firestore.FieldValue

// Основной класс для взаимодействия с Cloud Firestore.
import com.google.firebase.firestore.FirebaseFirestore

// SetOptions позволяет задавать параметры сохранения документов Firestore.
import com.google.firebase.firestore.SetOptions

// Dispatchers.IO предназначен для операций ввода-вывода:
// запросов к Firebase, базе данных и файловой системе.
import kotlinx.coroutines.Dispatchers

// withContext позволяет выполнять код в указанном потоке выполнения.
import kotlinx.coroutines.withContext

// Репозиторий объединяет работу с локальной базой Room,
// облачной базой Firestore и внешним API RAWG.
class GameRepository(

    // DAO предоставляет методы для обращения к локальной базе Room.
    private val gameDao: GameDao

) {

    // Получаем экземпляр Firestore для работы с облачной базой.
    private val firestore = FirebaseFirestore.getInstance()

    // Получаем ссылку на коллекцию games в Firestore.
    private val gamesCollection = firestore.collection("games")

    // Общий кэш каталога для всех разделов приложения.
    // Здесь хранятся уже загруженные игры.
    // null означает, что каталог ещё не был загружен.
    //
    // @Volatile гарантирует, что изменения переменной будут видны
    // другим потокам выполнения.
    @Volatile
    private var cachedGames: List<Game>? = null

    // Mutex не позволяет нескольким разделам одновременно
    // загружать один и тот же каталог игр.
    private val gamesMutex = Mutex()

    // Получаем каталог игр.
    // Если данные уже есть в кэше, возвращаем их.
    // Если кэш пустой, загружаем игры из Firestore.
    suspend fun getGames(): List<Game> {

        // Проверяем, существует ли уже загруженный каталог.
        // Если существует, сразу возвращаем его.
        cachedGames?.let {
            return it
        }

        // Блокируем выполнение, чтобы избежать нескольких
        // одновременных загрузок каталога.
        return gamesMutex.withLock {

            // Повторно проверяем кэш после получения блокировки.
            // Возможно, другая корутина уже успела загрузить игры.
            cachedGames?.let {
                return@withLock it
            }

            // Загружаем игры из Firestore.
            loadGamesFromFirestore().also { games ->

                // Сохраняем полученный список в общий кэш.
                cachedGames = games
            }
        }
    }

    // Загружает каталог игр из Firestore вместе с информацией о лайках.
    private suspend fun loadGamesFromFirestore(): List<Game> =

        // Выполняем запросы в фоновом потоке для операций ввода-вывода.
        withContext(Dispatchers.IO) {

            // Получаем UID текущего авторизованного пользователя.
            // Если пользователь не вошёл в аккаунт, выбрасываем исключение.
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid
                ?: throw IllegalStateException("Сначала войдите в аккаунт.")

            // Получаем все документы из коллекции games.
            // Tasks.await ожидает завершения запроса Firestore.
            val snapshot = Tasks.await(gamesCollection.get())

            // Создаём область для параллельного выполнения корутин.
            coroutineScope {

                // Преобразуем документы Firestore в объекты Game.
                // mapNotNull пропускает документы, в которых отсутствует название игры.
                snapshot.documents.mapNotNull { document ->

                    // Получаем название игры.
                    // Если оно отсутствует, пропускаем этот документ.
                    val title = document.getString("title")
                        ?: return@mapNotNull null

                    // Создаём объект Game на основе данных документа Firestore.
                    val game = Game(

                        // Получаем числовой ID игры.
                        // Если его нет, используем hashCode ID документа.
                        id = document.getLong("id")?.toInt()
                            ?: document.id.hashCode(),

                        // Название игры.
                        title = title,

                        // Получаем жанр.
                        // Если жанр отсутствует, устанавливаем значение по умолчанию.
                        genre = document.getString("genre")
                            ?: "Неизвестный жанр",

                        // Получаем изображение игры.
                        // Если изображения нет, используем пустую строку.
                        image = document.getString("image") ?: "",

                        // Получаем описание игры.
                        description = document.getString("description") ?: "",

                        // Получаем старое поле liked для совместимости.
                        // Если оно отсутствует, устанавливаем false.
                        liked = document.get("liked") as? Boolean ?: false

                    ).apply {

                        // Сохраняем настоящий ID документа Firestore.
                        // Он необходим для работы с лайками конкретной игры.
                        firestoreId = document.id
                    }

                    // Запускаем отдельную асинхронную задачу для получения лайков игры.
                    game to async {

                        // Получаем все документы из подколлекции likes.
                        val likesSnapshot = Tasks.await(
                            document.reference.collection("likes").get()
                        )

                        // Количество документов в likes соответствует количеству лайков.
                        game.likesCount = likesSnapshot.size()

                        // Проверяем, есть ли среди лайков документ текущего пользователя.
                        // Если есть, значит пользователь уже поставил лайк.
                        game.isLikedByCurrentUser =
                            likesSnapshot.documents.any {
                                it.id == currentUid
                            }
                    }

                }.also { pairs ->

                    // Ожидаем завершения загрузки лайков для всех игр.
                    pairs.map {
                        it.second
                    }.awaitAll()

                }.map {

                    // После загрузки лайков возвращаем только объекты Game.
                    // Пары с Deferred больше не нужны.
                    it.first
                }
            }
        }

    // Возвращает самые популярные игры по количеству лайков.
    suspend fun getPopularGames(limit: Int = 8): List<Game> {

        // Получаем общий каталог игр.
        return getGames()

            // Сортируем игры по количеству лайков.
            .sortedWith(

                // Сначала идут игры с наибольшим количеством лайков.
                compareByDescending<Game> {
                    it.likesCount

                    // Если количество лайков одинаковое,
                    // сортируем игры по названию в алфавитном порядке.
                }.thenBy {
                    it.title.lowercase()
                }
            )

            // Ограничиваем количество возвращаемых игр.
            // coerceAtLeast(0) не позволяет передать отрицательное число.
            .take(limit.coerceAtLeast(0))
    }

    // Ставит или убирает лайк текущего пользователя.
    suspend fun setGameLike(
        firestoreId: String,
        shouldLike: Boolean
    ) = withContext(Dispatchers.IO) {

        // Получаем UID текущего пользователя Firebase.
        // Если пользователь не авторизован, выбрасываем исключение.
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("Сначала войдите в аккаунт.")

        // Проверяем, что ID документа игры не пустой.
        // Если ID отсутствует, выполнение функции прекращается с ошибкой.
        require(firestoreId.isNotBlank()) {
            "Не найден ID игры в Firebase."
        }

        // Формируем ссылку на документ лайка.
        // Структура:
        // games/{firestoreId}/likes/{uid}
        //
        // UID используется как ID документа, поэтому один пользователь
        // не сможет создать несколько отдельных лайков для одной игры.
        val likeDocument = gamesCollection
            .document(firestoreId)
            .collection("likes")
            .document(uid)

        // Проверяем, нужно ли поставить лайк.
        if (shouldLike) {

            // Создаём или обновляем документ лайка.
            Tasks.await(
                likeDocument.set(
                    mapOf(

                        // Сохраняем UID пользователя.
                        "uid" to uid,

                        // Сохраняем время создания лайка.
                        // Время устанавливается сервером Firebase.
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            )

        } else {

            // Если пользователь снимает лайк,
            // удаляем его документ из подколлекции likes.
            Tasks.await(likeDocument.delete())
        }
    }

    // Резервная загрузка игр из локальной базы Room.
    // Может использоваться, если необходимо получить сохранённые локально данные.
    suspend fun getLocalGames(): List<Game> {

        // Получаем все игры через GameDao.
        return gameDao.getAllGames()
    }

    // Создаём начальные документы игр в Firestore,
    // только если соответствующие игры ещё не существуют.
    suspend fun loadInitialGames() = withContext(Dispatchers.IO) {

        // Создаём список начальных игр.
        // Каждая запись содержит:
        // 1. ID документа Firestore.
        // 2. Объект Game с информацией об игре.
        val initialGames = listOf(

            // Minecraft.
            "minecraft" to Game(
                title = "Minecraft",
                genre = "Sandbox • Open World",
                image = "mincraft",
                description = "Песочница с огромным открытым миром, где можно исследовать мир, добывать ресурсы и строить сооружения."
            ),

            // World of Tanks.
            "world_of_tanks" to Game(
                title = "World of Tanks",
                genre = "Online • Action",
                image = "world_of_tanks",
                description = "Онлайн-игра о танковых сражениях, в которой игроки управляют боевыми машинами."
            ),

            // Baldur's Gate 3.
            "baldurs_gate_3" to Game(
                title = "Baldur's Gate 3",
                genre = "RPG • Adventure",
                image = "bal_gate",
                description = "Ролевая игра с исследованием мира, диалогами и решениями, влияющими на приключение."
            ),

            // Grand Theft Auto VI.
            "gta_vi" to Game(
                title = "GTA VI",
                genre = "Action • Open World",
                image = "gta",
                description = "Приключенческая игра с открытым миром в криминальном сеттинге."
            ),

            // Genshin Impact.
            "genshin_impact" to Game(
                title = "Genshin Impact",
                genre = "RPG • Open World",
                image = "genshin_impact",
                description = "Ролевая игра с открытым миром, исследованием локаций и игровыми персонажами."
            ),

            // Portal 2.
            "portal_2" to Game(
                title = "Portal 2",
                genre = "Puzzle • Adventure",
                image = "portal",
                description = "Головоломка от первого лица, в которой портальная пушка помогает решать пространственные задачи."
            ),

            // Wuthering Waves.
            "wuthering_waves" to Game(
                title = "Wuthering Waves",
                genre = "RPG • Action",
                image = "wuwa",
                description = "Экшен-RPG с исследованием мира и динамическими сражениями."
            ),

            // Counter-Strike 2.
            "counter_strike_2" to Game(
                title = "Counter-Strike 2",
                genre = "FPS • Tactical Shooter",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/730/header.jpg",
                description = "Тактический многопользовательский шутер с командными сражениями."
            ),

            // Dota 2.
            "dota_2" to Game(
                title = "Dota 2",
                genre = "MOBA • Strategy",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/570/header.jpg",
                description = "Командная стратегическая игра, в которой герои сражаются за уничтожение базы противника."
            ),

            // PUBG: Battlegrounds.
            "pubg_battlegrounds" to Game(
                title = "PUBG: Battlegrounds",
                genre = "Battle Royale • Shooter",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/578080/header.jpg",
                description = "Королевская битва, в которой игроки собирают снаряжение и борются за выживание."
            ),

            // Apex Legends.
            "apex_legends" to Game(
                title = "Apex Legends",
                genre = "Battle Royale • FPS",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/1172470/header.jpg",
                description = "Командный шутер с уникальными героями и динамичными сражениями."
            ),

            // Cyberpunk 2077.
            "cyberpunk_2077" to Game(
                title = "Cyberpunk 2077",
                genre = "RPG • Open World",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/1091500/header.jpg",
                description = "Ролевая игра в футуристическом открытом мире Найт-Сити."
            ),

            // Elden Ring.
            "elden_ring" to Game(
                title = "Elden Ring",
                genre = "Action RPG • Fantasy",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/1245620/header.jpg",
                description = "Фэнтезийная ролевая игра с исследованием мира и сложными сражениями."
            ),

            // Terraria.
            "terraria" to Game(
                title = "Terraria",
                genre = "Sandbox • Adventure",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/105600/header.jpg",
                description = "Двухмерная песочница с исследованием, строительством и сражениями."
            ),

            // Stardew Valley.
            "stardew_valley" to Game(
                title = "Stardew Valley",
                genre = "Simulation • RPG",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/413150/header.jpg",
                description = "Симулятор фермы, где можно выращивать урожай, развивать хозяйство и знакомиться с жителями."
            ),

            // Red Dead Redemption 2.
            "red_dead_redemption_2" to Game(
                title = "Red Dead Redemption 2",
                genre = "Action • Open World",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/1174180/header.jpg",
                description = "Приключение на Диком Западе с открытым миром и насыщенным сюжетом."
            ),

            // The Witcher 3: Wild Hunt.
            "the_witcher_3" to Game(
                title = "The Witcher 3: Wild Hunt",
                genre = "RPG • Fantasy",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/292030/header.jpg",
                description = "Ролевая игра о ведьмаке Геральте, охотящемся на чудовищ."
            ),

            // Left 4 Dead 2.
            "left_4_dead_2" to Game(
                title = "Left 4 Dead 2",
                genre = "Co-op • Horror",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/550/header.jpg",
                description = "Кооперативный шутер о выживании во время зомби-апокалипсиса."
            ),

            // Rust.
            "rust" to Game(
                title = "Rust",
                genre = "Survival • Multiplayer",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/252490/header.jpg",
                description = "Многопользовательская игра о выживании, строительстве и борьбе за ресурсы."
            ),

            // Team Fortress 2.
            "team_fortress_2" to Game(
                title = "Team Fortress 2",
                genre = "FPS • Multiplayer",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/440/header.jpg",
                description = "Командный шутер с разными классами персонажей и динамичными матчами."
            ),

            // Warframe.
            "warframe" to Game(
                title = "Warframe",
                genre = "Action • Online",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/230410/header.jpg",
                description = "Бесплатный онлайн-экшен с космическими ниндзя и кооперативными миссиями."
            ),

            // Hades.
            "hades" to Game(
                title = "Hades",
                genre = "Roguelike • Action",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/1145360/header.jpg",
                description = "Динамичный экшен о попытках выбраться из подземного мира."
            ),

            // Hollow Knight.
            "hollow_knight" to Game(
                title = "Hollow Knight",
                genre = "Metroidvania • Adventure",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/367520/header.jpg",
                description = "Приключение в подземном королевстве с тайнами и сложными боссами."
            ),

            // Sekiro: Shadows Die Twice.
            "sekiro" to Game(
                title = "Sekiro: Shadows Die Twice",
                genre = "Action • Adventure",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/814380/header.jpg",
                description = "Экшен о воине-шиноби в фантастической версии Японии."
            ),

            // Resident Evil 4.
            "resident_evil_4" to Game(
                title = "Resident Evil 4",
                genre = "Survival Horror • Action",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/2050650/header.jpg",
                description = "Хоррор-экшен о спасательной миссии в опасной деревне."
            ),

            // The Sims 4.
            "the_sims_4" to Game(
                title = "The Sims 4",
                genre = "Simulation • Life",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/1222670/header.jpg",
                description = "Симулятор жизни, в котором можно создавать персонажей и строить дома."
            ),

            // Forza Horizon 5.
            "forza_horizon_5" to Game(
                title = "Forza Horizon 5",
                genre = "Racing • Open World",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/1551360/header.jpg",
                description = "Гоночная игра с открытым миром, автомобилями и соревнованиями."
            ),

            // Sea of Thieves.
            "sea_of_thieves" to Game(
                title = "Sea of Thieves",
                genre = "Adventure • Multiplayer",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/1172620/header.jpg",
                description = "Многопользовательское пиратское приключение с морскими путешествиями."
            ),

            // No Man's Sky.
            "no_mans_sky" to Game(
                title = "No Man's Sky",
                genre = "Exploration • Survival",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/275850/header.jpg",
                description = "Исследование процедурно генерируемой вселенной с множеством планет."
            ),

            // Don't Starve Together.
            "dont_starve_together" to Game(
                title = "Don't Starve Together",
                genre = "Survival • Co-op",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/322330/header.jpg",
                description = "Кооперативное выживание в необычном мире, полном опасностей."
            ),

            // Phasmophobia.
            "phasmophobia" to Game(
                title = "Phasmophobia",
                genre = "Horror • Co-op",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/739630/header.jpg",
                description = "Кооперативный хоррор об исследовании мест с паранормальной активностью."
            ),

            // Dead by Daylight.
            "dead_by_daylight" to Game(
                title = "Dead by Daylight",
                genre = "Horror • Multiplayer",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/381210/header.jpg",
                description = "Асимметричный хоррор, в котором выжившие пытаются спастись от убийцы."
            ),

            // Euro Truck Simulator 2.
            "euro_truck_simulator_2" to Game(
                title = "Euro Truck Simulator 2",
                genre = "Simulation • Driving",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/227300/header.jpg",
                description = "Симулятор дальнобойщика с перевозками грузов по европейским дорогам."
            ),

            // Fall Guys.
            "fall_guys" to Game(
                title = "Fall Guys",
                genre = "Party • Platformer",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/1097150/header.jpg",
                description = "Многопользовательские соревнования с препятствиями и забавными испытаниями."
            ),

            // Tom Clancy's Rainbow Six Siege.
            "rainbow_six_siege" to Game(
                title = "Tom Clancy's Rainbow Six Siege",
                genre = "Tactical FPS • Online",
                image = "https://cdn.akamai.steamstatic.com/steam/apps/359550/header.jpg",
                description = "Тактический командный шутер с операторами и разрушаемым окружением."
            )
        )

        // Получаем все существующие документы из коллекции games.
        // Это позволяет заранее проверить, какие игры уже добавлены.
        val existingSnapshot = Tasks.await(gamesCollection.get())

        // Собираем названия существующих игр.
        // trim() удаляет пробелы по краям.
        // lowercase() приводит названия к нижнему регистру.
        // Благодаря этому Minecraft и minecraft считаются одинаковыми.
        val existingTitles = existingSnapshot.documents
            .mapNotNull {
                it.getString("title")
            }
            .map {
                it.trim().lowercase()
            }
            .toMutableSet()

        // Перебираем все начальные игры.
        // index — порядковый номер игры.
        // entry — пара, содержащая ID документа и объект Game.
        initialGames.forEachIndexed { index, entry ->

            // Получаем ID документа Firestore.
            val documentId = entry.first

            // Получаем объект игры.
            val game = entry.second

            // Создаём ссылку на документ конкретной игры.
            val document = gamesCollection.document(documentId)

            // Проверяем, существует ли уже документ с таким ID.
            val snapshot = Tasks.await(document.get())

            // Если документ уже существует, не перезаписываем его.
            if (snapshot.exists()) {

                // Добавляем название в набор существующих игр.
                existingTitles.add(game.title.trim().lowercase())

                // Проверяем, есть ли у существующего документа поле id.
                if (!snapshot.contains("id")) {

                    // Если поля нет, добавляем его.
                    // SetOptions.merge() сохраняет остальные существующие поля.
                    Tasks.await(
                        document.set(
                            mapOf("id" to index + 1),
                            SetOptions.merge()
                        )
                    )
                }

                // Переходим к следующей игре.
                return@forEachIndexed
            }

            // Нормализуем название новой игры для проверки дубликатов.
            val normalizedTitle = game.title.trim().lowercase()

            // Если такое название уже существует под другим ID,
            // пропускаем добавление игры.
            if (normalizedTitle in existingTitles) {

                // Записываем информацию о пропущенном дубликате в Logcat.
                android.util.Log.d(
                    "GameRepository",
                    "Пропускаем дубликат: ${game.title}"
                )

                // Переходим к следующей игре.
                return@forEachIndexed
            }

            // Создаём карту данных для сохранения в Firestore.
            val gameData = mapOf(

                // Порядковый номер игры.
                "id" to index + 1,

                // Название игры.
                "title" to game.title,

                // Жанр игры.
                "genre" to game.genre,

                // Изображение игры.
                "image" to game.image,

                // Описание игры.
                "description" to game.description,

                // Старое поле лайка оставляем со значением false.
                "liked" to false
            )

            // Сохраняем новую игру в Firestore.
            Tasks.await(document.set(gameData))

            // Добавляем название в набор существующих игр,
            // чтобы избежать повторного добавления в рамках этого запуска.
            existingTitles.add(normalizedTitle)
        }
    }

    // Старый метод оставлен для совместимости с другим кодом.
    // Новая система лайков использует setGameLike().
    suspend fun updateLiked(
        firestoreId: String,
        liked: Boolean
    ) = withContext(Dispatchers.IO) {

        // Проверяем, что ID документа не пустой.
        if (firestoreId.isNotBlank()) {

            // Обновляем поле liked у конкретной игры в Firestore.
            Tasks.await(
                gamesCollection.document(firestoreId)
                    .update("liked", liked)
            )
        }
    }

    // Старый метод для обновления лайка в локальной базе Room.
    suspend fun updateLiked(gameId: Int, liked: Boolean) {

        // Передаём запрос в GameDao.
        gameDao.updateLiked(gameId, liked)
    }

    // Ищем игры по названию.
    suspend fun searchGames(query: String): List<Game> {

        // Получаем каталог и фильтруем его по поисковому запросу.
        return getGames().filter {

            // contains проверяет, содержится ли запрос в названии игры.
            // ignoreCase = true отключает учёт регистра.
            it.title.contains(query, ignoreCase = true)
        }
    }

    // Добавляем список игр в локальную базу Room.
    suspend fun addGames(games: List<Game>) {

        // Передаём список в DAO для сохранения.
        gameDao.insertGames(games)
    }

    // Получаем количество игр, сохранённых в локальной базе Room.
    suspend fun getGameCount(): Int {

        // Выполняем запрос через GameDao.
        return gameDao.getGameCount()
    }

    // Поиск игр через внешний API RAWG.
    // Этот метод оставлен без изменений.
    suspend fun searchGamesOnline(query: String): List<Game> {

        // Отправляем запрос к API RAWG.
        val response = RetrofitInstance.api.searchGames(
            apiKey = "YOUR_API_KEY",
            search = query
        )

        // Преобразуем результаты RAWG в объекты Game.
        return response.results.map { rawgGame ->

            // Вызываем функцию преобразования одной игры.
            convertToGame(rawgGame)
        }
    }

    // Преобразует объект RawgGame в объект Game,
    // чтобы результаты внешнего API можно было использовать в приложении.
    private fun convertToGame(rawgGame: RawgGame): Game {

        // Получаем жанры игры.
        // Если жанров несколько, объединяем их через разделитель •.
        // Если список жанров отсутствует, используем значение по умолчанию.
        val genre = rawgGame.genres
            ?.joinToString(" • ") {
                it.name
            }
            ?: "Неизвестный жанр"

        // Создаём и возвращаем объект Game.
        return Game(

            // Название игры из RAWG.
            title = rawgGame.name,

            // Жанры игры.
            genre = genre,

            // URL фонового изображения.
            // Если изображения нет, используем пустую строку.
            image = rawgGame.background_image ?: "",

            // В качестве описания сохраняем рейтинг игры.
            description = "Рейтинг игры: ${rawgGame.rating}"
        )
    }

    // Получаем профиль пользователя из локальной базы Room.
    suspend fun getUserProfile(uid: String): UserProfile? {

        // Передаём UID в GameDao и возвращаем найденный профиль.
        return gameDao.getUserProfile(uid)
    }

    // Сохраняем профиль пользователя в локальную базу Room.
    suspend fun saveUserProfile(profile: UserProfile) {

        // Передаём объект профиля в DAO.
        gameDao.insertUserProfile(profile)
    }
}