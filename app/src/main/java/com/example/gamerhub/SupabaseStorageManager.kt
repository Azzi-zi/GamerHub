package com.example.gamerhub

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Объект отвечает за загрузку изображений в Supabase Storage.
//
// Используется для загрузки аватаров, обложек профилей
// и других изображений приложения GamerHub.
object SupabaseStorageManager {

    // Адрес Supabase Edge Function.
    // Именно эта функция принимает изображение и загружает его в Storage.
    private const val FUNCTION_URL =
        "https://rewxoagdwwstvuuiqtqc.supabase.co/functions/v1/funk"

    // Публичный ключ Supabase.
    // Он используется для идентификации проекта при обращении к API.
    private const val SUPABASE_PUBLISHABLE_KEY =
        "sb_publishable_gjoGJw1PsN3_hXth7DuIsg_ruUUnIZ5"

    // Максимальный допустимый размер изображения — 5 МБ.
    private const val MAX_FILE_SIZE = 5L * 1024 * 1024

    /**
     * Загружает изображение в Supabase Storage.
     *
     * context — контекст Android, необходимый для доступа к файлу.
     * imageUri — адрес выбранного пользователем изображения.
     *
     * Возвращает публичную ссылку на загруженное изображение.
     */
    suspend fun uploadImage(
        context: Context,
        imageUri: Uri
    ): String = withContext(Dispatchers.IO) {

        // Получаем текущего пользователя Firebase.
        // Если пользователь не авторизован, загрузка невозможна.
        val user = com.google.firebase.auth.FirebaseAuth
            .getInstance()
            .currentUser
            ?: throw Exception("Сначала войди в аккаунт")

        // Получаем действующий Firebase ID Token.
        // Он подтверждает личность пользователя перед Edge Function.
        val token = user.getIdToken(false)
            .awaitToken()

        // ContentResolver позволяет Android работать
        // с файлами и изображениями по Uri.
        val resolver = context.contentResolver

        // Определяем MIME-тип выбранного изображения.
        // Например: image/jpeg, image/png или image/webp.
        val mimeType = resolver.getType(imageUri)
            ?: throw Exception("Не удалось определить формат изображения")

        // Разрешаем только три формата изображений.
        if (mimeType !in listOf(
                "image/jpeg",
                "image/png",
                "image/webp"
            )
        ) {
            throw Exception("Разрешены только JPEG, PNG и WebP")
        }

        // Открываем поток для чтения изображения.
        val inputStream = resolver.openInputStream(imageUri)
            ?: throw Exception("Не удалось открыть изображение")

        // Считываем содержимое изображения в массив байтов.
        // use автоматически закрывает поток после чтения.
        val imageBytes = inputStream.use {
            it.readBytes()
        }

        // Проверяем, что файл не пустой и не превышает 5 МБ.
        if (imageBytes.isEmpty() || imageBytes.size > MAX_FILE_SIZE) {
            throw Exception("Изображение должно быть меньше 5 МБ")
        }

        // Создаём уникальную границу multipart-запроса.
        // Она отделяет заголовки файла от его содержимого.
        val boundary = "GamerHub-${UUID.randomUUID()}"

        // Создаём HTTP-подключение к Supabase Edge Function.
        val connection = URL(FUNCTION_URL)
            .openConnection() as HttpURLConnection

        try {

            // Настраиваем HTTP-запрос.
            connection.requestMethod = "POST"

            // Максимальное время ожидания подключения — 15 секунд.
            connection.connectTimeout = 15000

            // Максимальное время ожидания ответа — 30 секунд.
            connection.readTimeout = 30000

            // Разрешаем отправку данных на сервер.
            connection.doOutput = true

            // Передаём Firebase ID Token.
            // Edge Function использует его для проверки авторизации.
            connection.setRequestProperty(
                "Authorization",
                "Bearer $token"
            )

            // Передаём публичный ключ Supabase.
            connection.setRequestProperty(
                "apikey",
                SUPABASE_PUBLISHABLE_KEY
            )

            // Указываем формат отправляемых данных.
            connection.setRequestProperty(
                "Content-Type",
                "multipart/form-data; boundary=$boundary"
            )

            // Открываем поток для отправки данных на сервер.
            DataOutputStream(connection.outputStream).use { output ->

                // Начинаем multipart-секцию.
                output.writeBytes("--$boundary\r\n")

                // Указываем имя поля file и имя загружаемого файла.
                // Расширение выбирается в зависимости от MIME-типа.
                output.writeBytes(
                    "Content-Disposition: form-data; name=\"file\"; " +
                            "filename=\"image.${
                                when (mimeType) {
                                    "image/jpeg" -> "jpg"
                                    "image/png" -> "png"
                                    else -> "webp"
                                }
                            }\"\r\n"
                )

                // Передаём тип изображения.
                output.writeBytes("Content-Type: $mimeType\r\n\r\n")

                // Отправляем непосредственно содержимое изображения.
                output.write(imageBytes)

                // Завершаем multipart-запрос.
                output.writeBytes("\r\n--$boundary--\r\n")

                // Принудительно отправляем оставшиеся данные.
                output.flush()
            }

            // Получаем HTTP-код ответа сервера.
            val statusCode = connection.responseCode

            // Если запрос успешен, читаем обычный ответ.
            // Если произошла ошибка, читаем errorStream.
            val responseStream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            // Преобразуем ответ сервера в обычный текст.
            val responseText = responseStream
                ?.bufferedReader()
                ?.use {
                    it.readText()
                }
                .orEmpty()

            // Проверяем успешность HTTP-запроса.
            if (statusCode !in 200..299) {

                // Пытаемся получить описание ошибки из JSON.
                val message = try {
                    JSONObject(responseText).optString(
                        "error",
                        "HTTP $statusCode"
                    )
                } catch (_: Exception) {

                    // Если ответ не является JSON,
                    // используем HTTP-код ошибки.
                    "HTTP $statusCode"
                }

                throw Exception("Ошибка загрузки: $message")
            }

            // Преобразуем успешный ответ сервера в JSON.
            val json = JSONObject(responseText)

            // Получаем публичную ссылку на загруженное изображение.
            val publicUrl = json.optString("url")

            // Проверяем, вернул ли сервер ссылку.
            if (publicUrl.isBlank()) {
                throw Exception("Сервер не вернул ссылку на изображение")
            }

            // Возвращаем ссылку вызывающему коду.
            publicUrl

        } finally {

            // Закрываем HTTP-подключение независимо от результата.
            connection.disconnect()
        }
    }

    /**
     * Преобразует Firebase Task в функцию, которую можно ожидать
     * с помощью suspend.
     *
     * Firebase возвращает результат асинхронно.
     * Этот метод позволяет получить его внутри корутины.
     */
    private suspend fun com.google.android.gms.tasks.Task<
            com.google.firebase.auth.GetTokenResult
            >.awaitToken(): String =
        suspendCancellableCoroutine { continuation ->

            // Вызывается, если Firebase успешно получил токен.
            addOnSuccessListener { result ->

                if (continuation.isActive) {

                    // Извлекаем токен из результата.
                    val token = result.token

                    if (token != null) {

                        // Возвращаем токен в корутину.
                        continuation.resume(token)

                    } else {

                        // Если токен отсутствует, передаём ошибку.
                        continuation.resumeWithException(
                            Exception("Firebase не вернул токен")
                        )
                    }
                }
            }

            // Вызывается, если Firebase не смог получить токен.
            addOnFailureListener { error ->

                if (continuation.isActive) {

                    // Передаём ошибку в корутину.
                    continuation.resumeWithException(error)
                }
            }
        }
}