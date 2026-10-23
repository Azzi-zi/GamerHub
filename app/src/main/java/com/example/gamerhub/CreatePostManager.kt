
package com.example.gamerhub

import android.content.Intent
import android.net.Uri
import android.view.View
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
import kotlinx.coroutines.launch

class CreatePostManager(
    private val activity: AppCompatActivity
) {
    private var currentView: View? = null
    private var selectedImageUri: Uri? = null

    // Регистрируем системный выбор изображения
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

                    selectedImageUri = uri
                    showImagePreview(uri)
                } catch (e: Exception) {
                    // Иногда разрешение уже выдано или не требуется.
                    // Пробуем показать выбранное изображение в любом случае.
                    selectedImageUri = uri
                    showImagePreview(uri)
                }
            }
        }

    // Настраиваем экран создания публикации
    fun setup(view: View) {
        currentView = view

        val imageArea = view.findViewById<FrameLayout>(
            R.id.imageUploadArea
        )

        imageArea.setOnClickListener {
            imagePicker.launch(arrayOf("image/*"))
        }

        val publishButton = view.findViewById<Button>(
            R.id.publishButton
        )

        publishButton.setOnClickListener {
            uploadPostImage()
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

    // Проверяем поля и загружаем изображение в Supabase
    private fun uploadPostImage() {
        val view = currentView ?: return

        val title = view.findViewById<EditText>(
            R.id.postTitle
        ).text.toString().trim()

        val content = view.findViewById<EditText>(
            R.id.postContent
        ).text.toString().trim()

        val gameSpinner = view.findViewById<Spinner>(
            R.id.postGameSpinner
        )

        if (title.isBlank()) {
            Toast.makeText(
                activity,
                "Введи название публикации",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (content.isBlank()) {
            Toast.makeText(
                activity,
                "Добавь текст публикации",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val imageUri = selectedImageUri

        if (imageUri == null) {
            Toast.makeText(
                activity,
                "Сначала выбери изображение",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val selectedGame = gameSpinner.selectedItem?.toString().orEmpty()

        if (selectedGame.isBlank()) {
            Toast.makeText(
                activity,
                "Выбери игру",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val publishButton = view.findViewById<Button>(
            R.id.publishButton
        )

        publishButton.isEnabled = false
        publishButton.text = "Загрузка изображения..."

        activity.lifecycleScope.launch {
            try {
                val imageUrl = SupabaseStorageManager.uploadImage(
                    activity,
                    imageUri
                )

                // Ссылка готова для сохранения вместе с публикацией.
                // Сохранение в Firestore подключим отдельно,
                // когда определим структуру коллекции публикаций.
                Toast.makeText(
                    activity,
                    "Изображение загружено в Supabase",
                    Toast.LENGTH_LONG
                ).show()

                android.util.Log.d(
                    "CreatePostManager",
                    "Название: $title; игра: $selectedGame; " +
                            "ссылка: $imageUrl; текст: $content"
                )
            } catch (e: Exception) {
                Toast.makeText(
                    activity,
                    "Ошибка загрузки: ${e.message ?: "неизвестная ошибка"}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                publishButton.isEnabled = true
                publishButton.text = "Опубликовать"
            }
        }
    }
}
