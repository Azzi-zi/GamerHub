
package com.example.gamerhub

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
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
    private val activity: AppCompatActivity,
    private val repository: GameRepository
) {

    private var currentView: View? = null
    private var selectedImageUri: Uri? = null

    private val db = FirebaseFirestore.getInstance()

    // Выбор изображения
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
                    // Разрешение может быть уже доступно.
                }

                selectedImageUri = uri
                showImagePreview(uri)
            }
        }

    // Настройка экрана создания публикации
    fun setup(view: View) {
        currentView = view

        setupGameSpinner(view)

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
    }

    // Загружаем игры из того же каталога, что и на главной странице
    private fun setupGameSpinner(view: View) {
        val spinner = view.findViewById<Spinner>(
            R.id.postGameSpinner
        )

        activity.lifecycleScope.launch {
            try {
                val games = try {
                    repository.getGames()
                } catch (e: Exception) {
                    repository.getLocalGames()
                }

                val gameTitles = games
                    .map { it.title }
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

            } catch (e: Exception) {
                android.util.Log.e(
                    "CreatePostManager",
                    "Не удалось загрузить игры",
                    e
                )
                showMessage("Не удалось загрузить список игр")
            }
        }
    }

    // Показываем выбранное изображение
    private fun showImagePreview(uri: Uri) {
        val view = currentView ?: return

        val imageArea = view.findViewById<FrameLayout>(
            R.id.imageUploadArea
        )

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
                // Загружаем изображение в Supabase
                val imageUrl = SupabaseStorageManager.uploadImage(
                    activity,
                    imageUri
                )

                // Формируем публикацию
                val post = hashMapOf(
                    "title" to title,
                    "content" to content,
                    "game" to selectedGame,
                    "imageUrl" to imageUrl,
                    "authorId" to user.uid,
                    "authorName" to (
                            user.displayName
                                ?: user.email
                                ?: "Игрок"
                            ),
                    "createdAt" to FieldValue.serverTimestamp()
                )

                // Сохраняем публикацию в Firestore
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

                val imageArea = view.findViewById<FrameLayout>(
                    R.id.imageUploadArea
                )

                imageArea.findViewWithTag<View>(
                    "post_image_preview"
                )?.let {
                    imageArea.removeView(it)
                }

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
