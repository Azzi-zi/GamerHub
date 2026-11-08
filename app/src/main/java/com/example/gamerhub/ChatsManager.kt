package com.example.gamerhub // Указывает пакет, к которому относится файл.

import android.widget.ImageView // Компонент для отображения изображений, например аватаров.
import com.bumptech.glide.Glide // Библиотека для загрузки изображений.
import android.graphics.Color // Класс для работы с цветами.
import android.graphics.Typeface // Класс для настройки шрифта.
import android.graphics.drawable.GradientDrawable // Класс для создания фонов с закруглёнными углами.
import android.text.Editable // Представляет изменяемый текст.
import android.text.TextWatcher // Позволяет отслеживать изменения текста.
import android.view.Gravity // Управляет выравниванием элементов.
import android.view.KeyEvent // Позволяет обрабатывать нажатия клавиш.
import android.view.View // Базовый класс элементов интерфейса.
import android.view.ViewGroup // Класс для работы с контейнерами элементов.
import android.view.inputmethod.EditorInfo // Содержит информацию о действиях экранной клавиатуры.
import android.widget.EditText // Поле для ввода текста.
import android.widget.LinearLayout // Контейнер для размещения элементов по горизонтали или вертикали.
import android.widget.ScrollView // Контейнер с возможностью прокрутки.
import android.widget.TextView // Компонент для отображения текста.
import android.widget.Toast // Компонент для показа коротких всплывающих сообщений.
import androidx.appcompat.app.AppCompatActivity // Базовый класс экрана приложения.
import com.google.firebase.Timestamp // Тип данных Firebase для хранения даты и времени.
import com.google.firebase.auth.FirebaseAuth // Инструмент для работы с авторизацией Firebase.
import com.google.firebase.firestore.FieldValue // Позволяет использовать специальные значения Firestore.
import com.google.firebase.firestore.FirebaseFirestore // Подключает облачную базу данных Firestore.
import com.google.firebase.firestore.ListenerRegistration // Тип для управления слушателями Firestore.
import java.text.SimpleDateFormat // Позволяет форматировать даты.
import java.util.Date // Представляет дату и время.
import java.util.Locale // Используется для учёта региональных настроек.

