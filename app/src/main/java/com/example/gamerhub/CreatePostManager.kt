package com.example.gamerhub

// Импортируем классы Android для работы с файлами, изображениями и интерфейсом.
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

// Импортируем инструменты для выбора изображения из галереи.
import androidx.activity.result.contract.ActivityResultContracts

// Импортируем базовую Activity, в которой работает менеджер.
import androidx.appcompat.app.AppCompatActivity

// Импортируем корутины для выполнения асинхронных операций.
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Импортируем Glide для загрузки изображений.
import com.bumptech.glide.Glide

// Импортируем Tasks для ожидания завершения Firebase-запросов.
import com.google.android.gms.tasks.Tasks

// Импортируем Firebase Authentication для получения текущего пользователя.
import com.google.firebase.auth.FirebaseAuth

// Импортируем FieldValue для установки серверной даты создания публикации.
import com.google.firebase.firestore.FieldValue

// Импортируем Firestore для работы с базой данных.
import com.google.firebase.firestore.FirebaseFirestore

// Менеджер отвечает за создание и редактирование публикаций.
class CreatePostManager(
    // Activity нужна для доступа к ресурсам, экрану и жизненному циклу.
    private val activity: AppCompatActivity
) {

    // Ссылка на текущий экран создания или редактирования публикации.
    private var currentView: View? = null

    // URI выбранного пользователем изображения.
    private var selectedImageUri: Uri? = null

    // Публикация, которую сейчас редактируем.
    // Если null — создаём новую.
    private var editingPost: Post? = null

    // Действие, которое выполняется после успешного сохранения изменений.
    private var onPostSaved: (Post) -> Unit = {}

    // Получаем экземпляр Firestore для обращения к базе данных.
    private val db = FirebaseFirestore.getInstance()

    // Множество выбранных тегов игры.
    // linkedSetOf сохраняет порядок добавления элементов.
    private val selectedGameTags = linkedSetOf<String>()

    // Множество выбранных тегов публикации.
    private val selectedTopicTags = linkedSetOf<String>()

    // Список доступных тегов игры.
    private var availableGameTags = listOf<String>()

    // Список доступных тегов публикации.
    private var availableTopicTags = listOf<String>()

    // Словарь тегов, которые предлагаются для определённых игр.
    private val gameTagOptions = mapOf(
        "minecraft" to listOf(
            "Песочница",
            "Выживание",
            "Крафт",
            "Строительство",
            "Открытый мир",
            "Моды"
        ),
        "world of tanks" to listOf(
            "Танки",
            "Тактика",
            "Командная игра",
            "Сражения",
            "Техника",
            "Стратегия"
        )
    )

    // Словарь тегов, которые предлагаются для разных типов публикаций.
    private val topicTagOptions = mapOf(
        "Игры" to listOf(
            "Геймплей",
            "Первый взгляд",
            "Впечатления"
        ),
        "Гайды" to listOf(
            "Советы",
            "Для новичков",
            "Продвинутый уровень",
            "Обучение"
        ),
        "Новости" to listOf(
            "Обновление",
            "Анонс",
            "Событие",
            "Разработка"
        ),
        "Лор" to listOf(
            "История мира",
            "Персонажи",
            "Теории",
            "Сюжет"
        )
    )

    // Регистрируем выбор изображения из галереи.
    private val imagePicker =
        activity.registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            // Проверяем, выбрал ли пользователь изображение.
            if (uri != null) {

                try {
                    // Сохраняем разрешение на чтение выбранного файла.
                    // Благодаря этому приложение сможет использовать изображение позже.
                    activity.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // Разрешение могло быть предоставлено ранее.
                }

                // Запоминаем URI выбранного изображения.
                selectedImageUri = uri

                // Показываем выбранное изображение на экране.
                showImagePreview(uri)
            }
        }

    // Настройка экрана создания или редактирования публикации.
    fun setup(
        // Экран, на котором располагаются поля публикации.
        view: View,

        // Публикация для редактирования.
        // Если параметр не передан, создаётся новая публикация.
        postToEdit: Post? = null,

        // Функция, которая вызывается после сохранения изменений.
        onSaved: (Post) -> Unit = {}
    ) {

        // Сохраняем ссылку на текущий экран.
        currentView = view

        // Запоминаем публикацию, если пользователь находится в режиме редактирования.
        editingPost = postToEdit

        // Сохраняем функцию обратного вызова.
        onPostSaved = onSaved

        // Сбрасываем выбранное изображение для нового запуска экрана.
        selectedImageUri = null

        // Очищаем ранее выбранные теги игры.
        selectedGameTags.clear()

        // Очищаем ранее выбранные теги публикации.
        selectedTopicTags.clear()

        // Находим заголовок экрана в XML.
        val screenTitle = view.findViewById<TextView>(
            R.id.postScreenTitle
        )

        // Меняем заголовок в зависимости от режима работы.
        screenTitle.text = if (postToEdit != null) {
            "Редактировать публикацию"
        } else {
            "Создать публикацию"
        }

        // Находим кнопку публикации.
        val publishButton = view.findViewById<Button>(
            R.id.publishButton
        )

        // Меняем текст кнопки в зависимости от режима.
        publishButton.text = if (postToEdit != null) {
            "Сохранить изменения"
        } else {
            "➤  Опубликовать"
        }

        // Если передана публикация, заполняем поля её данными.
        postToEdit?.let { post ->

            // Устанавливаем прежнее название публикации.
            view.findViewById<EditText>(
                R.id.postTitle
            ).setText(post.title)

            // Устанавливаем прежний текст публикации.
            view.findViewById<EditText>(
                R.id.postContent
            ).setText(post.content)

            // Восстанавливаем выбранные теги игры.
            selectedGameTags.addAll(post.gameTags)

            // Восстанавливаем выбранные теги публикации.
            selectedTopicTags.addAll(post.topicTags)

            // Устанавливаем текст кнопки редактирования.
            view.findViewById<Button>(
                R.id.publishButton
            ).text = "Сохранить изменения"

            // Если у публикации есть изображение, показываем его.
            if (post.imageUrl.isNotBlank()) {
                showExistingImagePreview(post.imageUrl)
            }
        }

        // Настраиваем список игр.
        setupGameSpinner(view)

        // Настраиваем список типов публикаций.
        setupPostTypeSpinner(view)

        // При нажатии на область изображения открываем галерею.
        view.findViewById<FrameLayout>(
            R.id.imageUploadArea
        ).setOnClickListener {

            // Запускаем системный выбор изображения.
            imagePicker.launch(arrayOf("image/*"))
        }

        // Настраиваем кнопку создания или сохранения публикации.
        view.findViewById<Button>(
            R.id.publishButton
        ).setOnClickListener {

            // Запускаем проверку данных и сохранение публикации.
            publishPost()
        }

        // Обновляем подсказку о выбранных тегах.
        updateTagsHint(view)
    }

    // Загружаем список игр из Firestore и устанавливаем его в Spinner.
    private fun setupGameSpinner(view: View) {

        // Находим выпадающий список игр.
        val spinner = view.findViewById<Spinner>(
            R.id.postGameSpinner
        )

        // Запускаем корутину, чтобы выполнить загрузку асинхронно.
        activity.lifecycleScope.launch {
            try {

                // Переключаемся на фоновый поток для обращения к Firestore.
                val snapshot = withContext(Dispatchers.IO) {

                    // Получаем документы из коллекции games.
                    Tasks.await(
                        db.collection("games").get()
                    )
                }

                // Извлекаем названия игр из документов.
                val gameTitles = snapshot.documents
                    .mapNotNull { document ->
                        document.getString("title")
                    }
                    .distinct() // Убираем повторяющиеся названия.
                    .sorted()   // Сортируем названия по алфавиту.

                // Проверяем, удалось ли получить хотя бы одну игру.
                if (gameTitles.isEmpty()) {
                    showMessage("Список игр пуст")
                    return@launch
                }

                // Создаём адаптер для отображения списка игр.
                val adapter = object : ArrayAdapter<String>(
                    activity,
                    android.R.layout.simple_spinner_item,
                    gameTitles
                ) {

                    // Создаём и настраиваем TextView для названия игры.
                    private fun createGameTextView(
                        convertView: View?,
                        paddingStart: Int,
                        paddingTop: Int
                    ): TextView {

                        // Используем готовый TextView, если он существует.
                        // Иначе создаём новый.
                        return (convertView as? TextView
                            ?: TextView(activity)).apply {

                            // Устанавливаем внутренние отступы текста.
                            setPadding(
                                paddingStart,
                                paddingTop,
                                paddingStart,
                                paddingTop
                            )

                            // Настраиваем размер и цвет текста.
                            textSize = 15f
                            setTextColor(Color.WHITE)

                            // Выравниваем текст по вертикальному центру.
                            gravity = Gravity.CENTER_VERTICAL
                        }
                    }

                    // Создаём отображение выбранного элемента Spinner.
                    override fun getView(
                        position: Int,
                        convertView: View?,
                        parent: ViewGroup
                    ): View {

                        // Возвращаем настроенный TextView с названием игры.
                        return createGameTextView(
                            convertView,
                            12,
                            0
                        ).apply {
                            text = getItem(position)
                        }
                    }

                    // Создаём отображение элементов раскрытого списка.
                    override fun getDropDownView(
                        position: Int,
                        convertView: View?,
                        parent: ViewGroup
                    ): View {

                        // Настраиваем элемент выпадающего списка.
                        return createGameTextView(
                            convertView,
                            20,
                            14
                        ).apply {

                            // Устанавливаем название игры.
                            text = getItem(position)

                            // Устанавливаем тёмный фон элемента.
                            setBackgroundColor(
                                Color.rgb(30, 30, 38)
                            )
                        }
                    }
                }

                // Подключаем адаптер к выпадающему списку.
                spinner.adapter = adapter

                // При редактировании выбираем прежнюю игру.
                editingPost?.let { post ->

                    // Находим позицию прежней игры в списке.
                    val position = adapter.getPosition(post.game)

                    // Если игра найдена, устанавливаем её.
                    if (position >= 0) {
                        spinner.setSelection(position)
                    }
                }

                // Устанавливаем цвет фона раскрывающегося списка.
                spinner.setPopupBackgroundDrawable(
                    ColorDrawable(Color.rgb(30, 30, 38))
                )

                // Назначаем обработчик изменения выбранной игры.
                spinner.onItemSelectedListener =
                    object : android.widget.AdapterView.OnItemSelectedListener {

                        // Вызывается, когда пользователь выбирает игру.
                        override fun onItemSelected(
                            parent: android.widget.AdapterView<*>?,
                            itemView: View?,
                            position: Int,
                            id: Long
                        ) {
                            // Обновляем доступные теги.
                            refreshTags()
                        }

                        // Вызывается, если ничего не выбрано.
                        override fun onNothingSelected(
                            parent: android.widget.AdapterView<*>?
                        ) {
                            // Также обновляем доступные теги.
                            refreshTags()
                        }
                    }

                // Обновляем теги после загрузки списка игр.
                refreshTags()

            } catch (e: Exception) {

                // Записываем подробную ошибку в Logcat.
                android.util.Log.e(
                    "CreatePostManager",
                    "Не удалось загрузить игры из Firestore",
                    e
                )

                // Показываем пользователю сообщение об ошибке.
                showMessage(
                    "Не удалось загрузить игры из Firebase. Проверь подключение и правила Firestore."
                )
            }
        }
    }

    // Настраиваем выпадающий список типов публикаций.
    private fun setupPostTypeSpinner(view: View) {

        // Находим Spinner в XML.
        val spinner = view.findViewById<Spinner>(
            R.id.postTypeSpinner
        )

        // Создаём список доступных типов публикаций.
        val postTypes = listOf(
            "Выбери тип",
            "Игры",
            "Гайды",
            "Новости",
            "Лор"
        )

        // Создаём адаптер для списка типов.
        val adapter = object : ArrayAdapter<String>(
            activity,
            android.R.layout.simple_spinner_item,
            postTypes
        ) {

            // Настраиваем выбранный элемент списка.
            override fun getView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {

                // Получаем стандартный TextView адаптера.
                val textView = super.getView(
                    position,
                    convertView,
                    parent
                ) as TextView

                // Устанавливаем белый цвет и размер текста.
                textView.setTextColor(Color.WHITE)
                textView.textSize = 15f

                return textView
            }

            // Настраиваем элементы раскрытого списка.
            override fun getDropDownView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {

                // Получаем TextView для элемента списка.
                val textView = super.getDropDownView(
                    position,
                    convertView,
                    parent
                ) as TextView

                // Устанавливаем белый цвет текста.
                textView.setTextColor(Color.WHITE)

                // Устанавливаем тёмный фон.
                textView.setBackgroundColor(
                    Color.parseColor("#201F2D")
                )

                // Настраиваем размер текста и отступы.
                textView.textSize = 15f
                textView.setPadding(24, 24, 24, 24)

                return textView
            }
        }

        // Устанавливаем стандартный макет раскрывающегося списка.
        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        // Подключаем адаптер к Spinner.
        spinner.adapter = adapter

        // Восстанавливаем прежний тип публикации при редактировании.
        val savedType = editingPost?.postType

        // Ищем позицию сохранённого типа в списке.
        val savedPosition = postTypes.indexOf(savedType)

        // Если тип найден, выбираем его.
        // В противном случае оставляем первый пункт.
        spinner.setSelection(
            if (savedPosition >= 0) savedPosition else 0
        )

        // Назначаем обработчик изменения типа публикации.
        spinner.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {

                // Вызывается при выборе типа публикации.
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    itemView: View?,
                    position: Int,
                    id: Long
                ) {
                    // Обновляем доступные теги.
                    refreshTags()
                }

                // Вызывается, если ничего не выбрано.
                override fun onNothingSelected(
                    parent: android.widget.AdapterView<*>?
                ) {
                    // Обновляем доступные теги.
                    refreshTags()
                }
            }
    }

    // Обновляем доступные теги при выборе игры или типа публикации.
    private fun refreshTags() {

        // Получаем текущий экран.
        // Если экран ещё не установлен, прекращаем выполнение.
        val view = currentView ?: return

        // Получаем выбранную игру из Spinner.
        val selectedGame = view.findViewById<Spinner>(
            R.id.postGameSpinner
        ).selectedItem?.toString().orEmpty()

        // Получаем выбранный тип публикации.
        val selectedType = view.findViewById<Spinner>(
            R.id.postTypeSpinner
        ).selectedItem?.toString().orEmpty()

        // Приводим название игры к нижнему регистру.
        val normalizedGame = selectedGame.lowercase()

        // Ищем подходящий набор тегов для выбранной игры.
        availableGameTags = gameTagOptions.entries
            .firstOrNull { normalizedGame.contains(it.key) }
            ?.value

                // Если игра не найдена в словаре,
                // предлагаем универсальные теги.
            ?: listOf(
                "Геймплей",
                "Советы",
                "Обсуждение",
                "Впечатления"
            )

        // Получаем теги для выбранного типа публикации.
        // Если тип отсутствует в словаре, возвращаем пустой список.
        availableTopicTags = topicTagOptions[selectedType]
            ?: emptyList()

        // Удаляем выбранные теги, которых больше нет среди доступных.
        selectedGameTags.retainAll(availableGameTags.toSet())
        selectedTopicTags.retainAll(availableTopicTags.toSet())

        // Перерисовываем список тегов.
        renderTags()

        // Обновляем подсказку о количестве выбранных тегов.
        updateTagsHint(view)
    }

    // Отображаем группы тегов на экране.
    private fun renderTags() {

        // Получаем текущий экран.
        val view = currentView ?: return

        // Находим контейнер, в котором будут располагаться теги.
        val container = view.findViewById<LinearLayout>(
            R.id.postTagsContainer
        )

        // Удаляем старые элементы, чтобы нарисовать обновлённый список.
        container.removeAllViews()

        // Добавляем секцию тегов игры.
        addTagSection(
            container,
            "Теги игры",
            availableGameTags,
            selectedGameTags
        )

        // Добавляем секцию тегов публикации.
        addTagSection(
            container,
            "Теги публикации",
            availableTopicTags,
            selectedTopicTags
        )
    }

    // Создаём отдельную секцию тегов.
    private fun addTagSection(
        // Контейнер, куда добавляется секция.
        container: LinearLayout,

        // Заголовок секции.
        sectionTitle: String,

        // Все доступные теги этой секции.
        tags: List<String>,

        // Множество уже выбранных тегов.
        selectedTags: MutableSet<String>
    ) {

        // Если тегов нет, секцию создавать не нужно.
        if (tags.isEmpty()) return

        // Создаём заголовок секции.
        val title = TextView(activity).apply {
            text = sectionTitle
            textSize = 14f
            setTextColor(Color.parseColor("#B9B4C9"))
            setPadding(0, 12, 0, 8)
        }

        // Добавляем заголовок в основной контейнер.
        container.addView(title)

        // Создаём вертикальный контейнер для строк тегов.
        val tagsLayout = android.widget.LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL

            // Контейнер занимает всю доступную ширину.
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Добавляем контейнер тегов на экран.
        container.addView(tagsLayout)

        // Создаём первую строку тегов.
        var currentRow = createTagRow()
        tagsLayout.addView(currentRow)

        // Вычисляем доступную ширину экрана в пикселях.
        val availableWidth =
            activity.resources.displayMetrics.widthPixels -
                    (40 * activity.resources.displayMetrics.density).toInt()

        // Счётчик уже занятой ширины текущей строки.
        var usedWidth = 0

        // Перебираем все доступные теги.
        tags.forEach { tag ->

            // Создаём визуальный элемент тега.
            // Проверяем, выбран ли он пользователем.
            val chip = createTagChip(
                tag,
                selectedTags.contains(tag)
            )

            // Назначаем обработчик нажатия на тег.
            chip.setOnClickListener {

                // Если тег уже выбран, удаляем его из множества.
                if (selectedTags.contains(tag)) {
                    selectedTags.remove(tag)
                } else {
                    // Иначе добавляем тег в выбранные.
                    selectedTags.add(tag)
                }

                // Перерисовываем теги с учётом нового состояния.
                renderTags()

                // Обновляем подсказку.
                updateTagsHint(currentView)
            }

            // Получаем плотность экрана для перевода размеров.
            val density = activity.resources.displayMetrics.density

            // Примерно рассчитываем ширину элемента тега.
            val chipWidth = (
                    tag.length * 8.5f +
                            if (selectedTags.contains(tag)) 48f else 36f
                    ) * density

            // Учитываем правый отступ между тегами.
            val margin = (8 * density).toInt()

            // Получаем общую ширину тега вместе с отступом.
            val totalWidth = chipWidth.toInt() + margin

            // Если тег не помещается, создаём новую строку.
            if (
                usedWidth > 0 &&
                usedWidth + totalWidth > availableWidth
            ) {
                // Создаём новую строку.
                currentRow = createTagRow()

                // Добавляем её в вертикальный контейнер.
                tagsLayout.addView(currentRow)

                // Сбрасываем счётчик занятой ширины.
                usedWidth = 0
            }

            // Добавляем тег в текущую строку.
            currentRow.addView(chip)

            // Увеличиваем счётчик занятой ширины.
            usedWidth += totalWidth
        }
    }

    // Создаём горизонтальную строку, в которой будут располагаться теги.
    private fun createTagRow(): LinearLayout {

        return LinearLayout(activity).apply {

            // Элементы внутри строки располагаются горизонтально.
            orientation = LinearLayout.HORIZONTAL

            // Выравниваем элементы по левому краю.
            gravity = Gravity.START

            // Строка занимает всю ширину контейнера.
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    // Вычисляем суммарную измеренную ширину элементов строки.
    private fun currentRowTagWidth(row: LinearLayout): Int {

        // Начальное значение ширины.
        var width = 0

        // Перебираем все дочерние элементы строки.
        for (i in 0 until row.childCount) {

            // Добавляем ширину очередного элемента.
            width += row.getChildAt(i).measuredWidth
        }

        // Возвращаем итоговую ширину.
        return width
    }

    // Создаём нажимаемый элемент-тег.
    private fun createTagChip(
        // Название тега.
        tag: String,

        // Выбран ли этот тег.
        isSelected: Boolean
    ): TextView {

        // Создаём фон с закруглёнными углами.
        val background = GradientDrawable().apply {

            // Указываем прямоугольную форму.
            shape = GradientDrawable.RECTANGLE

            // Настраиваем радиус закругления углов.
            cornerRadius = 40f

            // Устанавливаем цвет фона в зависимости от состояния.
            setColor(
                if (isSelected) {
                    Color.parseColor("#8055E8")
                } else {
                    Color.parseColor("#252431")
                }
            )

            // Добавляем рамку.
            // Для выбранного тега используется другой цвет.
            setStroke(
                1,
                if (isSelected) {
                    Color.parseColor("#B49AFF")
                } else {
                    Color.parseColor("#454052")
                }
            )
        }

        // Создаём TextView и настраиваем его внешний вид.
        return TextView(activity).apply {

            // Для выбранного тега добавляем галочку.
            text = if (isSelected) "✓ $tag" else tag

            // Устанавливаем размер и цвет текста.
            textSize = 13f
            setTextColor(Color.WHITE)

            // Добавляем внутренние отступы.
            setPadding(14, 10, 14, 10)

            // Устанавливаем созданный фон.
            this.background = background

            // Настраиваем размеры элемента и его отступы.
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 8, 8)
            }
        }
    }

    // Обновляем текстовую подсказку под списком тегов.
    private fun updateTagsHint(view: View?) {

        // Находим элемент подсказки.
        // Если его нет, прекращаем выполнение.
        val hint = view?.findViewById<TextView>(
            R.id.postTagsHint
        ) ?: return

        // Считаем общее количество выбранных тегов.
        val count = selectedGameTags.size + selectedTopicTags.size

        // Выбираем текст подсказки в зависимости от состояния.
        hint.text = when {

            // Если оба списка тегов пусты, просим сначала выбрать параметры.
            availableGameTags.isEmpty() &&
                    availableTopicTags.isEmpty() ->
                "Сначала выбери игру и тип публикации"

            // Если теги доступны, но пользователь ничего не выбрал.
            count == 0 ->
                "Нажимай на теги, чтобы выбрать подходящие"

            // Если есть выбранные теги, показываем их количество.
            else ->
                "Выбрано тегов: $count"
        }
    }

    // Показываем предварительный просмотр нового изображения.
    private fun showImagePreview(uri: Uri) {

        // Получаем текущий экран.
        val view = currentView ?: return

        // Находим область загрузки изображения.
        val imageArea = view.findViewById<FrameLayout>(
            R.id.imageUploadArea
        )

        // Находим заглушку, которая отображается до выбора изображения.
        val placeholder = imageArea.findViewById<View>(
            R.id.imageUploadPlaceholder
        )

        // Скрываем заглушку.
        placeholder?.visibility = View.GONE

        // Ищем уже созданный ImageView для предварительного просмотра.
        // Если его нет, создаём новый.
        val preview = imageArea.findViewWithTag<ImageView>(
            "post_image_preview"
        ) ?: ImageView(activity).apply {

            // Устанавливаем специальный тег для поиска этого ImageView.
            tag = "post_image_preview"

            // Растягиваем изображение на всю область загрузки.
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            // Обрезаем изображение так, чтобы оно заполнило область.
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        // Добавляем ImageView, только если он ещё не находится в контейнере.
        if (preview.parent == null) {
            imageArea.addView(preview)
        }

        // Загружаем изображение по URI с помощью Glide.
        Glide.with(activity)
            .load(uri)
            .into(preview)

        // Перемещаем изображение на передний план.
        preview.bringToFront()
    }

    // Показываем старую обложку публикации при редактировании.
    private fun showExistingImagePreview(imageUrl: String) {

        // Получаем текущий экран.
        val view = currentView ?: return

        // Находим область загрузки изображения.
        val imageArea = view.findViewById<FrameLayout>(
            R.id.imageUploadArea
        )

        // Скрываем заглушку загрузки.
        imageArea.findViewById<View>(
            R.id.imageUploadPlaceholder
        )?.visibility = View.GONE

        // Ищем существующий ImageView или создаём новый.
        val preview = imageArea.findViewWithTag<ImageView>(
            "post_image_preview"
        ) ?: ImageView(activity).apply {

            // Устанавливаем тег для последующего поиска.
            tag = "post_image_preview"

            // Настраиваем размеры изображения.
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            // Изображение будет заполнять область с обрезкой краёв.
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        // Добавляем ImageView в контейнер, если он ещё не добавлен.
        if (preview.parent == null) {
            imageArea.addView(preview)
        }

        // Загружаем старую обложку по URL.
        Glide.with(activity)
            .load(imageUrl)
            .centerCrop()
            .into(preview)

        // Перемещаем изображение на передний план.
        preview.bringToFront()
    }

    // Проверяем введённые данные и создаём или обновляем публикацию.
    private fun publishPost() {

        // Получаем текущий экран.
        val view = currentView ?: return

        // Получаем название публикации и удаляем лишние пробелы по краям.
        val title = view.findViewById<EditText>(
            R.id.postTitle
        ).text.toString().trim()

        // Получаем основной текст публикации.
        val content = view.findViewById<EditText>(
            R.id.postContent
        ).text.toString().trim()

        // Получаем выбранную игру.
        val selectedGame = view.findViewById<Spinner>(
            R.id.postGameSpinner
        ).selectedItem?.toString().orEmpty()

        // Получаем выбранный тип публикации.
        val selectedType = view.findViewById<Spinner>(
            R.id.postTypeSpinner
        ).selectedItem?.toString().orEmpty()

        // Сохраняем ссылку на выбранное изображение.
        val imageUri = selectedImageUri

        // Сохраняем публикацию, которую редактируем, если она есть.
        val postBeingEdited = editingPost

        // Получаем текущего авторизованного пользователя Firebase.
        val user = FirebaseAuth.getInstance().currentUser

        // Проверяем, вошёл ли пользователь в аккаунт.
        if (user == null) {
            showMessage("Сначала войди в аккаунт")
            return
        }

        // Редактировать можно только собственную публикацию.
        if (
            postBeingEdited != null &&
            postBeingEdited.authorId != user.uid
        ) {
            showMessage("Можно редактировать только свои публикации")
            return
        }

        // Проверяем, введено ли название публикации.
        if (title.isBlank()) {
            showMessage("Введи название публикации")
            return
        }

        // Проверяем, введён ли основной текст.
        if (content.isBlank()) {
            showMessage("Добавь текст публикации")
            return
        }

        // Проверяем, выбрана ли игра.
        if (selectedGame.isBlank()) {
            showMessage("Выбери игру")
            return
        }

        // Проверяем, выбран ли тип публикации.
        if (selectedType == "Выбери тип" || selectedType.isBlank()) {
            showMessage("Выбери тип публикации")
            return
        }

        // Пользователь должен выбрать хотя бы один тег.
        if (selectedGameTags.isEmpty() && selectedTopicTags.isEmpty()) {
            showMessage("Выбери хотя бы один тег")
            return
        }

        // Для новой публикации изображение обязательно.
        // При редактировании можно оставить старую обложку.
        if (imageUri == null && postBeingEdited == null) {
            showMessage("Сначала выбери изображение")
            return
        }

        // Находим кнопку публикации.
        val publishButton = view.findViewById<Button>(
            R.id.publishButton
        )

        // Блокируем кнопку, чтобы пользователь не отправил форму повторно.
        publishButton.isEnabled = false

        // Показываем, что публикация загружается.
        publishButton.text = "Загрузка..."

        // Запускаем асинхронную операцию сохранения.
        activity.lifecycleScope.launch {

            try {

                // Получаем username текущего пользователя.
                val profileSnapshot = withContext(Dispatchers.IO) {

                    // Загружаем публичный профиль пользователя из Firestore.
                    Tasks.await(
                        db.collection("publicProfiles")
                            .document(user.uid)
                            .get()
                    )
                }

                // Получаем имя пользователя из публичного профиля.
                // Если оно отсутствует, используем displayName из Firebase Auth.
                // Если и его нет, устанавливаем запасное имя.
                val username = profileSnapshot.getString("username")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: user.displayName
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                    ?: "Игрок GamerHub"

                // Получаем URL аватара пользователя.
                // Сначала проверяем avatarUrl, затем avatarUri.
                val avatarUrl = profileSnapshot.getString("avatarUrl")
                    ?: profileSnapshot.getString("avatarUri")
                    ?: ""

                // Загружаем новую обложку или оставляем старую.
                val imageUrl = if (imageUri != null) {

                    // Загружаем выбранное изображение в Supabase Storage.
                    withContext(Dispatchers.IO) {
                        SupabaseStorageManager.uploadImage(
                            activity,
                            imageUri
                        )
                    }

                } else {

                    // Если новое изображение не выбрано,
                    // сохраняем URL старой обложки.
                    postBeingEdited?.imageUrl.orEmpty()
                }

                // Преобразуем выбранные теги игры в обычный список.
                val gameTags = selectedGameTags.toList()

                // Преобразуем выбранные теги публикации в обычный список.
                val topicTags = selectedTopicTags.toList()

                // Объединяем обе группы тегов и удаляем возможные повторы.
                val allTags = (
                        gameTags + topicTags
                        ).distinct()

                // Формируем общие данные публикации.
                val postData = hashMapOf<String, Any>(
                    "title" to title,
                    "content" to content,
                    "game" to selectedGame,
                    "postType" to selectedType,
                    "gameTags" to gameTags,
                    "topicTags" to topicTags,
                    "tags" to allTags,
                    "imageUrl" to imageUrl
                )

                // Проверяем, редактируем ли мы существующую публикацию.
                if (postBeingEdited != null) {

                    // Обновляем существующий документ.
                    // Автора и дату создания не меняем.
                    withContext(Dispatchers.IO) {

                        // Обновляем поля документа в коллекции posts.
                        Tasks.await(
                            db.collection("posts")
                                .document(postBeingEdited.id)
                                .update(postData)
                        )
                    }

                    // Создаём обновлённый объект публикации.
                    val updatedPost = postBeingEdited.copy(
                        title = title,
                        content = content,
                        game = selectedGame,
                        postType = selectedType,
                        gameTags = gameTags,
                        topicTags = topicTags,
                        imageUrl = imageUrl
                    )

                    // Сообщаем пользователю об успешном сохранении.
                    showMessage("Изменения сохранены")

                    // Передаём обновлённую публикацию в функцию обратного вызова.
                    onPostSaved(updatedPost)

                } else {

                    // Создаём новую публикацию.

                    // Сохраняем UID автора.
                    postData["authorId"] = user.uid

                    // Сохраняем имя автора.
                    postData["authorName"] = username

                    // Сохраняем URL аватара автора.
                    postData["authorAvatarUrl"] = avatarUrl

                    // Устанавливаем дату создания по времени сервера Firebase.
                    postData["createdAt"] = FieldValue.serverTimestamp()

                    // Отправляем новую публикацию в Firestore.
                    withContext(Dispatchers.IO) {

                        Tasks.await(
                            db.collection("posts").add(postData)
                        )
                    }

                    // Показываем сообщение об успешной публикации.
                    showMessage("Публикация успешно создана")

                    // Очищаем форму только после создания новой публикации.

                    // Очищаем поле названия.
                    view.findViewById<EditText>(
                        R.id.postTitle
                    ).text.clear()

                    // Очищаем поле основного текста.
                    view.findViewById<EditText>(
                        R.id.postContent
                    ).text.clear()

                    // Сбрасываем выбранное изображение.
                    selectedImageUri = null

                    // Очищаем выбранные теги.
                    selectedGameTags.clear()
                    selectedTopicTags.clear()

                    // Получаем область загрузки изображения.
                    val imageArea = view.findViewById<FrameLayout>(
                        R.id.imageUploadArea
                    )

                    // Удаляем изображение предварительного просмотра,
                    // если оно присутствует.
                    imageArea.findViewWithTag<View>(
                        "post_image_preview"
                    )?.let {
                        imageArea.removeView(it)
                    }

                    // Возвращаем заглушку загрузки изображения.
                    imageArea.findViewById<View>(
                        R.id.imageUploadPlaceholder
                    )?.visibility = View.VISIBLE

                    // Обновляем отображение тегов после очистки.
                    renderTags()

                    // Обновляем подсказку о выбранных тегах.
                    updateTagsHint(view)
                }

            } catch (e: Exception) {

                // Записываем подробную информацию об ошибке в Logcat.
                android.util.Log.e(
                    "CreatePostManager",
                    "Ошибка сохранения публикации",
                    e
                )

                // Показываем пользователю сообщение об ошибке.
                showMessage(
                    "Не удалось сохранить публикацию: ${
                        e.message ?: "неизвестная ошибка"
                    }"
                )

            } finally {

                // В любом случае снова включаем кнопку.
                publishButton.isEnabled = true

                // Возвращаем текст кнопки в зависимости от режима работы.
                publishButton.text = if (editingPost != null) {
                    "Сохранить изменения"
                } else {
                    "Опубликовать"
                }
            }
        }
    }

    // Показываем короткое сообщение пользователю.
    private fun showMessage(message: String) {

        // Создаём Toast с переданным текстом.
        Toast.makeText(
            activity,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}