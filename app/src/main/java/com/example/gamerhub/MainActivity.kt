package com.example.gamerhub

// Intent используется для перехода между Activity.
import android.content.Intent

// Color позволяет задавать цвета элементов интерфейса.
import android.graphics.Color

// Uri представляет адрес выбранного изображения.
import android.net.Uri

// Bundle используется для передачи и восстановления состояния Activity.
import android.os.Bundle

// SpannableString позволяет форматировать отдельные части текста.
import android.text.SpannableString

// Spanned содержит настройки форматирования текста.
import android.text.Spanned

// ForegroundColorSpan задаёт цвет определённой части строки.
import android.text.style.ForegroundColorSpan

// LayoutInflater создаёт View на основе XML-разметки.
import android.view.LayoutInflater

// View — базовый класс элементов интерфейса.
import android.view.View

// FrameLayout используется как контейнер для сменяемых экранов.
import android.widget.FrameLayout

// ImageView отображает изображения.
import android.widget.ImageView

// LinearLayout используется в нижней панели навигации.
import android.widget.LinearLayout

// TextView отображает текст.
import android.widget.TextView

// Toast показывает короткие сообщения пользователю.
import android.widget.Toast

// Позволяет использовать edge-to-edge отображение интерфейса.
import androidx.activity.enableEdgeToEdge

// Контракт для выбора документа из памяти устройства.
import androidx.activity.result.contract.ActivityResultContracts

// Базовый класс Activity из AndroidX.
import androidx.appcompat.app.AppCompatActivity

// ViewCompat и WindowInsetsCompat помогают обрабатывать системные отступы.
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

// lifecycleScope позволяет запускать корутины,
// связанные с жизненным циклом Activity.
import androidx.lifecycle.lifecycleScope

// GridLayoutManager размещает игры в виде сетки.
import androidx.recyclerview.widget.GridLayoutManager

// RecyclerView отображает прокручиваемый список элементов.
import androidx.recyclerview.widget.RecyclerView

// Room используется для создания локальной базы данных.
import androidx.room.Room

// Migration описывает переход с одной версии базы данных на другую.
import androidx.room.migration.Migration

// SupportSQLiteDatabase позволяет выполнять SQL-запросы при миграции.
import androidx.sqlite.db.SupportSQLiteDatabase

// Glide загружает изображения, в том числе из интернета.
import com.bumptech.glide.Glide

// FirebaseAuth позволяет проверить текущего пользователя.
import com.google.firebase.auth.FirebaseAuth

// Dispatchers определяет поток выполнения корутины.
import kotlinx.coroutines.Dispatchers

// launch запускает корутину.
import kotlinx.coroutines.launch

// withContext переключает выполнение на другой диспетчер.
import kotlinx.coroutines.withContext

// await позволяет дождаться завершения задачи Firebase.
import kotlinx.coroutines.tasks.await


// Главная Activity приложения GamerHub.
//
// MainActivity отвечает за:
// - проверку авторизации;
// - создание базы данных Room;
// - инициализацию менеджеров;
// - переключение экранов;
// - нижнюю панель навигации;
// - открытие публикаций и профилей;
// - отображение каталога игр.
class MainActivity : AppCompatActivity() {

    // Менеджер уведомлений.
    // Отвечает за отображение уведомлений и обработку нажатий на них.
    private lateinit var notificationsManager: NotificationsManager

    // Контейнер, внутри которого отображаются разные экраны приложения.
    // Вместо создания отдельной Activity для каждой страницы
    // мы заменяем содержимое этого контейнера.
    private lateinit var pageContainer: FrameLayout

    // Менеджер главной страницы.
    // Управляет категориями, поиском и каталогом игр.
    private lateinit var homeManager: HomeManager

    // Менеджер публикаций.
    // Отвечает за загрузку и отображение постов.
    private lateinit var postsManager: PostsManager

    // Менеджер мессенджера.
    // Управляет списком чатов и личными переписками.
    private lateinit var chatsManager: ChatsManager

    // Менеджер создания и редактирования публикаций.
    private lateinit var createPostManager: CreatePostManager

    // Временное хранение функции, которая должна получить выбранный аватар.
    //
    // Uri — адрес изображения, выбранного пользователем.
    // Функция вызывается после успешного выбора изображения.
    private var avatarPickCallback: ((Uri) -> Unit)? = null