// Класс отвечает за поиск пользователей, создание чатов, загрузку и отправку сообщений.
class ChatsManager(
    private val activity: AppCompatActivity, // Экран приложения, с которым работает менеджер чатов.
    private val openPage: (Int) -> Unit, // Функция для переключения экранов по ID разметки.
    private val openPublicProfile: (String, () -> Unit) -> Unit // Функция открытия профиля пользователя с возможностью возврата.
) {
    private val db = FirebaseFirestore.getInstance() // Получает экземпляр облачной базы данных Firestore.

    private var currentChatId: String? = null // Хранит ID текущего чата; null означает, что чат не выбран.
    private var currentChatUsername = "" // Хранит имя собеседника.
    private var currentChatAvatarUrl = "" // Хранит ссылку на аватар собеседника.

    private var chatListener: ListenerRegistration? = null // Слушатель изменений списка чатов.
    private var messagesListener: ListenerRegistration? = null // Слушатель изменений сообщений текущего чата.

    // Класс данных для хранения информации, необходимой одной строке списка чатов.
    private data class ChatRowData(
        val chatId: String, // Уникальный идентификатор чата.
        val username: String, // Имя второго участника чата.
        val avatarUrl: String, // Ссылка на аватар второго участника.
        val lastMessage: String, // Текст последнего сообщения.
        val lastMessageAt: Timestamp? // Время последнего сообщения; может отсутствовать.
    )

    // Настраивает экран в зависимости от того, какая разметка сейчас открыта.
    fun setupPage(layoutId: Int, view: View) {
        if (layoutId != R.layout.screen_chats) { // Если открыт не список чатов:
            chatListener?.remove() // Отключает слушатель списка чатов, если он был создан.
            chatListener = null // Очищает ссылку на слушатель.
        }

        if (layoutId != R.layout.screen_private_chat) { // Если открыта не личная переписка:
            messagesListener?.remove() // Отключает слушатель сообщений.
            messagesListener = null // Очищает ссылку на слушатель.
        }

        when (layoutId) { // Определяет, какой экран нужно настроить.
            R.layout.screen_chats -> { // Экран со списком переписок.
                setupPlayerSearch(view) // Настраивает поиск игроков на этом экране.
                setupChatList(view) // Загружает список существующих чатов.

                view.findViewById<View>(R.id.newChatButton) // Находит кнопку создания нового чата.
                    ?.setOnClickListener { // Назначает действие при нажатии, если кнопка существует.
                        openPage(R.layout.screen_new_chat) // Открывает экран поиска пользователя.
                    }
            }

            R.layout.screen_new_chat -> setupUserSearch(view) // Настраивает поиск пользователей для нового чата.
            R.layout.screen_private_chat -> setupPrivateChat(view) // Настраивает экран личной переписки.
        }
    }

    // ---------------------------------------------------------
    // Поиск пользователей для создания личного чата
    // ---------------------------------------------------------

    // Настраивает поиск пользователей на отдельном экране создания чата.
    private fun setupUserSearch(view: View) {
        val searchInput = // Переменная для поля ввода поискового запроса.
            view.findViewById<EditText>(R.id.userSearchInput) // Находит поле поиска в XML-разметке.
        val resultsContainer = // Переменная для контейнера результатов поиска.
            view.findViewById<LinearLayout>(R.id.userResultsContainer) // Находит контейнер, куда будут добавляться пользователи.
        val statusText = // Переменная для текста состояния поиска.
            view.findViewById<TextView>(R.id.searchUsersStatus) // Находит текстовую подсказку на экране.

        val currentUid = FirebaseAuth.getInstance().currentUser?.uid // Получает ID текущего пользователя Firebase; если вход не выполнен, будет null.

        searchInput.addTextChangedListener(object : TextWatcher { // Добавляет наблюдатель за изменениями текста в поисковой строке.
            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int // Параметры содержат текст до изменения и сведения об изменении.
            ) {} // Метод не используется, поэтому его тело пустое.

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int // Параметры содержат текст во время изменения и количество изменённых символов.
            ) {} // Метод не используется, поэтому его тело пустое.

            override fun afterTextChanged(s: Editable?) { // Вызывается после завершения изменения текста.
                val rawQuery = s?.toString()?.trim().orEmpty() // Получает текст, убирает пробелы по краям и заменяет null пустой строкой.
                val query = rawQuery // Начинает подготовку поискового запроса.
                    .removePrefix("@") // Удаляет символ @ в начале строки, если он присутствует.
                    .trim() // Повторно убирает пробелы по краям.
                    .lowercase(Locale.ROOT) // Переводит запрос в нижний регистр для удобства сравнения.

                resultsContainer.removeAllViews() // Удаляет предыдущие результаты поиска.

                if (query.isEmpty()) { // Проверяет, не осталась ли поисковая строка пустой.
                    statusText.text = "Введи имя или тег игрока" // Показывает подсказку пользователю.
                    return // Завершает функцию, потому что пустой запрос искать не нужно.
                }

                statusText.text = "Ищем пользователей..." // Показывает сообщение о выполнении поиска.

                db.collection("publicProfiles") // Обращается к коллекции публичных профилей в Firestore.
                    .get() // Загружает документы из коллекции.
                    .addOnSuccessListener { snapshot -> // Выполняет блок, если загрузка прошла успешно.
                        val users = snapshot.documents.filter { document -> // Оставляет только документы, подходящие под условия поиска.
                            val uid = document.getString("uid") ?: document.id // Получает UID пользователя или использует ID документа.
                            val username = // Переменная для имени пользователя.
                                document.getString("username").orEmpty() // Получает имя из базы или пустую строку.

                            val tag = ( // Начинает получение игрового тега.
                                    document.getString("tag") // Сначала пытается получить поле tag.
                                        ?: document.getString("usernameTag") // Если tag отсутствует, использует usernameTag.
                                        ?: "" // Если оба поля отсутствуют, задаёт пустую строку.
                                    ).trim() // Удаляет пробелы в начале и конце тега.

                            val normalizedTag = tag // Подготавливает тег к сравнению.
                                .removePrefix("@") // Убирает символ @ в начале тега.
                                .lowercase(Locale.ROOT) // Переводит тег в нижний регистр.

                            uid != currentUid && // Исключает текущего пользователя из результатов.
                                    ( // Начало группы условий поиска.
                                            username.contains( // Проверяет, содержится ли запрос в имени пользователя.
                                                query, // Передаёт текст, который нужно найти.
                                                ignoreCase = true // Игнорирует различия между заглавными и строчными буквами.
                                            ) || normalizedTag.contains(query) // Либо проверяет совпадение с игровым тегом.
                                            ) // Конец группы условий.
                        }.take(30) // Ограничивает список первыми 30 найденными пользователями.

                        resultsContainer.removeAllViews() // Очищает контейнер перед добавлением новых результатов.

                        if (users.isEmpty()) { // Проверяет, нашёлся ли хотя бы один пользователь.
                            statusText.text = "Пользователи не найдены" // Показывает сообщение об отсутствии результатов.
                            return@addOnSuccessListener // Завершает текущий обработчик успешной загрузки.
                        }

                        statusText.text = "Найдено: ${users.size}" // Выводит количество найденных пользователей.

                        users.forEach { document -> // Перебирает всех найденных пользователей.
                            val uid = // Переменная для идентификатора пользователя.
                                document.getString("uid") ?: document.id // Получает UID или использует ID документа.
                            val username = // Переменная для имени пользователя.
                                document.getString("username") ?: "Игрок" // Получает имя или подставляет стандартное значение.

                            val tag = ( // Получает тег пользователя.
                                    document.getString("tag") // Читает поле tag.
                                        ?: document.getString("usernameTag") // Если его нет, читает usernameTag.
                                        ?: "" // Если оба поля отсутствуют, использует пустую строку.
                                    ).trim() // Убирает пробелы по краям тега.

                            val item = TextView(activity).apply { // Создаёт текстовый элемент для отображения найденного пользователя.
                                text = if (tag.isNotBlank()) { // Проверяет, заполнен ли тег.
                                    "🎮  $username\n     $tag" // Если тег есть, выводит имя и тег на разных строках.
                                } else { // Если тег отсутствует:
                                    "🎮  $username" // Выводит только имя пользователя.
                                }

                                textSize = 16f // Устанавливает размер текста.
                                setTextColor(Color.WHITE) // Устанавливает белый цвет текста.
                                setPadding( // Настраивает внутренние отступы элемента.
                                    dp(16), dp(14), dp(16), dp(14) // Отступы слева, сверху, справа и снизу.
                                )
                                gravity = Gravity.CENTER_VERTICAL // Выравнивает текст по вертикальному центру.
                                background = roundedBackground( // Устанавливает фон с закруглёнными углами.
                                    Color.rgb(26, 26, 32), 14 // Задаёт цвет фона и радиус закругления.
                                )
                                isClickable = true // Разрешает нажимать на элемент.
                                isFocusable = true // Разрешает элементу получать фокус.
                            }

                            val params = LinearLayout.LayoutParams( // Создаёт параметры размещения результата поиска.
                                ViewGroup.LayoutParams.MATCH_PARENT, // Элемент занимает всю доступную ширину.
                                ViewGroup.LayoutParams.WRAP_CONTENT // Высота зависит от содержимого.
                            ).apply {
                                bottomMargin = dp(8) // Добавляет нижний отступ между результатами.
                            }

                            resultsContainer.addView(item, params) // Добавляет пользователя в контейнер результатов.

                            val avatarUrl = // Переменная для ссылки на аватар.
                                document.getString("avatarUrl").orEmpty() // Получает ссылку из Firestore или пустую строку.

                            item.setOnClickListener { // Назначает действие при нажатии на пользователя.
                                createPrivateChat( // Вызывает функцию создания или открытия личного чата.
                                    uid, // Передаёт ID выбранного пользователя.
                                    username, // Передаёт имя выбранного пользователя.
                                    avatarUrl // Передаёт ссылку на его аватар.
                                )
                            }
                        }
                    }
                    .addOnFailureListener { error -> // Выполняется, если загрузить профили не удалось.
                        statusText.text = "Ошибка поиска пользователей" // Показывает сообщение об ошибке.

                        Toast.makeText( // Создаёт всплывающее уведомление.
                            activity, // Указывает экран, на котором появится уведомление.
                            error.message // Получает описание ошибки.
                                ?: "Не удалось выполнить поиск", // Использует стандартный текст, если описание отсутствует.
                            Toast.LENGTH_LONG // Устанавливает длительность показа уведомления.
                        ).show() // Показывает уведомление.
                    }
            }
        })
    }

    // ---------------------------------------------------------
    // Поиск игроков прямо на экране сообщений
    // ---------------------------------------------------------

    // Настраивает строку поиска игроков на основном экране чатов.
    private fun setupPlayerSearch(view: View) {
        val search = view.findViewById<EditText>(R.id.chatSearch) // Находит поле поиска на экране сообщений.
        val results = // Переменная для контейнера найденных игроков.
            view.findViewById<LinearLayout>(R.id.playerSearchResults) // Находит контейнер результатов поиска.

        val currentUid = FirebaseAuth.getInstance().currentUser?.uid // Получает ID текущего пользователя.

        search.addTextChangedListener(object : TextWatcher { // Добавляет обработчик изменения текста.
            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int // Содержит информацию о тексте до изменения.
            ) {} // Действия до изменения текста не требуются.

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int // Содержит новый текст и сведения об изменении.
            ) {
                val query = s?.toString()?.trim().orEmpty() // Получает поисковый запрос и удаляет пробелы по краям.
                results.removeAllViews() // Удаляет старые результаты поиска.

                if (query.isEmpty()) { // Проверяет, пустая ли строка поиска.
                    results.visibility = View.GONE // Скрывает контейнер результатов.
                    return // Завершает выполнение метода.
                }

                results.visibility = View.VISIBLE // Показывает контейнер результатов.

                db.collection("publicProfiles") // Обращается к коллекции публичных профилей.
                    .orderBy("username") // Сортирует профили по имени пользователя.
                    .startAt(query) // Начинает выборку с указанного текста.
                    .endAt(query + "\uf8ff") // Задаёт верхнюю границу выборки для поиска имён с таким началом.
                    .limit(20) // Ограничивает количество результатов двадцатью.
                    .get() // Выполняет запрос к Firestore.
                    .addOnSuccessListener { snapshot -> // Обрабатывает успешно полученные результаты.
                        results.removeAllViews() // Очищает контейнер перед добавлением найденных игроков.
                        var found = false // Запоминает, найден ли хотя бы один подходящий пользователь.

                        snapshot.documents.forEach { document -> // Перебирает документы с профилями.
                            val uid = // Переменная для ID пользователя.
                                document.getString("uid") ?: document.id // Получает UID или ID документа.
                            val username = // Переменная для имени пользователя.
                                document.getString("username") // Получает имя из документа.
                                    ?: return@forEach // Пропускает документ, если имя отсутствует.

                            if (uid == currentUid) return@forEach // Не показывает в результатах самого текущего пользователя.

                            found = true // Отмечает, что найден подходящий пользователь.

                            val item = TextView(activity).apply { // Создаёт элемент списка для найденного игрока.
                                text = "🎮  $username" // Устанавливает текст с именем пользователя.
                                setTextColor(Color.WHITE) // Устанавливает белый цвет текста.
                                textSize = 16f // Устанавливает размер текста.
                                setPadding(dp(16), dp(14), dp(16), dp(14)) // Задаёт внутренние отступы.
                                background = roundedBackground( // Устанавливает закруглённый фон.
                                    Color.rgb(26, 26, 32), 12 // Задаёт цвет и радиус углов.
                                )
                                isClickable = true // Разрешает нажатие на элемент.
                                isFocusable = true // Разрешает элементу получать фокус.
                            }

                            val params = LinearLayout.LayoutParams( // Создаёт параметры размещения элемента.
                                ViewGroup.LayoutParams.MATCH_PARENT, // Ширина равна ширине контейнера.
                                ViewGroup.LayoutParams.WRAP_CONTENT // Высота определяется содержимым.
                            ).apply {
                                topMargin = dp(4) // Добавляет небольшой отступ сверху.
                            }

                            results.addView(item, params) // Добавляет игрока в контейнер результатов.

                            val avatarUrl = // Переменная для ссылки на аватар пользователя.
                                document.getString("avatarUrl").orEmpty() // Получает ссылку на изображение или пустую строку.

                            item.setOnClickListener { // Обрабатывает нажатие на найденного игрока.
                                createPrivateChat( // Создаёт новый чат или открывает существующий.
                                    uid, // Передаёт ID собеседника.
                                    username, // Передаёт имя собеседника.
                                    avatarUrl // Передаёт ссылку на его аватар.
                                )
                            }
                        }

                        if (!found) { // Проверяет, не остались ли результаты пустыми.
                            results.addView(TextView(activity).apply { // Создаёт текстовый элемент с сообщением.
                                text = "Игроки не найдены" // Устанавливает текст сообщения.
                                setTextColor(Color.LTGRAY) // Устанавливает светло-серый цвет текста.
                                setPadding(dp(16), dp(14), dp(16), dp(14)) // Задаёт внутренние отступы.
                            })
                        }
                    }
                    .addOnFailureListener { // Выполняется, если поиск завершился ошибкой.
                        results.removeAllViews() // Удаляет содержимое контейнера.
                        results.addView(TextView(activity).apply { // Добавляет текстовый элемент с сообщением об ошибке.
                            text = "Не удалось загрузить игроков" // Устанавливает текст ошибки.
                            setTextColor(Color.LTGRAY) // Делает текст светло-серым.
                            setPadding(dp(16), dp(14), dp(16), dp(14)) // Задаёт отступы.
                        })
                    }
            }

            override fun afterTextChanged(s: Editable?) {} // Метод не используется, так как поиск выполняется в onTextChanged.
        })
    }

    // ---------------------------------------------------------
    // Создание чата или открытие уже существующего
    // ---------------------------------------------------------

    // Проверяет наличие переписки и создаёт её, если она ещё не существует.
    private fun createPrivateChat(
        otherUid: String, // ID пользователя, с которым нужно начать переписку.
        otherUsername: String, // Имя этого пользователя.
        otherAvatarUrl: String = "" // Ссылка на его аватар; по умолчанию пустая.
    ) {
        val currentUser = FirebaseAuth.getInstance().currentUser // Получает данные авторизованного пользователя.

        if (currentUser == null) { // Проверяет, выполнен ли вход в аккаунт.
            Toast.makeText( // Создаёт уведомление.
                activity, // Указывает текущий экран.
                "Сначала войди в аккаунт", // Текст уведомления.
                Toast.LENGTH_SHORT // Короткая длительность показа.
            ).show() // Показывает уведомление.
            return // Прерывает создание чата.
        }

        if (currentUser.uid == otherUid) { // Проверяет, не пытается ли пользователь написать самому себе.
            Toast.makeText( // Создаёт уведомление.
                activity, // Указывает текущий экран.
                "Нельзя создать чат с самим собой", // Сообщает о недопустимом действии.
                Toast.LENGTH_SHORT // Устанавливает короткую длительность.
            ).show() // Показывает уведомление.
            return // Прерывает выполнение функции.
        }

        db.collection("chats") // Обращается к коллекции чатов.
            .whereArrayContains("participants", currentUser.uid) // Ищет чаты, в которых участвует текущий пользователь.
            .get() // Загружает подходящие документы.
            .addOnSuccessListener { snapshot -> // Выполняется после успешной загрузки.
                val existingChat = snapshot.documents.firstOrNull { document -> // Ищет первый чат, где есть оба пользователя.
                    val participants = // Получает список участников чата.
                        document.get("participants") as? List<*> // Преобразует значение в список, если это возможно.
                    participants?.contains(otherUid) == true // Проверяет, присутствует ли второй пользователь в списке.
                }

                if (existingChat != null) { // Если подходящий чат уже существует:
                    openPrivateChat( // Открывает существующую переписку.
                        existingChat.id, // Передаёт ID найденного чата.
                        otherUsername, // Передаёт имя собеседника.
                        otherAvatarUrl // Передаёт ссылку на аватар.
                    )
                } else { // Если существующий чат не найден:
                    val participants = // Создаёт список участников нового чата.
                        listOf(currentUser.uid, otherUid).sorted() // Добавляет обоих пользователей и сортирует их ID.

                    val chatId = // Формирует уникальный ID чата.
                        "${participants[0]}_${participants[1]}" // Объединяет два отсортированных UID через подчёркивание.

                    val chatData = hashMapOf( // Создаёт набор полей, которые будут сохранены в Firestore.
                        "chatId" to chatId, // Сохраняет идентификатор чата.
                        "participants" to participants, // Сохраняет список участников.
                        "createdAt" to FieldValue.serverTimestamp(), // Сохраняет время создания по часам сервера.
                        "lastMessage" to "", // Изначально последнее сообщение пустое.
                        "lastMessageAt" to FieldValue.serverTimestamp() // Записывает время создания чата как начальное время последнего сообщения.
                    )

                    db.collection("chats") // Обращается к коллекции чатов.
                        .document(chatId) // Выбирает документ с созданным ID.
                        .set(chatData) // Сохраняет данные нового чата.
                        .addOnSuccessListener { // Выполняется, если запись прошла успешно.
                            openPrivateChat( // Открывает только что созданную переписку.
                                chatId, // Передаёт ID нового чата.
                                otherUsername, // Передаёт имя собеседника.
                                otherAvatarUrl // Передаёт ссылку на аватар собеседника.
                            )
                        }
                        .addOnFailureListener { error -> // Обрабатывает ошибку при создании чата.
                            Toast.makeText( // Создаёт всплывающее сообщение.
                                activity, // Указывает текущий экран.
                                "Не удалось создать чат: ${error.message}", // Добавляет описание ошибки к сообщению.
                                Toast.LENGTH_LONG // Устанавливает длительный показ.
                            ).show() // Показывает уведомление.
                        }
                }
            }
            .addOnFailureListener { error -> // Обрабатывает ошибку при загрузке существующих чатов.
                Toast.makeText( // Создаёт уведомление.
                    activity, // Указывает текущий экран.
                    "Не удалось загрузить чаты: ${error.message}", // Выводит описание ошибки.
                    Toast.LENGTH_LONG // Устанавливает длительность показа.
                ).show() // Показывает уведомление.
            }
    }

    // Сохраняет данные выбранного чата и открывает экран переписки.
    private fun openPrivateChat(
        chatId: String, // ID чата, который нужно открыть.
        username: String, // Имя собеседника.
        avatarUrl: String = "" // Ссылка на аватар собеседника.
    ) {
        currentChatId = chatId // Сохраняет ID открываемого чата.
        currentChatUsername = username // Сохраняет имя собеседника.
        currentChatAvatarUrl = avatarUrl // Сохраняет ссылку на аватар.
        openPage(R.layout.screen_private_chat) // Переключает приложение на экран личной переписки.
    }

    // ---------------------------------------------------------
    // Загрузка списка переписок из Firestore
    // ---------------------------------------------------------

    // Получает список чатов текущего пользователя и следит за его изменениями.
    private fun setupChatList(view: View) {
        val container = // Переменная для контейнера списка переписок.
            view.findViewById<LinearLayout>(R.id.chatsListContainer) // Находит контейнер чатов в XML.
        val emptyText = // Переменная для сообщения о состоянии списка.
            view.findViewById<TextView>(R.id.chatsEmptyText) // Находит текстовый элемент для пустого списка или загрузки.

        chatListener?.remove() // Отключает предыдущий слушатель, чтобы не создавать дубликаты.
        chatListener = null // Очищает ссылку на старый слушатель.

        val uid = FirebaseAuth.getInstance().currentUser?.uid // Получает UID текущего пользователя.

        if (uid == null) { // Если пользователь не авторизован:
            emptyText.text = "Войди в аккаунт, чтобы увидеть чаты" // Показывает сообщение о необходимости входа.
            emptyText.visibility = View.VISIBLE // Делает сообщение видимым.
            return // Завершает загрузку списка.
        }

        emptyText.text = "Загружаем чаты..." // Показывает состояние загрузки.
        emptyText.visibility = View.VISIBLE // Делает текст загрузки видимым.

        chatListener = db.collection("chats") // Начинает запрос к коллекции чатов.
            .whereArrayContains("participants", uid) // Выбирает чаты, в которых состоит текущий пользователь.
            .addSnapshotListener { snapshot, error -> // Подписывается на изменения данных в реальном времени.

                if (error != null) { // Проверяет, возникла ли ошибка при получении обновлений.
                    android.util.Log.e( // Записывает ошибку в системный журнал Android.
                        "GamerHubChats", // Указывает тег, по которому можно найти сообщение в Logcat.
                        "Ошибка списка чатов: ${error.message}", // Записывает описание ошибки.
                        error // Передаёт сам объект ошибки для подробного журнала.
                    )

                    emptyText.text = // Меняет текст состояния списка.
                        "Ошибка Firestore: ${error.code}\n${error.message}" // Показывает код и описание ошибки.
                    emptyText.visibility = View.VISIBLE // Показывает сообщение об ошибке.
                    return@addSnapshotListener // Завершает текущий вызов слушателя.
                }

                if (snapshot == null) { // Проверяет, пришли ли данные.
                    emptyText.text = "Ожидание данных чатов..." // Показывает сообщение об ожидании.
                    emptyText.visibility = View.VISIBLE // Делает сообщение видимым.
                    return@addSnapshotListener // Завершает обработку текущего обновления.
                }

                val documents = snapshot.documents.sortedByDescending { // Создаёт список чатов, отсортированный от новых к старым.
                    it.getTimestamp("lastMessageAt")?.toDate()?.time ?: 0L // Получает время последнего сообщения в миллисекундах или 0, если его нет.
                }

                container.removeAllViews() // Удаляет старые строки списка перед отрисовкой обновлённых данных.

                if (documents.isEmpty()) { // Проверяет, есть ли у пользователя чаты.
                    emptyText.text = // Устанавливает текст пустого состояния.
                        "У тебя пока нет чатов.\nНажми +, чтобы найти игрока." // Предлагает создать новую переписку.
                    emptyText.visibility = View.VISIBLE // Показывает сообщение.
                    return@addSnapshotListener // Завершает обработку, поскольку отображать нечего.
                }

                emptyText.visibility = View.GONE // Скрывает сообщение о загрузке или пустом списке.

                val rows = arrayOfNulls<ChatRowData>(documents.size) // Создаёт массив для подготовленных данных строк чатов.
                var remaining = documents.size // Считает, сколько профилей участников ещё нужно загрузить.

                documents.forEachIndexed { index, document -> // Перебирает чаты вместе с их индексами.
                    val participants = // Переменная для списка участников чата.
                        document.get("participants") as? List<*> // Получает список участников из документа.

                    val otherUid = participants // Начинает обработку списка участников.
                        ?.filterIsInstance<String>() // Оставляет только строковые значения.
                        ?.firstOrNull { it != uid } // Находит первого участника, который не является текущим пользователем.

                    if (otherUid == null) { // Если второй участник не найден:
                        remaining-- // Уменьшает число оставшихся загрузок.
                        if (remaining == 0) { // Проверяет, завершена ли обработка всех чатов.
                            renderChatRows(container, rows) // Отображает подготовленные строки.
                        }
                        return@forEachIndexed // Переходит к следующему чату.
                    }

                    db.collection("publicProfiles") // Обращается к коллекции публичных профилей.
                        .document(otherUid) // Выбирает профиль второго участника.
                        .get() // Загружает данные профиля.
                        .addOnSuccessListener { profile -> // Выполняется при успешной загрузке профиля.
                            rows[index] = ChatRowData( // Записывает подготовленные данные чата в массив.
                                chatId = document.id, // Сохраняет ID чата.
                                username = profile.getString("username") // Получает имя собеседника.
                                    ?: "Игрок", // Использует стандартное имя, если поле отсутствует.
                                avatarUrl = profile.getString("avatarUrl") // Получает ссылку на аватар.
                                    .orEmpty(), // Заменяет null пустой строкой.
                                lastMessage = document.getString("lastMessage") // Получает текст последнего сообщения.
                                    ?: "Начните общение!", // Показывает подсказку, если сообщений ещё нет.
                                lastMessageAt = // Начинает получение времени последнего сообщения.
                                    document.getTimestamp("lastMessageAt") // Получает временную метку из документа чата.
                            )

                            remaining-- // Отмечает, что профиль этого чата загружен.
                            if (remaining == 0) { // Проверяет, завершились ли все загрузки профилей.
                                renderChatRows(container, rows) // Отображает весь список чатов.
                            }
                        }
                        .addOnFailureListener { profileError -> // Выполняется, если профиль собеседника загрузить не удалось.
                            android.util.Log.e( // Записывает ошибку в Logcat.
                                "GamerHubChats", // Тег сообщений менеджера чатов.
                                "Не удалось загрузить профиль $otherUid: " + // Формирует описание ошибки.
                                        profileError.message, // Добавляет текст ошибки.
                                profileError // Передаёт объект ошибки.
                            )

                            rows[index] = ChatRowData( // Создаёт строку чата даже при недоступном профиле.
                                chatId = document.id, // Сохраняет ID чата.
                                username = "Игрок", // Подставляет стандартное имя.
                                avatarUrl = "", // Оставляет ссылку на аватар пустой.
                                lastMessage = document.getString("lastMessage") // Получает последнее сообщение.
                                    ?: "Начните общение!", // Подставляет подсказку, если сообщений нет.
                                lastMessageAt = // Получает время последнего сообщения.
                                    document.getTimestamp("lastMessageAt") // Читает временную метку из документа.
                            )

                            remaining-- // Уменьшает число незавершённых загрузок.
                            if (remaining == 0) { // Проверяет, обработаны ли все чаты.
                                renderChatRows(container, rows) // Отображает список, включая строки с недоступными профилями.
                            }
                        }
                }
            }
    }

    // ---------------------------------------------------------
    // Отрисовка списка чатов
    // ---------------------------------------------------------

    // Создаёт визуальные строки списка переписок.
    private fun renderChatRows(
        container: LinearLayout, // Контейнер, в который будут добавлены строки.
        rows: Array<ChatRowData?> // Массив с подготовленными данными чатов.
    ) {
        container.removeAllViews() // Очищает контейнер перед новой отрисовкой.

        rows.filterNotNull().forEach { chat -> // Убирает пустые элементы массива и перебирает оставшиеся чаты.
            val row = LinearLayout(activity).apply { // Создаёт горизонтальную строку одного чата.
                orientation = LinearLayout.HORIZONTAL // Располагает элементы по горизонтали.
                gravity = Gravity.CENTER_VERTICAL // Выравнивает содержимое по вертикальному центру.
                setPadding(dp(12), dp(12), dp(12), dp(12)) // Задаёт внутренние отступы строки.
                background = roundedBackground( // Устанавливает закруглённый фон.
                    Color.rgb(26, 26, 32), 14 // Задаёт цвет фона и радиус углов.
                )
                isClickable = true // Разрешает нажимать на строку.
                isFocusable = true // Разрешает строке получать фокус.
            }

            val avatar = ImageView(activity).apply { // Создаёт ImageView для аватара собеседника.
                scaleType = ImageView.ScaleType.CENTER_CROP // Заполняет область изображения с сохранением пропорций и обрезкой краёв.
                background = roundedBackground( // Задаёт фон аватара.
                    Color.rgb(48, 38, 62), 25 // Устанавливает цвет фона и большое закругление.
                )
                clipToOutline = true // Обрезает изображение по форме фона.
            }

            if (chat.avatarUrl.startsWith("http")) { // Проверяет, похожа ли ссылка на веб-адрес.
                Glide.with(activity) // Создаёт запрос Glide с контекстом Activity.
                    .load(chat.avatarUrl) // Указывает адрес изображения.
                    .placeholder(R.drawable.ic_launcher_foreground) // Показывает изображение приложения во время загрузки.
                    .error(R.drawable.ic_launcher_foreground) // Показывает изображение приложения, если загрузка завершилась ошибкой.
                    .circleCrop() // Обрезает аватар в круглую форму.
                    .into(avatar) // Загружает изображение в ImageView.
            } else { // Если ссылки на аватар нет:
                avatar.setImageResource(R.drawable.ic_launcher_foreground) // Показывает стандартное изображение приложения.
            }

            row.addView( // Добавляет аватар в строку чата.
                avatar, // Указывает добавляемый элемент.
                LinearLayout.LayoutParams(dp(50), dp(50)) // Задаёт ширину и высоту аватара по 50 dp.
            )

            val textColumn = LinearLayout(activity).apply { // Создаёт контейнер для имени и последнего сообщения.
                orientation = LinearLayout.VERTICAL // Располагает текстовые элементы вертикально.
            }

            val name = TextView(activity).apply { // Создаёт текстовый элемент с именем собеседника.
                text = chat.username // Устанавливает имя из данных чата.
                textSize = 15f // Задаёт размер текста.
                setTypeface(null, Typeface.BOLD) // Делает текст жирным.
                setTextColor(Color.WHITE) // Устанавливает белый цвет.
                maxLines = 1 // Ограничивает имя одной строкой.
            }

            val lastMessage = TextView(activity).apply { // Создаёт элемент для последнего сообщения.
                text = chat.lastMessage.ifBlank { "Начните общение!" } // Показывает текст сообщения или подсказку, если строка пустая.
                textSize = 13f // Устанавливает размер текста.
                setTextColor(Color.rgb(146, 146, 157)) // Задаёт серый цвет текста.
                maxLines = 1 // Ограничивает сообщение одной строкой.
                ellipsize = android.text.TextUtils.TruncateAt.END // Обрезает слишком длинный текст в конце.
            }

            textColumn.addView(name) // Добавляет имя в вертикальный контейнер.
            textColumn.addView(lastMessage) // Добавляет последнее сообщение под именем.

            val textParams = LinearLayout.LayoutParams( // Создаёт параметры для текстового контейнера.
                0, // Нулевая ширина позволяет использовать вес для распределения свободного места.
                ViewGroup.LayoutParams.WRAP_CONTENT, // Высота определяется содержимым.
                1f // Вес 1 заставляет контейнер занять доступное свободное пространство.
            ).apply {
                marginStart = dp(12) // Добавляет отступ слева от аватара.
            }

            row.addView(textColumn, textParams) // Добавляет имя и последнее сообщение в строку.

            val time = TextView(activity).apply { // Создаёт элемент для отображения времени последнего сообщения.
                text = formatTime(chat.lastMessageAt) // Преобразует временную метку в удобный текст.
                textSize = 11f // Устанавливает небольшой размер текста.
                setTextColor(Color.rgb(119, 119, 128)) // Задаёт серый цвет времени.
            }

            val timeParams = LinearLayout.LayoutParams( // Создаёт параметры размещения времени.
                ViewGroup.LayoutParams.WRAP_CONTENT, // Ширина зависит от текста.
                ViewGroup.LayoutParams.WRAP_CONTENT // Высота зависит от текста.
            ).apply {
                marginStart = dp(8) // Добавляет отступ слева от времени.
            }

            row.addView(time, timeParams) // Добавляет время в правую часть строки.

            val rowParams = LinearLayout.LayoutParams( // Создаёт параметры всей строки чата.
                ViewGroup.LayoutParams.MATCH_PARENT, // Строка занимает всю ширину контейнера.
                ViewGroup.LayoutParams.WRAP_CONTENT // Высота зависит от содержимого.
            ).apply {
                bottomMargin = dp(10) // Добавляет расстояние между соседними чатами.
            }

            container.addView(row, rowParams) // Добавляет строку чата в общий список.

            row.setOnClickListener { // Обрабатывает нажатие на строку переписки.
                currentChatId = chat.chatId // Сохраняет ID выбранного чата.
                currentChatUsername = chat.username // Сохраняет имя собеседника.
                currentChatAvatarUrl = chat.avatarUrl // Сохраняет ссылку на аватар собеседника.
                openPage(R.layout.screen_private_chat) // Открывает экран личной переписки.
            }
        }
    }

    // ---------------------------------------------------------
    // Экран переписки: история и отправка сообщений
    // ---------------------------------------------------------

    // Настраивает личную переписку, отправку сообщений и получение новых сообщений.
    private fun setupPrivateChat(view: View) {
        val chatId = currentChatId ?: run { // Получает ID чата или выполняет блок, если ID отсутствует.
            Toast.makeText( // Создаёт уведомление.
                activity, // Указывает текущий экран.
                "Не удалось открыть чат", // Текст ошибки.
                Toast.LENGTH_SHORT // Короткая длительность показа.
            ).show() // Показывает уведомление.
            openPage(R.layout.screen_chats) // Возвращает пользователя к списку переписок.
            return // Завершает настройку личного чата.
        }

        val title = view.findViewById<TextView>(R.id.chatTitle) // Находит заголовок с именем собеседника.
        val backButton = view.findViewById<TextView>(R.id.backButton) // Находит кнопку возврата к списку чатов.
        val input = view.findViewById<EditText>(R.id.messageInput) // Находит поле ввода сообщения.
        val sendButton = view.findViewById<TextView>(R.id.sendButton) // Находит кнопку отправки сообщения.

        val messagesContainer = // Переменная для контейнера сообщений.
            view.findViewById<LinearLayout>(R.id.messagesContainer) // Находит LinearLayout, в который будут добавляться сообщения.

        val messagesScroll = // Переменная для прокручиваемой области сообщений.
            view.findViewById<ScrollView>(R.id.messagesScroll) // Находит ScrollView в XML-разметке.

        title.text = currentChatUsername // Показывает имя собеседника в заголовке.

        val avatar = view.findViewById<ImageView>(R.id.chatAvatar) // Находит ImageView аватара в заголовке чата.

        // Открытие публичного профиля собеседника.
        val openChatProfile = { // Создаёт действие, которое открывает профиль второго участника.
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid // Получает ID текущего пользователя.

            if (currentUid == null) { // Проверяет, авторизован ли пользователь.
                Toast.makeText( // Создаёт всплывающее уведомление.
                    activity, // Указывает текущий экран.
                    "Сначала войди в аккаунт", // Сообщает о необходимости авторизации.
                    Toast.LENGTH_SHORT // Устанавливает короткую длительность.
                ).show() // Показывает уведомление.
            } else { // Если пользователь авторизован:
                db.collection("chats") // Обращается к коллекции чатов.
                    .document(chatId) // Выбирает текущий чат.
                    .get() // Загружает документ чата.
                    .addOnSuccessListener { document -> // Обрабатывает успешно загруженный документ.
                        val participants = document.get("participants") // Получает список участников.
                                as? List<*> // Преобразует значение в список, если это возможно.

                        val otherUid = participants // Начинает поиск UID собеседника.
                            ?.filterIsInstance<String>() // Оставляет только строковые значения.
                            ?.firstOrNull { it != currentUid } // Выбирает участника, который не является текущим пользователем.

                        if (!otherUid.isNullOrBlank()) { // Проверяет, удалось ли получить UID собеседника.
                            openPublicProfile(otherUid) { // Открывает публичный профиль найденного пользователя.
                                // Возвращаемся в эту переписку.
                                openPage(R.layout.screen_private_chat) // После закрытия профиля снова открывает текущий чат.
                            }
                        } else { // Если UID второго участника не найден:
                            Toast.makeText( // Создаёт уведомление.
                                activity, // Указывает текущий экран.
                                "Не удалось найти профиль собеседника", // Текст сообщения.
                                Toast.LENGTH_SHORT // Короткая длительность.
                            ).show() // Показывает уведомление.
                        }
                    }
                    .addOnFailureListener { // Выполняется, если загрузить документ чата не удалось.
                        Toast.makeText( // Создаёт уведомление.
                            activity, // Указывает текущий экран.
                            "Не удалось открыть профиль", // Текст ошибки.
                            Toast.LENGTH_SHORT // Короткая длительность.
                        ).show() // Показывает уведомление.
                    }
            }
        }

        // Нажатие на аватарку.
        avatar.setOnClickListener { // Назначает действие при нажатии на изображение собеседника.
            openChatProfile() // Запускает открытие публичного профиля.
        }

        // Нажатие на никнейм.
        title.setOnClickListener { // Назначает действие при нажатии на имя собеседника.
            openChatProfile() // Запускает открытие публичного профиля.
        }

        if (currentChatAvatarUrl.startsWith("http")) { // Проверяет, есть ли корректная веб-ссылка на аватар.
            Glide.with(avatar) // Создаёт запрос Glide с привязкой к ImageView.
                .load(currentChatAvatarUrl) // Указывает адрес изображения.
                .placeholder(R.drawable.ic_launcher_foreground) // Показывает стандартное изображение во время загрузки.
                .error(R.drawable.ic_launcher_foreground) // Показывает стандартное изображение при ошибке.
                .circleCrop() // Обрезает изображение по кругу.
                .into(avatar) // Загружает изображение в аватар.
        } else { // Если ссылка отсутствует или не начинается с http:
            avatar.setImageResource(R.drawable.ic_launcher_foreground) // Устанавливает стандартный аватар приложения.
        }

        backButton.setOnClickListener { // Назначает действие кнопке возврата.
            openPage(R.layout.screen_chats) // Открывает список переписок.
        }

        fun sendMessage() { // Объявляет локальную функцию отправки сообщения.
            val text = input.text.toString().trim() // Получает текст из поля ввода и удаляет пробелы по краям.

            if (text.isEmpty()) return // Не отправляет пустое сообщение.

            val uid = FirebaseAuth.getInstance().currentUser?.uid // Получает ID пользователя, отправляющего сообщение.

            if (uid == null) { // Проверяет, выполнен ли вход в аккаунт.
                Toast.makeText( // Создаёт уведомление.
                    activity, // Указывает текущий экран.
                    "Сначала войди в аккаунт", // Текст уведомления.
                    Toast.LENGTH_SHORT // Короткая длительность показа.
                ).show() // Показывает уведомление.
                return // Прерывает отправку сообщения.
            }

            val chatRef = db.collection("chats").document(chatId) // Получает ссылку на документ текущего чата.
            val messageRef = chatRef.collection("messages").document() // Создаёт ссылку на новый документ сообщения с автоматически созданным ID.

            val message = hashMapOf( // Формирует набор данных нового сообщения.
                "senderId" to uid, // Сохраняет ID отправителя.
                "text" to text, // Сохраняет текст сообщения.
                "createdAt" to FieldValue.serverTimestamp(), // Сохраняет время отправки по часам сервера.
                "readBy" to listOf(uid) // Добавляет отправителя в список пользователей, прочитавших сообщение.
            )

            sendButton.isEnabled = false // Временно отключает кнопку, чтобы избежать повторной отправки.

            messageRef.set(message) // Сохраняет сообщение в подколлекции messages.
                .addOnSuccessListener { // Выполняется, если сообщение успешно записано.
                    chatRef.update( // Обновляет информацию о последнем сообщении в документе чата.
                        mapOf( // Создаёт набор полей для обновления.
                            "lastMessage" to text, // Сохраняет текст последнего сообщения.
                            "lastMessageAt" to FieldValue.serverTimestamp() // Обновляет время последнего сообщения.
                        )
                    ).addOnFailureListener { error -> // Обрабатывает ошибку обновления информации о чате.
                        Toast.makeText( // Создаёт уведомление.
                            activity, // Указывает текущий экран.
                            "Сообщение отправлено, но превью чата " + // Начало текста уведомления.
                                    "не обновилось: ${error.message}", // Добавляет описание ошибки.
                            Toast.LENGTH_LONG // Устанавливает длительный показ.
                        ).show() // Показывает уведомление.
                    }

                    input.text.clear() // Очищает поле ввода после успешной отправки.
                    sendButton.isEnabled = true // Снова включает кнопку отправки.
                }
                .addOnFailureListener { error -> // Выполняется, если сохранить сообщение не удалось.
                    sendButton.isEnabled = true // Возвращает возможность нажимать кнопку.

                    Toast.makeText( // Создаёт уведомление об ошибке.
                        activity, // Указывает текущий экран.
                        "Не удалось отправить сообщение: ${error.message}", // Показывает описание ошибки.
                        Toast.LENGTH_LONG // Устанавливает длительность показа.
                    ).show() // Показывает уведомление.
                }
        }

        sendButton.setOnClickListener { // Назначает обработчик нажатия кнопки отправки.
            sendMessage() // Вызывает функцию отправки сообщения.
        }

        input.setOnEditorActionListener { _, actionId, event -> // Обрабатывает действия клавиатуры в поле ввода.
            val sendAction = actionId == EditorInfo.IME_ACTION_SEND // Проверяет, нажата ли кнопка отправки на клавиатуре.

            val enterPressed = // Создаёт условие для определения нажатия Enter.
                event?.keyCode == KeyEvent.KEYCODE_ENTER && // Проверяет, что нажата клавиша Enter.
                        event.action == KeyEvent.ACTION_UP && // Проверяет, что событие соответствует отпусканию клавиши.
                        !event.isShiftPressed // Проверяет, что Shift не зажат.

            if (sendAction || enterPressed) { // Если нажата экранная кнопка отправки или Enter:
                sendMessage() // Отправляет сообщение.
                true // Сообщает системе, что действие обработано.
            } else { // Если нажато другое действие:
                false // Позволяет системе обработать событие стандартным способом.
            }
        }

        messagesListener?.remove() // Отключает предыдущий слушатель сообщений, если он существовал.

        messagesListener = db.collection("chats") // Обращается к коллекции чатов.
            .document(chatId) // Выбирает текущий чат.
            .collection("messages") // Переходит к подколлекции сообщений.
            .orderBy("createdAt") // Сортирует сообщения по времени создания от старых к новым.
            .addSnapshotListener { snapshot, error -> // Подписывается на изменения сообщений в реальном времени.
                if (error != null) { // Проверяет, возникла ли ошибка получения сообщений.
                    Toast.makeText( // Создаёт уведомление.
                        activity, // Указывает текущий экран.
                        "Не удалось загрузить сообщения: ${error.message}", // Показывает описание ошибки.
                        Toast.LENGTH_SHORT // Устанавливает короткую длительность.
                    ).show() // Показывает уведомление.
                    return@addSnapshotListener // Завершает обработку текущего обновления.
                }

                if (snapshot == null) return@addSnapshotListener // Завершает обработку, если снимок данных отсутствует.

                messagesContainer.removeAllViews() // Удаляет старые сообщения перед отрисовкой актуального списка.

                val currentUid = // Переменная для ID текущего пользователя.
                    FirebaseAuth.getInstance().currentUser?.uid.orEmpty() // Получает UID или пустую строку, если пользователь не авторизован.

                snapshot.documents.forEach { document -> // Перебирает все документы сообщений.
                    val senderId = document.getString("senderId").orEmpty() // Получает ID отправителя сообщения.
                    val text = document.getString("text") ?: return@forEach // Получает текст; пропускает документ, если текста нет.
                    val isMine = senderId == currentUid // Проверяет, отправлено ли сообщение текущим пользователем.

                    val readBy = (document.get("readBy") as? List<*>) // Получает список пользователей, прочитавших сообщение.
                        ?.filterIsInstance<String>() // Оставляет только строковые значения.
                        ?: emptyList() // Если список отсутствует, использует пустой список.

                    // Помечаем входящее сообщение прочитанным.
                    if (
                        !isMine && // Проверяет, что сообщение отправил не текущий пользователь.
                        currentUid.isNotEmpty() && // Проверяет, что ID текущего пользователя получен.
                        currentUid !in readBy // Проверяет, что текущего пользователя ещё нет в списке прочитавших.
                    ) {
                        document.reference.update( // Обновляет документ сообщения.
                            "readBy", // Указывает поле, которое нужно изменить.
                            FieldValue.arrayUnion(currentUid) // Добавляет UID в массив, не создавая дубликат.
                        )
                    }

                    // Проверяем, прочитал ли сообщение собеседник.
                    val isReadByRecipient = // Создаёт признак прочтения сообщения получателем.
                        isMine && readBy.any { it != senderId } // Для своих сообщений проверяет наличие в readBy другого пользователя.

                    val timestamp = document.getTimestamp("createdAt") // Получает время создания сообщения.
                    val timeText = timestamp?.toDate()?.let { // Преобразует временную метку в дату, если она существует.
                        SimpleDateFormat( // Создаёт форматировщик времени.
                            "HH:mm", // Указывает формат часов и минут.
                            Locale.getDefault() // Использует региональные настройки устройства.
                        ).format(it) // Преобразует дату в строку.
                    } ?: "" // Если время отсутствует, использует пустую строку.

                    val wrapper = LinearLayout(activity).apply { // Создаёт внешний контейнер для сообщения.
                        orientation = LinearLayout.HORIZONTAL // Располагает содержимое по горизонтали.
                        gravity = if (isMine) Gravity.END else Gravity.START // Свои сообщения выравнивает вправо, входящие — влево.
                    }

                    val bubble = LinearLayout(activity).apply { // Создаёт контейнер-пузырь сообщения.
                        orientation = LinearLayout.VERTICAL // Располагает текст и метаданные вертикально.
                        setPadding(dp(14), dp(10), dp(14), dp(8)) // Задаёт внутренние отступы пузыря.

                        background = roundedBackground( // Устанавливает фон пузыря с закруглёнными углами.
                            if (isMine) { // Проверяет, кому принадлежит сообщение.
                                Color.rgb(112, 58, 180) // Использует фиолетовый цвет для своих сообщений.
                            } else { // Если сообщение входящее:
                                Color.rgb(40, 40, 48) // Использует тёмно-серый цвет.
                            },
                            16 // Устанавливает радиус закругления.
                        )
                    }

                    val messageText = TextView(activity).apply { // Создаёт текстовый элемент для содержимого сообщения.
                        this.text = text // Устанавливает текст сообщения.
                        textSize = 15f // Задаёт размер текста.
                        setTextColor(Color.WHITE) // Устанавливает белый цвет.
                    }

                    val metadata = TextView(activity).apply { // Создаёт элемент для времени и статуса прочтения.
                        val status = if (isMine) { // Проверяет, отправлено ли сообщение текущим пользователем.
                            if (isReadByRecipient) "✓✓" else "✓" // Показывает две галочки при прочтении и одну, если сообщение ещё не прочитано.
                        } else { // Для входящих сообщений:
                            "" // Не показывает статус прочтения отправителю.
                        }

                        this.text = listOf(timeText, status) // Создаёт список из времени и статуса.
                            .filter { it.isNotEmpty() } // Убирает пустые значения.
                            .joinToString("  ") // Объединяет оставшиеся значения через два пробела.

                        textSize = 11f // Устанавливает небольшой размер текста.
                        setTextColor(Color.rgb(210, 195, 230)) // Задаёт светлый оттенок текста.
                        gravity = Gravity.END // Выравнивает время и статус по правому краю.
                    }

                    bubble.addView(messageText) // Добавляет текст сообщения в пузырь.
                    bubble.addView(metadata) // Добавляет время и статус под текстом.

                    val bubbleParams = LinearLayout.LayoutParams( // Создаёт параметры размещения пузыря.
                        ViewGroup.LayoutParams.WRAP_CONTENT, // Ширина зависит от содержимого.
                        ViewGroup.LayoutParams.WRAP_CONTENT // Высота зависит от содержимого.
                    ).apply {
                        bottomMargin = dp(8) // Добавляет отступ снизу между сообщениями.
                        marginStart = dp(36) // Добавляет отступ слева.
                        marginEnd = dp(36) // Добавляет отступ справа.
                    }

                    wrapper.addView(bubble, bubbleParams) // Добавляет пузырь во внешний контейнер.

                    messagesContainer.addView( // Добавляет контейнер сообщения в общий список.
                        wrapper, // Передаёт созданный контейнер.
                        LinearLayout.LayoutParams( // Создаёт параметры размещения сообщения.
                            ViewGroup.LayoutParams.MATCH_PARENT, // Сообщение занимает всю доступную ширину строки.
                            ViewGroup.LayoutParams.WRAP_CONTENT // Высота зависит от содержимого.
                        )
                    )
                }

                messagesScroll.post { // Выполняет действие после обновления интерфейса.
                    messagesScroll.fullScroll(View.FOCUS_DOWN) // Прокручивает переписку к самому последнему сообщению.
                }
            }
    }

    // ---------------------------------------------------------
    // Вспомогательные функции оформления
    // ---------------------------------------------------------

    // Создаёт фон заданного цвета с закруглёнными углами.
    private fun roundedBackground(
        color: Int, // Цвет создаваемого фона.
        radiusDp: Int // Радиус закругления углов в dp.
    ): GradientDrawable { // Указывает, что функция возвращает объект GradientDrawable.
        return GradientDrawable().apply { // Создаёт новый фон и сразу настраивает его.
            setColor(color) // Устанавливает цвет фона.
            cornerRadius = dp(radiusDp).toFloat() // Переводит радиус из dp в пиксели и задаёт закругление.
        }
    }

    // Переводит значение из dp в пиксели с учётом плотности экрана устройства.
    private fun dp(value: Int): Int {
        return ( // Начинает вычисление итогового значения.
                value * activity.resources.displayMetrics.density // Умножает dp на плотность экрана.
                ).toInt() // Преобразует результат в целое число пикселей.
    }

    // Преобразует временную метку в строку для отображения в списке чатов.
    private fun formatTime(timestamp: Timestamp?): String {
        if (timestamp == null) return "" // Если времени нет, возвращает пустую строку.

        val date = timestamp.toDate() // Преобразует Timestamp Firebase в обычный объект Date.

        val today = SimpleDateFormat( // Создаёт форматировщик для определения текущего дня.
            "yyyyMMdd", // Форматирует дату как год, месяц и день без разделителей.
            Locale.getDefault() // Использует региональные настройки устройства.
        ).format(Date()) // Форматирует текущую дату.

        val messageDay = SimpleDateFormat( // Создаёт форматировщик для даты сообщения.
            "yyyyMMdd", // Использует тот же формат, что и для текущего дня.
            Locale.getDefault() // Использует региональные настройки устройства.
        ).format(date) // Форматирует дату сообщения.

        val pattern = if (today == messageDay) "HH:mm" else "dd.MM" // Для сегодняшних сообщений показывает время, для остальных — день и месяц.

        return SimpleDateFormat( // Создаёт форматировщик итоговой строки.
            pattern, // Передаёт выбранный выше формат.
            Locale.getDefault() // Использует региональные настройки устройства.
        ).format(date) // Возвращает отформатированную дату или время.
    }
}