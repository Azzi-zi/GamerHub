package com.example.gamerhub

// Импортируем AlertDialog для отображения списка подписчиков или подписок.
import android.app.AlertDialog

// Импортируем Color для настройки цветов элементов интерфейса.
import android.graphics.Color

// Импортируем Typeface для настройки начертания текста.
import android.graphics.Typeface

// Импортируем ColorDrawable для установки цвета фона диалогового окна.
import android.graphics.drawable.ColorDrawable

// Импортируем Gravity для выравнивания элементов.
import android.view.Gravity

// Импортируем ViewGroup для настройки размеров элементов.
import android.view.ViewGroup

// Импортируем LinearLayout для создания вертикальных списков.
import android.widget.LinearLayout

// Импортируем ProgressBar для индикатора загрузки.
import android.widget.ProgressBar

// Импортируем ScrollView для прокрутки длинного списка.
import android.widget.ScrollView

// Импортируем TextView для отображения текста.
import android.widget.TextView

// Импортируем Toast для показа коротких сообщений.
import android.widget.Toast

// Импортируем AppCompatActivity — базовый класс Activity приложения.
import androidx.appcompat.app.AppCompatActivity

// Импортируем lifecycleScope для запуска корутин с учётом жизненного цикла Activity.
import androidx.lifecycle.lifecycleScope

// Импортируем Firestore для получения данных о подписках.
import com.google.firebase.firestore.FirebaseFirestore

// Импортируем launch для запуска корутины.
import kotlinx.coroutines.launch

// Импортируем await для ожидания завершения Firebase-запросов внутри корутины.
import kotlinx.coroutines.tasks.await

