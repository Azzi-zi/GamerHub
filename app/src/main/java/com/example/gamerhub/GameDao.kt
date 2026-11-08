package com.example.gamerhub

// @Dao обозначает интерфейс как объект доступа к данным (Data Access Object).
// Room использует его методы для выполнения запросов к локальной базе данных.
import androidx.room.Dao

// Аннотация для добавления одной или нескольких записей в таблицу.
import androidx.room.Insert

// Позволяет указать, что делать при конфликте первичного ключа.
import androidx.room.OnConflictStrategy

// Аннотация для выполнения SQL-запросов.
import androidx.room.Query

// Аннотация для обновления существующих записей.
import androidx.room.Update

// Интерфейс содержит методы для работы с таблицами локальной базы Room.
@Dao
interface GameDao {

    // Получаем все игры из таблицы games.
    // SELECT * означает, что выбираются все столбцы таблицы.
    // suspend позволяет вызывать функцию из корутины.
    @Query("SELECT * FROM games")
    suspend fun getAllGames(): List<Game>

    // Получаем общее количество игр в таблице.
    // COUNT(*) подсчитывает количество записей.
    // Результат возвращается в виде целого числа.
    @Query("SELECT COUNT(*) FROM games")
    suspend fun getGameCount(): Int

    // Ищем игры по названию.
    // LIKE позволяет выполнять поиск по частичному совпадению.
    // Символы '%' означают, что до и после поискового запроса
    // могут находиться любые символы.
    // :query — параметр, который передаётся в функцию searchGames().
    @Query("""
        SELECT * FROM games
        WHERE title LIKE '%' || :query || '%'
    """)
    suspend fun searchGames(query: String): List<Game>

    // Добавляем одну игру в таблицу games.
    // Room автоматически сопоставляет свойства объекта Game
    // со столбцами таблицы.
    @Insert
    suspend fun insertGame(game: Game)

    // Добавляем сразу несколько игр.
    // Список Game будет сохранён в таблицу games.
    @Insert
    suspend fun insertGames(games: List<Game>)

    // Обновляем значение liked у конкретной игры.
    // UPDATE — команда изменения существующих записей.
    // SET liked = :liked устанавливает новое значение лайка.
    // WHERE id = :gameId указывает, какую именно игру нужно изменить.
    @Query("UPDATE games SET liked = :liked WHERE id = :gameId")
    suspend fun updateLiked(gameId: Int, liked: Boolean)

    // Получаем профиль конкретного пользователя по его UID.
    // UID — уникальный идентификатор аккаунта.
    // Запрос ищет запись в таблице user_profiles.
    // Если профиль не найден, возвращается null.
    @Query("SELECT * FROM user_profiles WHERE uid = :uid")
    suspend fun getUserProfile(uid: String): UserProfile?

    // Сохраняем профиль пользователя в таблицу.
    // Если запись с таким первичным ключом уже существует,
    // OnConflictStrategy.REPLACE заменяет её новой записью.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfile)
}