    // Контракт для открытия системного окна выбора документа.
    //
    // ActivityResultContracts.OpenDocument() позволяет выбрать файл,
    // в нашем случае изображение из галереи или другого хранилища.
    //
    // registerForActivityResult регистрирует обработчик результата выбора.
    private val avatarPicker =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            // Если пользователь действительно выбрал изображение,
            // uri будет не равен null.
            if (uri != null) {

                try {

                    // Сохраняем разрешение на чтение выбранного файла.
                    //
                    // Это позволяет приложению продолжать обращаться
                    // к изображению даже после закрытия окна выбора.
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )

                    // Передаём полученный Uri в функцию,
                    // которую ранее сохранили в avatarPickCallback.
                    avatarPickCallback?.invoke(uri)

                } catch (e: Exception) {

                    // Если получить доступ к изображению не удалось,
                    // показываем сообщение об ошибке.
                    Toast.makeText(
                        this,
                        "Не удалось открыть фотографию",
                        Toast.LENGTH_SHORT
                    ).show()

                } finally {

                    // В любом случае очищаем callback,
                    // чтобы случайно не вызвать его повторно.
                    avatarPickCallback = null
                }
            }
        }


    // Эту функцию вызывает ProfileManager,
    // когда пользователю нужно выбрать новое изображение аватара.
    fun openAvatarPicker(onPicked: (Uri) -> Unit) {

        // Сохраняем функцию, которая должна обработать выбранное изображение.
        avatarPickCallback = onPicked

        // Запускаем системный выбор файлов.
        // image/* означает, что разрешено выбирать изображения любого типа.
        avatarPicker.launch(arrayOf("image/*"))
    }


    // ============================================================
    // DATABASE — НАСТРОЙКА ЛОКАЛЬНОЙ БАЗЫ ДАННЫХ
    // ============================================================

    // companion object содержит свойства и методы,
    // которые принадлежат самому классу, а не отдельному объекту Activity.
    companion object {

        // Миграция с версии базы данных 4 на версию 5.
        //
        // Migration(4, 5):
        // первое число — старая версия;
        // второе число — новая версия.
        private val MIGRATION_4_5 = object : Migration(4, 5) {

            // Метод выполняется при переходе с версии 4 на версию 5.
            override fun migrate(db: SupportSQLiteDatabase) {

                // Создаём новую таблицу user_profiles_new.
                //
                // CREATE TABLE IF NOT EXISTS означает,
                // что таблица создаётся только в том случае,
                // если она ещё не существует.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS user_profiles_new (
                        uid TEXT NOT NULL,
                        username TEXT NOT NULL,
                        tag TEXT NOT NULL,
                        bio TEXT NOT NULL,
                        PRIMARY KEY(uid)
                    )
                    """.trimIndent()
                )

                // Переносим данные из старой таблицы user_profiles
                // в новую таблицу user_profiles_new.
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO user_profiles_new
                        (uid, username, tag, bio)
                    SELECT '', username, tag, bio
                    FROM user_profiles
                    WHERE id = 1
                    """.trimIndent()
                )

                // Удаляем старую таблицу.
                db.execSQL("DROP TABLE user_profiles")

                // Переименовываем новую таблицу,
                // возвращая ей первоначальное название.
                db.execSQL(
                    "ALTER TABLE user_profiles_new RENAME TO user_profiles"
                )
            }
        }
    }


    // Миграция с версии 5 на версию 6.
    //
    // В таблицу user_profiles добавляются два новых столбца:
    // roles — роли пользователя;
    // avatarUri — адрес изображения аватара.
    private val MIGRATION_5_6 = object : Migration(5, 6) {

        override fun migrate(db: SupportSQLiteDatabase) {

            // Добавляем столбец roles.
            // DEFAULT 'Геймер' задаёт значение по умолчанию
            // для уже существующих записей.
            db.execSQL(
                "ALTER TABLE user_profiles ADD COLUMN roles TEXT NOT NULL DEFAULT 'Геймер'"
            )

            // Добавляем столбец avatarUri.
            // Для существующих записей по умолчанию используется пустая строка.
            db.execSQL(
                "ALTER TABLE user_profiles ADD COLUMN avatarUri TEXT NOT NULL DEFAULT ''"
            )
        }
    }


    // Создаём базу данных лениво.
    //
    // by lazy означает, что база данных будет создана
    // только при первом обращении к переменной database.
    private val database by lazy {

        // Room.databaseBuilder создаёт конфигурацию базы данных.
        Room.databaseBuilder(

            // Контекст приложения.
            applicationContext,

            // Класс, описывающий структуру базы данных.
            GamerHubDatabase::class.java,

            // Имя файла базы данных на устройстве.
            "gamerhub_database"
        )

            // Регистрируем миграции между версиями.
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6)

            // Если Room не найдёт подходящую миграцию,
            // база будет пересоздана.
            //
            // ВАЖНО: это может привести к удалению локальных данных.
            .fallbackToDestructiveMigration()

            // Создаём объект базы данных.
            .build()
    }


    // Создаём репозиторий игр.
    //
    // Он также создаётся только при первом обращении благодаря lazy.
    // В GameRepository передаём DAO для доступа к таблицам Room.
    private val repository by lazy {
        GameRepository(database.gameDao())
    }


    // ============================================================
    // OPEN POST FROM NOTIFICATION — ОТКРЫТИЕ ПОСТА ИЗ УВЕДОМЛЕНИЯ
    // ============================================================

    // Открывает публикацию по её ID.
    //
    // Например, пользователь нажал на уведомление о комментарии.
    // Из уведомления мы получаем postId и загружаем соответствующую публикацию.
    private fun openPostFromNotification(postId: String) {

        // Проверяем, что ID публикации не пустой.
        if (postId.isBlank()) {

            // Если ID отсутствует, сообщаем об ошибке.
            Toast.makeText(
                this,
                "Не удалось определить публикацию",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        // Запускаем корутину, связанную с жизненным циклом Activity.
        lifecycleScope.launch {

            try {

                // Получаем документ публикации из Firestore.
                // await() позволяет дождаться завершения Firebase-запроса
                // без блокировки основного потока.
                val document = com.google.firebase.firestore
                    .FirebaseFirestore.getInstance()
                    .collection("posts")
                    .document(postId)
                    .get()
                    .await()

                // Проверяем, существует ли документ.
                if (!document.exists()) {

                    Toast.makeText(
                        this@MainActivity,
                        "Эта публикация больше не существует",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@launch
                }

                // Преобразуем документ Firestore в объект Post.
                //
                // copy(id = document.id) дополнительно устанавливает
                // настоящий ID документа Firestore в объект публикации.
                val post = document.toObject(Post::class.java)
                    ?.copy(id = document.id)

                // Проверяем, удалось ли преобразовать документ.
                if (post == null) {

                    Toast.makeText(
                        this@MainActivity,
                        "Не удалось открыть публикацию",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@launch
                }

                // Открываем экран подробного просмотра публикации.
                openPostDetails(post)

            } catch (e: Exception) {

                // Записываем подробности ошибки в Logcat.
                android.util.Log.e(
                    "MainActivity",
                    "Не удалось открыть публикацию из уведомления",
                    e
                )

                // Показываем пользователю сообщение об ошибке.
                Toast.makeText(
                    this@MainActivity,
                    "Ошибка загрузки публикации",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


    // ============================================================
    // ON CREATE — СОЗДАНИЕ ГЛАВНОГО ЭКРАНА
    // ============================================================

    // onCreate вызывается при создании MainActivity.
    override fun onCreate(savedInstanceState: Bundle?) {

        // Вызываем реализацию родительского класса.
        super.onCreate(savedInstanceState)


        // Проверяем, авторизован ли пользователь через Firebase.
        if (FirebaseAuth.getInstance().currentUser == null) {

            // Если пользователь не вошёл в аккаунт,
            // перенаправляем его на экран авторизации.
            startActivity(Intent(this, LoginActivity::class.java))

            // Закрываем MainActivity.
            finish()

            // Прерываем дальнейшую настройку главного экрана.
            return
        }


        // Включаем режим edge-to-edge.
        // Интерфейс сможет отображаться под системными панелями Android.
        enableEdgeToEdge()

        // Подключаем XML-разметку главного экрана.
        setContentView(R.layout.activity_main)


        // Создаём менеджер уведомлений.
        notificationsManager = NotificationsManager(

            // Обработчик нажатия на уведомление.
            // Получаем ID публикации и открываем её.
            onNotificationClick = { postId ->
                openPostFromNotification(postId)
            },

            // Обработчик нажатия на профиль автора уведомления.
            onProfileClick = { userId ->

                // Открываем публичный профиль пользователя.
                // После возврата снова открываем страницу уведомлений.
                openPublicProfile(userId) {
                    openPage(R.layout.screen_notifications)
                }
            }
        )


        // Находим основной контейнер страниц в activity_main.xml.
        pageContainer = findViewById(R.id.pageContainer)


        // Создаём менеджер публикаций.
        postsManager = PostsManager(this)

        // Назначаем обработчик нажатия на публикацию.
        postsManager.setOnPostClickListener { post ->

            // Открываем подробную страницу выбранной публикации.
            openPostDetails(post)
        }

        // Назначаем обработчик нажатия на автора публикации.
        postsManager.setOnAuthorClickListener { authorId ->

            // Проверяем, что ID автора не пустой.
            if (authorId.isNotBlank()) {

                // Открываем публичный профиль автора.
                openPublicProfile(authorId)
            }
        }


        // ========================================================
        // СОЗДАНИЕ МЕНЕДЖЕРОВ
        // ========================================================

        // Создаём менеджер чатов.
        chatsManager = ChatsManager(

            // Передаём текущую Activity.
            activity = this,

            // Передаём функцию для переключения экранов.
            openPage = { layoutId ->
                openPage(layoutId)
            },

            // Передаём функцию открытия публичного профиля.
            // onBack позволяет менеджеру определить,
            // куда вернуться после просмотра профиля.
            openPublicProfile = { userId, onBack ->
                this@MainActivity.openPublicProfile(userId, onBack)
            }
        )


        // Создаём менеджер публикаций.
        createPostManager = CreatePostManager(
            this
        )


        // Создаём менеджер главной страницы.
        homeManager = HomeManager(

            // Передаём Activity.
            activity = this,

            // Передаём репозиторий игр.
            repository = repository,

            // Передаём менеджер публикаций.
            postsManager = postsManager,

            // Обработчик выбора игры.
            onGameClick = { game ->
                openGameDetails(game)
            },

            // Обработчик перехода к уведомлениям.
            onNotificationsClick = {
                openPage(R.layout.screen_notifications)
            }
        )


        // ========================================================
        // ОБРАБОТКА СИСТЕМНЫХ ОТСТУПОВ
        // ========================================================

        // Устанавливаем обработчик системных отступов
        // для корневого элемента с ID main.
        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
        ) { view, insets ->

            // Получаем размеры системных панелей:
            // статус-бара и нижней панели навигации Android.
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            // Получаем размеры экранной клавиатуры.
            val ime = insets.getInsets(
                WindowInsetsCompat.Type.ime()
            )

            // Выбираем наибольший нижний отступ.
            //
            // Это помогает избежать наложения интерфейса
            // на клавиатуру или системную панель.
            val bottomInset = maxOf(
                systemBars.bottom,
                ime.bottom
            )

            // Устанавливаем отступы для корневого View.
            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                bottomInset
            )

            // Возвращаем insets, чтобы Android мог продолжить их обработку.
            insets
        }


        // Настраиваем нижнюю панель навигации.
        setupBottomNavigation()


        // Автоматически добавляем недостающие игры в Firestore.
        //
        // Загрузка выполняется в фоновом потоке,
        // чтобы не блокировать интерфейс.
        lifecycleScope.launch {

            try {

                // Выполняем заполнение каталога на IO-потоке.
                withContext(Dispatchers.IO) {
                    repository.loadInitialGames()
                }

            } catch (e: Exception) {

                // Если заполнение каталога завершилось ошибкой,
                // записываем её в Logcat.
                android.util.Log.e(
                    "MainActivity",
                    "Ошибка заполнения каталога игр",
                    e
                )
            }
        }


        // Открываем главную страницу приложения.
        openHomePage()

        // Устанавливаем активное состояние кнопки «Главная».
        setActiveNav(findViewById(R.id.navHome))
    }


    // ============================================================
    // BOTTOM NAVIGATION — НИЖНЯЯ ПАНЕЛЬ НАВИГАЦИИ
    // ============================================================

    // Настраивает обработчики нажатий на кнопки нижней панели.
    private fun setupBottomNavigation() {

        // Находим кнопку главной страницы.
        val navHome = findViewById<LinearLayout>(R.id.navHome)

        // Находим кнопку поиска.
        val navSearch = findViewById<LinearLayout>(R.id.navSearch)

        // Находим кнопку создания публикации.
        val navCreate = findViewById<LinearLayout>(R.id.navCreate)

        // Находим кнопку сообщений.
        val navChats = findViewById<LinearLayout>(R.id.navChats)

        // Находим кнопку профиля.
        val navProfile = findViewById<LinearLayout>(R.id.navProfile)


        // Обработчик перехода на главную страницу.
        navHome.setOnClickListener {

            // Открываем главную страницу.
            openHomePage()

            // Подсвечиваем соответствующую кнопку навигации.
            setActiveNav(navHome)
        }


        // Обработчик перехода к поиску.
        navSearch.setOnClickListener {

            // Открываем экран поиска.
            openSearchPage()

            // Подсвечиваем кнопку поиска.
            setActiveNav(navSearch)
        }


        // Обработчик перехода к созданию публикации.
        navCreate.setOnClickListener {

            // Открываем экран создания публикации.
            openPage(R.layout.screen_create)

            // Подсвечиваем кнопку создания.
            setActiveNav(navCreate)
        }


        // Обработчик перехода к чатам.
        navChats.setOnClickListener {

            // Открываем экран мессенджера.
            openPage(R.layout.screen_chats)

            // Подсвечиваем кнопку сообщений.
            setActiveNav(navChats)
        }


        // Обработчик перехода к профилю пользователя.
        navProfile.setOnClickListener {

            // Открываем XML-разметку профиля.
            openPage(R.layout.screen_profile)

            // Получаем View открытого экрана профиля.
            val profileView = pageContainer.getChildAt(0)

            // Получаем UID текущего пользователя Firebase.
            val userId = FirebaseAuth.getInstance().currentUser?.uid

            // Создаём и настраиваем менеджер профиля.
            ProfileManager(profileView, postsManager).setup()

            // Если пользователь авторизован,
            // загружаем его публикации.
            if (userId != null) {
                postsManager.loadUserPosts(profileView, userId)
            }

            // Подсвечиваем кнопку профиля.
            setActiveNav(navProfile)
        }
    }


    // ============================================================
    // OPEN PAGE — УНИВЕРСАЛЬНОЕ ПЕРЕКЛЮЧЕНИЕ ЭКРАНОВ
    // ============================================================

    // Открывает экран по ID его XML-разметки.
    //
    // layoutId — ID XML-файла, который нужно отобразить.
    //
    // postToEdit — необязательная публикация,
    // которую нужно передать на экран редактирования.
    private fun openPage(
        layoutId: Int,
        postToEdit: Post? = null
    ) {

        // Удаляем предыдущий экран из контейнера.
        pageContainer.removeAllViews()

        // Создаём View на основе переданной XML-разметки.
        val view = LayoutInflater.from(this).inflate(
            layoutId,
            pageContainer,
            false
        )

        // Добавляем новый экран в контейнер.
        pageContainer.addView(view)


        // Передаём новый экран менеджеру чатов.
        // Менеджер самостоятельно проверяет layoutId
        // и определяет, требуется ли настройка мессенджера.
        chatsManager.setupPage(layoutId, view)


        // Если открыт экран создания публикации,
        // настраиваем CreatePostManager.
        if (layoutId == R.layout.screen_create) {

            // Передаём View и публикацию для редактирования.
            createPostManager.setup(view, postToEdit) { updatedPost ->

                // После создания или изменения публикации
                // открываем её подробную страницу.
                openPostDetails(updatedPost)
            }
        }


        // Если открыт экран уведомлений,
        // запускаем его настройку.
        if (layoutId == R.layout.screen_notifications) {
            notificationsManager.setup(view)
        }
    }


    // ============================================================
    // HOME — КАТЕГОРИИ ГЛАВНОЙ СТРАНИЦЫ
    // ============================================================

    // Настраивает категории главной страницы.
    private fun setupHomeCategories(view: View) {

        // Создаём соответствие между ID кнопок и названиями категорий.
        val categories = mapOf(
            R.id.category_all to "Все",
            R.id.category_games to "Игры",
            R.id.category_guides to "Гайды",
            R.id.category_news to "Новости",
            R.id.category_lore to "Лор"
        )


        // Внутренняя функция выбирает категорию
        // и обновляет внешний вид кнопок.
        fun selectCategory(selectedId: Int) {

            // Перебираем все ID категорий.
            categories.keys.forEach { categoryId ->

                // Находим кнопку текущей категории.
                // Если её нет в XML, пропускаем итерацию.
                val category = view.findViewById<TextView>(categoryId)
                    ?: return@forEach

                // Проверяем, выбрана ли эта категория.
                val isSelected = categoryId == selectedId

                // Устанавливаем фон кнопки.
                category.setBackgroundResource(
                    if (isSelected) {
                        R.drawable.bg_category_active
                    } else {
                        R.drawable.bg_category
                    }
                )

                // Устанавливаем цвет текста.
                category.setTextColor(
                    if (isSelected) {
                        Color.WHITE
                    } else {
                        Color.rgb(170, 170, 170)
                    }
                )
            }

            // Получаем название выбранной категории.
            // Если ID отсутствует в карте, завершаем функцию.
            val selectedCategory = categories[selectedId] ?: return


            // В зависимости от категории выполняем нужное действие.
            when (selectedCategory) {

                // Для категорий «Все» и «Игры» показываем каталог игр.
                "Все", "Игры" -> {

                    // Делаем RecyclerView видимым.
                    view.findViewById<RecyclerView>(
                        R.id.gamesRecyclerView
                    )?.visibility = View.VISIBLE

                    // Загружаем игры.
                    loadGames()
                }

                // Для остальных категорий пока показываем сообщение.
                "Гайды", "Новости", "Лор" -> {

                    Toast.makeText(
                        this,
                        "Категория «$selectedCategory». " +
                                "Подключим публикации следующим этапом.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }


        // Назначаем обработчик нажатия каждой кнопке категории.
        categories.keys.forEach { categoryId ->

            view.findViewById<TextView>(categoryId)
                ?.setOnClickListener {

                    // При нажатии выбираем соответствующую категорию.
                    selectCategory(categoryId)
                }
        }

        // При первом открытии главной страницы
        // автоматически выбираем категорию «Все».
        selectCategory(R.id.category_all)
    }


    // ============================================================
    // POST DETAILS — ПОДРОБНЫЙ ПРОСМОТР ПУБЛИКАЦИИ
    // ============================================================

    // Открывает страницу подробного просмотра публикации.
    private fun openPostDetails(post: Post) {

        // Открываем XML-разметку подробной страницы публикации.
        openPage(R.layout.screen_post_details)

        // Получаем View открытой страницы.
        val view = pageContainer.getChildAt(0)


        // Создаём менеджер подробного просмотра публикации.
        PostDetailsManager(

            // Передаём текущую Activity.
            activity = this,

            // Передаём публикацию, которую нужно отобразить.
            post = post,

            // Обработчик возврата назад.
            onBack = {

                // Возвращаемся на главную страницу.
                openHomePage()

                // Подсвечиваем кнопку главной страницы.
                setActiveNav(findViewById(R.id.navHome))
            },

            // Обработчик нажатия на автора публикации.
            onAuthorClick = { authorId ->

                // Проверяем, что ID автора существует.
                if (authorId.isNotBlank()) {

                    // Открываем публичный профиль автора.
                    //
                    // При возвращении снова открываем текущую публикацию.
                    openPublicProfile(authorId) {
                        openPostDetails(post)
                    }

                } else {

                    // Если ID автора отсутствует,
                    // показываем сообщение об ошибке.
                    Toast.makeText(
                        this,
                        "Профиль автора недоступен",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },

            // Обработчик редактирования публикации.
            onEditClick = { postToEdit ->

                // Открываем экран создания публикации,
                // передавая в него публикацию для редактирования.
                openPage(R.layout.screen_create, postToEdit)
            }

        ).setup(view)
    }


    // ============================================================
    // PUBLIC PROFILE — ПУБЛИЧНЫЙ ПРОФИЛЬ
    // ============================================================

    // Открывает публичный профиль другого пользователя.
    //
    // userId — UID пользователя в Firebase.
    //
    // onBack — функция, определяющая, куда вернуться
    // после просмотра профиля.
    //
    // По умолчанию пользователь возвращается на главную страницу.
    fun openPublicProfile(
        userId: String,
        onBack: () -> Unit = {

            // Открываем главную страницу.
            openHomePage()

            // Подсвечиваем кнопку главной страницы.
            setActiveNav(findViewById(R.id.navHome))
        }
    ) {

        // Открываем разметку публичного профиля.
        openPage(R.layout.screen_public_profile)

        // Получаем View открытого экрана.
        val view = pageContainer.getChildAt(0)

        // Создаём менеджер публичного профиля.
        PublicProfileManager(

            // Передаём текущую Activity.
            activity = this,

            // Передаём UID пользователя, чей профиль открываем.
            userId = userId,

            // Передаём менеджер публикаций.
            postsManager = postsManager,

            // Передаём функцию возврата назад.
            onBack = onBack

        ).setup(view)
    }


    // ============================================================
    // OPEN HOME PAGE — ГЛАВНАЯ СТРАНИЦА
    // ============================================================

    // Открывает главную страницу приложения.
    private fun openHomePage() {

        // Загружаем XML-разметку главной страницы.
        openPage(R.layout.screen_home)

        // Получаем View созданной страницы.
        val view = pageContainer.getChildAt(0)

        // Передаём экран менеджеру главной страницы.
        // Он настраивает логотип, поиск, категории и каталог игр.
        homeManager.setup(view)
    }


    // ============================================================
    // HOME LOGO — ОФОРМЛЕНИЕ ЛОГОТИПА
    // ============================================================

    // Настраивает цветовое оформление логотипа GamerHub.
    private fun setupHomeLogo(view: View) {

        // Находим TextView с логотипом.
        val title = view.findViewById<TextView>(R.id.logoText)

        // Создаём текст логотипа.
        val text = "GamerHub"

        // Создаём форматированную строку.
        val spannable = SpannableString(text)

        // Первые пять символов Gamer окрашиваем в белый цвет.
        spannable.setSpan(
            ForegroundColorSpan(Color.WHITE),
            0,
            5,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Последние три символа Hub окрашиваем в фиолетовый цвет.
        spannable.setSpan(
            ForegroundColorSpan(Color.rgb(170, 80, 255)),
            5,
            text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Устанавливаем форматированный логотип.
        title.text = spannable

        // Обработчик нажатия на логотип.
        title.setOnClickListener {

            // Открываем экран регистрации.
            startActivity(
                Intent(this, RegisterActivity::class.java)
            )
        }
    }


    // ============================================================
    // LOAD GAMES — ЗАГРУЗКА КАТАЛОГА
    // ============================================================

    // Загружает каталог игр из репозитория.
    private fun loadGames() {

        // Запускаем корутину в фоновом потоке IO.
        lifecycleScope.launch(Dispatchers.IO) {

            try {

                // Получаем список игр через GameRepository.
                val games = repository.getGames()

                // Переключаемся на главный поток,
                // поскольку обновлять интерфейс можно только из него.
                withContext(Dispatchers.Main) {

                    // Передаём игры для отображения на главной странице.
                    showHomeGames(games)
                }

            } catch (e: Exception) {

                // Записываем ошибку загрузки из Firebase в Logcat.
                android.util.Log.e(
                    "GamerHub",
                    "Ошибка загрузки каталога из Firebase",
                    e
                )

                // Если загрузка из Firebase не удалась,
                // пробуем получить игры из локальной базы Room.
                val localGames = try {

                    repository.getLocalGames()

                } catch (localError: Exception) {

                    // Записываем ошибку локальной базы.
                    android.util.Log.e(
                        "GamerHub",
                        "Ошибка загрузки каталога из Room",
                        localError
                    )

                    // Если и локальная загрузка завершилась ошибкой,
                    // возвращаем пустой список.
                    emptyList()
                }

                // Возвращаемся на главный поток для обновления интерфейса.
                withContext(Dispatchers.Main) {

                    // Отображаем игры, полученные из Room.
                    showHomeGames(localGames)

                    // Если локальный каталог тоже пуст,
                    // показываем сообщение пользователю.
                    if (localGames.isEmpty()) {
                        Toast.makeText(
                            this@MainActivity,
                            "Не удалось загрузить каталог игр",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }


    // ============================================================
    // HOME GAMES — ОТОБРАЖЕНИЕ ИГР НА ГЛАВНОЙ
    // ============================================================

    // Отображает список игр в RecyclerView.
    private fun showHomeGames(games: List<Game>) {

        // Находим RecyclerView на текущей странице.
        // Если его нет, завершаем выполнение метода.
        val recyclerView = pageContainer.findViewById<RecyclerView>(
            R.id.gamesRecyclerView
        ) ?: return

        // Устанавливаем сетку из двух колонок.
        recyclerView.layoutManager = GridLayoutManager(this, 2)

        // Создаём адаптер для отображения карточек игр.
        recyclerView.adapter = GameAdapter(
            games,       // Список игр.
            repository,  // Репозиторий для работы с лайками.
            this         // Activity.
        ) { game ->

            // При нажатии на карточку открываем страницу игры.
            openGameDetails(game)
        }
    }


    // ============================================================
    // SEARCH — ПОИСК
    // ============================================================

    // Открывает страницу поиска игр.
    private fun openSearchPage() {

        // Загружаем XML-разметку поиска.
        openPage(R.layout.screen_search)

        // Получаем View открытого экрана.
        val view = pageContainer.getChildAt(0)

        // Создаём менеджер поиска.
        SearchManager(

            // Передаём Activity.
            this,

            // Передаём репозиторий игр.
            repository,

            // Передаём контейнер страниц.
            pageContainer

        ) { game ->

            // При выборе игры открываем её подробную страницу.
            openGameDetails(game)

        }.setup(view)
    }


    // ============================================================
    // GAME DETAILS — ПОДРОБНАЯ СТРАНИЦА ИГРЫ
    // ============================================================

    // Открывает подробную страницу выбранной игры.
    private fun openGameDetails(game: Game) {

        // Загружаем XML-разметку страницы игры.
        openPage(R.layout.screen_game_details)

        // Получаем View открытой страницы.
        val view = pageContainer.getChildAt(0)

        // Находим кнопку возврата.
        val backButton = view.findViewById<View>(
            R.id.backButton
        )

        // Настраиваем возврат на главную страницу.
        backButton.setOnClickListener {

            // Открываем главную страницу.
            openHomePage()

            // Подсвечиваем кнопку главной страницы.
            setActiveNav(findViewById(R.id.navHome))
        }


        // Находим ImageView для изображения игры.
        val image = view.findViewById<ImageView>(
            R.id.gameDetailsImage
        )

        // Проверяем, является ли адрес изображения интернет-ссылкой.
        if (game.image.startsWith("http")) {

            // Если это URL, загружаем изображение через Glide.
            Glide.with(this)
                .load(game.image)
                .into(image)

        } else {

            // Если это не URL, пытаемся найти изображение
            // среди ресурсов drawable.
            val imageId = resources.getIdentifier(
                game.image,  // Название изображения.
                "drawable",   // Тип ресурса.
                packageName  // Пакет приложения.
            )

            // Если ресурс найден, устанавливаем его в ImageView.
            if (imageId != 0) {
                image.setImageResource(imageId)
            }
        }


        // Находим TextView названия игры и устанавливаем название.
        view.findViewById<TextView>(
            R.id.gameDetailsTitle
        ).text = game.title

        // Устанавливаем жанр игры.
        view.findViewById<TextView>(
            R.id.gameDetailsGenre
        ).text = game.genre

        // Устанавливаем описание игры.
        view.findViewById<TextView>(
            R.id.gameDetailsDescription
        ).text = game.description


        // Загружаем публикации, связанные с выбранной игрой.
        postsManager.loadPostsForGame(
            view = view,
            gameTitle = game.title
        )
    }


    // ============================================================
    // BOTTOM NAVIGATION ACTIVE STATE — АКТИВНАЯ КНОПКА
    // ============================================================

    // Меняет цвет выбранного элемента нижней навигации.
    //
    // selected — LinearLayout той кнопки,
    // которая должна отображаться активной.
    private fun setActiveNav(selected: LinearLayout) {

        // Находим общий контейнер нижней навигации.
        val navigation = findViewById<LinearLayout>(
            R.id.navigation
        )

        // Перебираем все дочерние элементы панели навигации.
        for (i in 0 until navigation.childCount) {

            // Получаем очередной элемент по его индексу.
            val item = navigation.getChildAt(i)

            // Проверяем, что элемент является LinearLayout.
            // Если это другой тип View, пропускаем его.
            if (item !is LinearLayout) {
                continue
            }

            // Первый дочерний элемент — иконка.
            val image = item.getChildAt(0)

            // Второй дочерний элемент — текстовая подпись.
            val text = item.getChildAt(1)

            // Выбираем цвет в зависимости от состояния кнопки.
            //
            // Активная кнопка — фиолетовая.
            // Неактивные кнопки — серые.
            val color = if (item == selected) {
                Color.rgb(180, 90, 255)
            } else {
                Color.rgb(150, 150, 150)
            }

            // Если первый элемент является ImageView,
            // меняем цвет его иконки.
            if (image is ImageView) {
                image.setColorFilter(color)
            }

            // Если второй элемент является TextView,
            // меняем цвет его текста.
            if (text is TextView) {
                text.setTextColor(color)
            }
        }
    }
}