// Менеджер отвечает за отображение списков подписчиков и подписок пользователя.
class FollowListManager(
    // Activity нужна для создания интерфейса и доступа к ресурсам приложения.
    private val activity: AppCompatActivity,

    // Функция, которая вызывается при нажатии на пользователя.
    // В неё передаётся UID выбранного пользователя.
    private val onUserClick: (String) -> Unit
) {

    // Получаем экземпляр Firestore для работы с базой данных.
    private val db = FirebaseFirestore.getInstance()

    // Цвет, используемый для выделения кнопок и элементов интерфейса.
    private val purple = Color.rgb(155, 122, 255)

    // Белый цвет для основного текста.
    private val white = Color.WHITE

    // Серый цвет для дополнительного текста и подсказок.
    private val gray = Color.rgb(170, 170, 180)

    // Тёмный цвет фона диалогового окна.
    private val dark = Color.rgb(26, 26, 32)

    // Открываем список подписчиков указанного пользователя.
    fun showFollowers(userId: String) {

        // Передаём UID пользователя, название коллекции followers
        // и заголовок диалогового окна.
        showUsers(userId, "followers", "Подписчики")
    }

    // Открываем список пользователей, на которых подписан указанный пользователь.
    fun showFollowing(userId: String) {

        // Передаём UID пользователя, название коллекции following
        // и заголовок диалогового окна.
        showUsers(userId, "following", "Подписки")
    }

    // Общая функция для отображения подписчиков или подписок.
    private fun showUsers(
        // UID пользователя, чей список мы открываем.
        userId: String,

        // Название подколлекции Firestore: followers или following.
        collectionName: String,

        // Заголовок диалогового окна.
        title: String
    ) {

        // Создаём вертикальный контейнер для элементов списка.
        val container = LinearLayout(activity).apply {

            // Располагаем элементы друг под другом.
            orientation = LinearLayout.VERTICAL

            // Устанавливаем внутренние отступы контейнера.
            setPadding(24, 12, 24, 12)
        }

        // Создаём текст, который будет отображаться во время загрузки.
        val loading = TextView(activity).apply {

            // Устанавливаем текст загрузки.
            text = "Загрузка списка..."

            // Устанавливаем размер текста.
            textSize = 14f

            // Выравниваем текст по центру.
            gravity = Gravity.CENTER

            // Устанавливаем серый цвет текста.
            setTextColor(gray)

            // Добавляем внутренние отступы.
            setPadding(12, 32, 12, 32)
        }

        // Добавляем сообщение о загрузке в контейнер.
        container.addView(loading)

        // Создаём ScrollView, чтобы длинный список можно было прокручивать.
        val scrollView = ScrollView(activity).apply {

            // Помещаем контейнер со списком внутрь области прокрутки.
            addView(container)
        }

        // Создаём диалоговое окно со списком пользователей.
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title) // Устанавливаем заголовок окна.
            .setView(scrollView) // Устанавливаем содержимое окна.
            .setNegativeButton("Закрыть", null) // Добавляем кнопку закрытия.
            .create() // Создаём диалог.

        // Настраиваем внешний вид диалогового окна после его показа.
        dialog.setOnShowListener {

            // Устанавливаем тёмный фон окна.
            dialog.window?.setBackgroundDrawable(
                ColorDrawable(dark)
            )

            // Находим системный элемент заголовка диалога.
            dialog.findViewById<TextView>(
                activity.resources.getIdentifier(
                    "alertTitle", // Имя системного элемента.
                    "id",         // Тип ресурса.
                    "android"     // Пространство имён Android.
                )
            )?.setTextColor(white) // Устанавливаем белый цвет заголовка.

            // Находим кнопку закрытия и окрашиваем её в фиолетовый цвет.
            dialog.getButton(
                AlertDialog.BUTTON_NEGATIVE
            )?.setTextColor(purple)
        }

        // Показываем диалоговое окно пользователю.
        dialog.show()

        // Запускаем корутину, чтобы загрузить данные из Firestore.
        activity.lifecycleScope.launch {

            try {

                // Получаем документы из нужной подколлекции пользователя.
                // Например: users/{userId}/followers или users/{userId}/following.
                val documents = db.collection("users")
                    .document(userId) // Выбираем документ пользователя.
                    .collection(collectionName) // Открываем нужную подколлекцию.
                    .get() // Выполняем запрос.
                    .await() // Ожидаем завершения запроса без блокировки главного потока.

                // Удаляем сообщение о загрузке перед отображением результата.
                container.removeAllViews()

                // Проверяем, есть ли пользователи в полученном списке.
                if (documents.isEmpty) {

                    // Если список пуст, отображаем соответствующее сообщение.
                    container.addView(
                        TextView(activity).apply {

                            // Для подписчиков и подписок показываем разные сообщения.
                            text = if (collectionName == "followers") {
                                "Пока нет подписчиков"
                            } else {
                                "Пока нет подписок"
                            }

                            // Настраиваем размер текста.
                            textSize = 14f

                            // Выравниваем сообщение по центру.
                            gravity = Gravity.CENTER

                            // Устанавливаем серый цвет текста.
                            setTextColor(gray)

                            // Добавляем внутренние отступы.
                            setPadding(12, 32, 12, 32)
                        }
                    )

                    // Завершаем корутину, так как список пуст.
                    return@launch
                }

                // Перебираем все документы из коллекции подписок или подписчиков.
                for (document in documents.documents) {

                    // Получаем UID пользователя из поля uid.
                    // Если поле отсутствует, используем ID документа.
                    val targetUid = document.getString("uid")
                        ?: document.id

                    // Если UID пустой, пропускаем текущий документ.
                    if (targetUid.isBlank()) continue

                    // Пытаемся получить публичный профиль пользователя.
                    val profile = try {

                        // Открываем документ публичного профиля по UID.
                        db.collection("publicProfiles")
                            .document(targetUid)
                            .get()
                            .await()

                    } catch (_: Exception) {

                        // Если профиль не удалось загрузить,
                        // сохраняем null и продолжим обработку списка.
                        null
                    }

                    // Получаем имя пользователя.
                    // Сначала используем username из публичного профиля.
                    // Если его нет, пробуем взять username из документа подписки.
                    // Если оба значения отсутствуют, используем запасное имя.
                    val username = profile
                        ?.getString("username")
                        ?.takeIf { it.isNotBlank() }
                        ?: document.getString("username")
                        ?: "Игрок GamerHub"

                    // Создаём вертикальный элемент списка для пользователя.
                    val row = LinearLayout(activity).apply {

                        // Располагаем имя и тег друг под другом.
                        orientation = LinearLayout.VERTICAL

                        // Выравниваем содержимое по вертикальному центру.
                        gravity = Gravity.CENTER_VERTICAL

                        // Устанавливаем внутренние отступы элемента.
                        setPadding(16, 18, 16, 18)

                        // Разрешаем нажимать на элемент.
                        isClickable = true

                        // Разрешаем элементу получать фокус.
                        isFocusable = true

                        // Устанавливаем стандартный фон нажатия Android.
                        setBackgroundResource(
                            android.R.drawable.list_selector_background
                        )
                    }

                    // Создаём TextView для отображения имени пользователя.
                    val name = TextView(activity).apply {

                        // Устанавливаем имя пользователя.
                        text = username

                        // Настраиваем размер текста.
                        textSize = 16f

                        // Устанавливаем белый цвет.
                        setTextColor(white)

                        // Делаем текст жирным.
                        setTypeface(null, Typeface.BOLD)
                    }

                    // Создаём TextView для отображения тега пользователя.
                    val tag = TextView(activity).apply {

                        // Сначала пытаемся получить готовый тег из профиля.
                        // Если его нет, формируем тег из имени пользователя.
                        // Пробелы заменяются символами подчёркивания.
                        text = profile?.getString("tag")
                            ?: "@${username.lowercase().replace(" ", "_")}"

                        // Устанавливаем небольшой размер текста.
                        textSize = 12f

                        // Устанавливаем серый цвет.
                        setTextColor(gray)
                    }

                    // Добавляем имя в строку пользователя.
                    row.addView(name)

                    // Добавляем тег под именем.
                    row.addView(tag)

                    // Назначаем обработчик нажатия на пользователя.
                    row.setOnClickListener {

                        // Закрываем диалоговое окно.
                        dialog.dismiss()

                        // Передаём UID выбранного пользователя
                        // в функцию перехода к его профилю.
                        onUserClick(targetUid)
                    }

                    // Добавляем строку пользователя в общий список.
                    container.addView(
                        row,
                        LinearLayout.LayoutParams(
                            // Элемент занимает всю доступную ширину.
                            ViewGroup.LayoutParams.MATCH_PARENT,

                            // Высота зависит от содержимого.
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )

                    // Создаём горизонтальный разделитель между пользователями.
                    val separator = android.view.View(activity).apply {

                        // Устанавливаем цвет разделительной линии.
                        setBackgroundColor(
                            Color.rgb(48, 48, 57)
                        )
                    }

                    // Добавляем разделитель под текущим пользователем.
                    container.addView(
                        separator,
                        LinearLayout.LayoutParams(
                            // Линия занимает всю ширину списка.
                            ViewGroup.LayoutParams.MATCH_PARENT,

                            // Высота линии — 1 пиксель.
                            1
                        )
                    )
                }

            } catch (e: Exception) {

                // Если загрузка завершилась ошибкой,
                // очищаем контейнер от предыдущего содержимого.
                container.removeAllViews()

                // Показываем сообщение о невозможности загрузить список.
                container.addView(
                    TextView(activity).apply {

                        // Объясняем пользователю, что список не загрузился.
                        text = "Не удалось загрузить список.\nПроверь правила Firestore."

                        // Настраиваем размер текста.
                        textSize = 14f

                        // Выравниваем сообщение по центру.
                        gravity = Gravity.CENTER

                        // Устанавливаем серый цвет.
                        setTextColor(gray)

                        // Добавляем внутренние отступы.
                        setPadding(12, 32, 12, 32)
                    }
                )

                // Дополнительно показываем Toast с описанием ошибки.
                Toast.makeText(
                    activity,
                    "Ошибка загрузки: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}