
package com.example.gamerhub

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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreatePostManager(
    private val activity: AppCompatActivity

) {

    private var currentView: View? = null
    private var selectedImageUri: Uri? = null

    private val db = FirebaseFirestore.getInstance()

    private val selectedGameTags = linkedSetOf<String>()
    private val selectedTopicTags = linkedSetOf<String>()

    private var availableGameTags = listOf<String>()
    private var availableTopicTags = listOf<String>()

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

    // Выбор изображения из галереи
    private val imagePicker =
        activity.registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                try {
                    activity.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // Разрешение могло быть предоставлено ранее.
                }

                selectedImageUri = uri
                showImagePreview(uri)
            }
        }

    // Настройка экрана создания публикации
    fun setup(view: View) {
        currentView = view

        setupGameSpinner(view)
        setupPostTypeSpinner(view)

        view.findViewById<FrameLayout>(
            R.id.imageUploadArea
        ).setOnClickListener {
            imagePicker.launch(arrayOf("image/*"))
        }

        view.findViewById<Button>(
            R.id.publishButton
        ).setOnClickListener {
            publishPost()
        }

        updateTagsHint(view)
    }

    // Загрузка списка игр
    // Загружаем список игр напрямую из Firestore
    private fun setupGameSpinner(view: View) {
        val spinner = view.findViewById<Spinner>(
            R.id.postGameSpinner
        )

        activity.lifecycleScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    Tasks.await(
                        db.collection("games").get()
                    )
                }

                val gameTitles = snapshot.documents
                    .mapNotNull { document ->
                        document.getString("title")
                    }
                    .distinct()
                    .sorted()

                if (gameTitles.isEmpty()) {
                    showMessage("Список игр пуст")
                    return@launch
                }

                val adapter = object : ArrayAdapter<String>(
                    activity,
                    android.R.layout.simple_spinner_item,
                    gameTitles
                ) {
                    private fun createGameTextView(
                        convertView: View?,
                        paddingStart: Int,
                        paddingTop: Int
                    ): TextView {
                        return (convertView as? TextView
                            ?: TextView(activity)).apply {
                            setPadding(
                                paddingStart,
                                paddingTop,
                                paddingStart,
                                paddingTop
                            )
                            textSize = 15f
                            setTextColor(Color.WHITE)
                            gravity = Gravity.CENTER_VERTICAL
                        }
                    }

                    override fun getView(
                        position: Int,
                        convertView: View?,
                        parent: ViewGroup
                    ): View {
                        return createGameTextView(
                            convertView,
                            12,
                            0
                        ).apply {
                            text = getItem(position)
                        }
                    }

                    override fun getDropDownView(
                        position: Int,
                        convertView: View?,
                        parent: ViewGroup
                    ): View {
                        return createGameTextView(
                            convertView,
                            20,
                            14
                        ).apply {
                            text = getItem(position)
                            setBackgroundColor(
                                Color.rgb(30, 30, 38)
                            )
                        }
                    }
                }

                spinner.adapter = adapter

                spinner.setPopupBackgroundDrawable(
                    ColorDrawable(Color.rgb(30, 30, 38))
                )

                spinner.onItemSelectedListener =
                    object : android.widget.AdapterView.OnItemSelectedListener {

                        override fun onItemSelected(
                            parent: android.widget.AdapterView<*>?,
                            itemView: View?,
                            position: Int,
                            id: Long
                        ) {
                            refreshTags()
                        }

                        override fun onNothingSelected(
                            parent: android.widget.AdapterView<*>?
                        ) {
                            refreshTags()
                        }
                    }

                refreshTags()

            } catch (e: Exception) {
                android.util.Log.e(
                    "CreatePostManager",
                    "Не удалось загрузить игры из Firestore",
                    e
                )

                showMessage(
                    "Не удалось загрузить игры из Firebase. Проверь подключение и правила Firestore."
                )
            }
        }
    }
    // Настройка списка типов публикаций
    private fun setupPostTypeSpinner(view: View) {
        val spinner = view.findViewById<Spinner>(
            R.id.postTypeSpinner
        )

        val postTypes = listOf(
            "Выбери тип",
            "Игры",
            "Гайды",
            "Новости",
            "Лор"
        )

        val adapter = object : ArrayAdapter<String>(
            activity,
            android.R.layout.simple_spinner_item,
            postTypes
        ) {
            override fun getView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val textView = super.getView(
                    position,
                    convertView,
                    parent
                ) as TextView

                textView.setTextColor(Color.WHITE)
                textView.textSize = 15f
                return textView
            }

            override fun getDropDownView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val textView = super.getDropDownView(
                    position,
                    convertView,
                    parent
                ) as TextView

                textView.setTextColor(Color.WHITE)
                textView.setBackgroundColor(
                    Color.parseColor("#201F2D")
                )
                textView.textSize = 15f
                textView.setPadding(24, 24, 24, 24)

                return textView
            }
        }

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        spinner.adapter = adapter
        spinner.setSelection(0)

        spinner.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    itemView: View?,
                    position: Int,
                    id: Long
                ) {
                    refreshTags()
                }

                override fun onNothingSelected(
                    parent: android.widget.AdapterView<*>?
                ) {
                    refreshTags()
                }
            }
    }

    // Обновляем доступные теги при выборе игры или типа публикации
    private fun refreshTags() {
        val view = currentView ?: return

        val selectedGame = view.findViewById<Spinner>(
            R.id.postGameSpinner
        ).selectedItem?.toString().orEmpty()

        val selectedType = view.findViewById<Spinner>(
            R.id.postTypeSpinner
        ).selectedItem?.toString().orEmpty()

        val normalizedGame = selectedGame.lowercase()

        availableGameTags = gameTagOptions.entries
            .firstOrNull { normalizedGame.contains(it.key) }
            ?.value
            ?: listOf(
                "Геймплей",
                "Советы",
                "Обсуждение",
                "Впечатления"
            )

        availableTopicTags = topicTagOptions[selectedType]
            ?: emptyList()

        // Убираем выбранные теги, которых больше нет в списке
        selectedGameTags.retainAll(availableGameTags.toSet())
        selectedTopicTags.retainAll(availableTopicTags.toSet())

        renderTags()
        updateTagsHint(view)
    }

    // Рисуем группы тегов
    private fun renderTags() {
        val view = currentView ?: return

        val container = view.findViewById<LinearLayout>(
            R.id.postTagsContainer
        )

        container.removeAllViews()

        addTagSection(
            container,
            "Теги игры",
            availableGameTags,
            selectedGameTags
        )

        addTagSection(
            container,
            "Теги публикации",
            availableTopicTags,
            selectedTopicTags
        )
    }


    private fun addTagSection(
        container: LinearLayout,
        sectionTitle: String,
        tags: List<String>,
        selectedTags: MutableSet<String>
    ) {
        if (tags.isEmpty()) return

        val title = TextView(activity).apply {
            text = sectionTitle
            textSize = 14f
            setTextColor(Color.parseColor("#B9B4C9"))
            setPadding(0, 12, 0, 8)
        }

        container.addView(title)

        // Контейнер для строк тегов.
        val tagsLayout = android.widget.LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        container.addView(tagsLayout)

        // Каждая строка будет содержать несколько тегов.
        var currentRow = createTagRow()
        tagsLayout.addView(currentRow)

        val availableWidth = activity.resources.displayMetrics.widthPixels -
                (40 * activity.resources.displayMetrics.density).toInt()

        var usedWidth = 0

        tags.forEach { tag ->
            val chip = createTagChip(
                tag,
                selectedTags.contains(tag)
            )

            chip.setOnClickListener {
                if (selectedTags.contains(tag)) {
                    selectedTags.remove(tag)
                } else {
                    selectedTags.add(tag)
                }

                renderTags()
                updateTagsHint(currentView)
            }

            // Измеряем реальную ширину тега.
            val density = activity.resources.displayMetrics.density
            val chipWidth = (
                    tag.length * 8.5f +
                            if (selectedTags.contains(tag)) 48f else 36f
                    ) * density

            val margin = (8 * density).toInt()
            val totalWidth = chipWidth.toInt() + margin

            // Если тег не помещается, создаём новую строку.
            if (
                usedWidth > 0 &&
                usedWidth + totalWidth > availableWidth
            ) {
                currentRow = createTagRow()
                tagsLayout.addView(currentRow)
                usedWidth = 0
            }

            currentRow.addView(chip)
            usedWidth += totalWidth
        }
    }

    private fun createTagRow(): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun currentRowTagWidth(row: LinearLayout): Int {
        var width = 0

        for (i in 0 until row.childCount) {
            width += row.getChildAt(i).measuredWidth
        }

        return width
    }

    // Создаём нажимаемый тег
    private fun createTagChip(
        tag: String,
        isSelected: Boolean
    ): TextView {
        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 40f
            setColor(
                if (isSelected) {
                    Color.parseColor("#8055E8")
                } else {
                    Color.parseColor("#252431")
                }
            )
            setStroke(
                1,
                if (isSelected) {
                    Color.parseColor("#B49AFF")
                } else {
                    Color.parseColor("#454052")
                }
            )
        }

        return TextView(activity).apply {
            text = if (isSelected) "✓ $tag" else tag
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(14, 10, 14, 10)
            this.background = background

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 8, 8)
            }
        }
    }

    private fun updateTagsHint(view: View?) {
        val hint = view?.findViewById<TextView>(
            R.id.postTagsHint
        ) ?: return

        val count = selectedGameTags.size + selectedTopicTags.size

        hint.text = when {
            availableGameTags.isEmpty() &&
                    availableTopicTags.isEmpty() ->
                "Сначала выбери игру и тип публикации"

            count == 0 ->
                "Нажимай на теги, чтобы выбрать подходящие"

            else ->
                "Выбрано тегов: $count"
        }
    }

    // Предварительный просмотр изображения
    private fun showImagePreview(uri: Uri) {
        val view = currentView ?: return

        val imageArea = view.findViewById<FrameLayout>(
            R.id.imageUploadArea
        )

        val placeholder = imageArea.findViewById<View>(
            R.id.imageUploadPlaceholder
        )
        placeholder?.visibility = View.GONE

        val preview = imageArea.findViewWithTag<ImageView>(
            "post_image_preview"
        ) ?: ImageView(activity).apply {
            tag = "post_image_preview"
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        if (preview.parent == null) {
            imageArea.addView(preview)
        }

        Glide.with(activity)
            .load(uri)
            .into(preview)

        preview.bringToFront()
    }

    // Проверяем данные и публикуем запись
    private fun publishPost() {
        val view = currentView ?: return

        val title = view.findViewById<EditText>(
            R.id.postTitle
        ).text.toString().trim()

        val content = view.findViewById<EditText>(
            R.id.postContent
        ).text.toString().trim()

        val selectedGame = view.findViewById<Spinner>(
            R.id.postGameSpinner
        ).selectedItem?.toString().orEmpty()

        val selectedType = view.findViewById<Spinner>(
            R.id.postTypeSpinner
        ).selectedItem?.toString().orEmpty()

        val imageUri = selectedImageUri
        val user = FirebaseAuth.getInstance().currentUser

        if (user == null) {
            showMessage("Сначала войди в аккаунт")
            return
        }

        if (title.isBlank()) {
            showMessage("Введи название публикации")
            return
        }

        if (content.isBlank()) {
            showMessage("Добавь текст публикации")
            return
        }

        if (selectedGame.isBlank()) {
            showMessage("Выбери игру")
            return
        }

        if (selectedType == "Выбери тип" || selectedType.isBlank()) {
            showMessage("Выбери тип публикации")
            return
        }

        if (selectedGameTags.isEmpty() && selectedTopicTags.isEmpty()) {
            showMessage("Выбери хотя бы один тег")
            return
        }

        if (imageUri == null) {
            showMessage("Сначала выбери изображение")
            return
        }

        val publishButton = view.findViewById<Button>(
            R.id.publishButton
        )

        publishButton.isEnabled = false
        publishButton.text = "Загрузка..."

        activity.lifecycleScope.launch {
            try {
                // Получаем username текущего пользователя из publicProfiles
                val profileSnapshot = withContext(Dispatchers.IO) {
                    Tasks.await(
                        db.collection("publicProfiles")
                            .document(user.uid)
                            .get()
                    )
                }

                val username = profileSnapshot.getString("username")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: user.displayName
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                    ?: "Игрок GamerHub"
                // Загружаем изображение в Supabase
                val imageUrl = withContext(Dispatchers.IO) {
                    SupabaseStorageManager.uploadImage(
                        activity,
                        imageUri
                    )
                }

                val allTags = (
                        selectedGameTags.toList() +
                                selectedTopicTags.toList()
                        ).distinct()

                // Сохраняем публикацию и теги в Firestore
                val post = hashMapOf(
                    "title" to title,
                    "content" to content,
                    "game" to selectedGame,
                    "postType" to selectedType,
                    "gameTags" to selectedGameTags.toList(),
                    "topicTags" to selectedTopicTags.toList(),
                    "tags" to allTags,
                    "imageUrl" to imageUrl,
                    "authorId" to user.uid,
                    "authorName" to username,
                    "createdAt" to FieldValue.serverTimestamp()
                )

                withContext(Dispatchers.IO) {
                    Tasks.await(
                        db.collection("posts").add(post)
                    )
                }

                showMessage("Публикация успешно создана")

                view.findViewById<EditText>(
                    R.id.postTitle
                ).text.clear()

                view.findViewById<EditText>(
                    R.id.postContent
                ).text.clear()

                selectedImageUri = null
                selectedGameTags.clear()
                selectedTopicTags.clear()

                val imageArea = view.findViewById<FrameLayout>(
                    R.id.imageUploadArea
                )

                imageArea.findViewWithTag<View>(
                    "post_image_preview"
                )?.let {
                    imageArea.removeView(it)
                }

                imageArea.findViewById<View>(
                    R.id.imageUploadPlaceholder
                )?.visibility = View.VISIBLE

                renderTags()
                updateTagsHint(view)

            } catch (e: Exception) {
                android.util.Log.e(
                    "CreatePostManager",
                    "Ошибка создания публикации",
                    e
                )

                showMessage(
                    "Не удалось опубликовать: ${
                        e.message ?: "неизвестная ошибка"
                    }"
                )
            } finally {
                publishButton.isEnabled = true
                publishButton.text = "Опубликовать"
            }
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(
            activity,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}
