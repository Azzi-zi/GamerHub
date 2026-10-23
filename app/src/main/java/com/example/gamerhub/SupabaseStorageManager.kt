
package com.example.gamerhub

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object SupabaseStorageManager {

    private const val FUNCTION_URL =
        "https://rewxoagdwwstvuuiqtqc.supabase.co/functions/v1/funk"

    private const val SUPABASE_PUBLISHABLE_KEY =
        "sb_publishable_gjoGJw1PsN3_hXth7DuIsg_ruUUnIZ5"

    private const val MAX_FILE_SIZE = 5L * 1024 * 1024

    /**
     * Загружает изображение в Supabase Storage.
     * Возвращает публичную ссылку на файл.
     */
    suspend fun uploadImage(
        context: Context,
        imageUri: Uri
    ): String = withContext(Dispatchers.IO) {

        val user = com.google.firebase.auth.FirebaseAuth
            .getInstance()
            .currentUser
            ?: throw Exception("Сначала войди в аккаунт")

        // Получаем действующий Firebase ID token
        val token = user.getIdToken(false)
            .awaitToken()

        val resolver = context.contentResolver

        val mimeType = resolver.getType(imageUri)
            ?: throw Exception("Не удалось определить формат изображения")

        if (mimeType !in listOf(
                "image/jpeg",
                "image/png",
                "image/webp"
            )
        ) {
            throw Exception("Разрешены только JPEG, PNG и WebP")
        }

        val inputStream = resolver.openInputStream(imageUri)
            ?: throw Exception("Не удалось открыть изображение")

        val imageBytes = inputStream.use { it.readBytes() }

        if (imageBytes.isEmpty() || imageBytes.size > MAX_FILE_SIZE) {
            throw Exception("Изображение должно быть меньше 5 МБ")
        }

        val boundary = "GamerHub-${UUID.randomUUID()}"
        val connection = URL(FUNCTION_URL)
            .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.doOutput = true

            connection.setRequestProperty(
                "Authorization",
                "Bearer $token"
            )
            connection.setRequestProperty(
                "apikey",
                SUPABASE_PUBLISHABLE_KEY
            )
            connection.setRequestProperty(
                "Content-Type",
                "multipart/form-data; boundary=$boundary"
            )

            DataOutputStream(connection.outputStream).use { output ->

                output.writeBytes("--$boundary\r\n")
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
                output.writeBytes("Content-Type: $mimeType\r\n\r\n")
                output.write(imageBytes)
                output.writeBytes("\r\n--$boundary--\r\n")
                output.flush()
            }

            val statusCode = connection.responseCode

            val responseStream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseText = responseStream
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            if (statusCode !in 200..299) {
                val message = try {
                    JSONObject(responseText).optString(
                        "error",
                        "HTTP $statusCode"
                    )
                } catch (_: Exception) {
                    "HTTP $statusCode"
                }

                throw Exception("Ошибка загрузки: $message")
            }

            val json = JSONObject(responseText)
            val publicUrl = json.optString("url")

            if (publicUrl.isBlank()) {
                throw Exception("Сервер не вернул ссылку на изображение")
            }

            publicUrl
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun com.google.android.gms.tasks.Task<
            com.google.firebase.auth.GetTokenResult
            >.awaitToken(): String =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->

            addOnSuccessListener { result ->
                if (continuation.isActive) {
                    val token = result.token

                    if (token != null) {
                        continuation.resume(token) {}
                    } else {
                        continuation.resumeWith(
                            Result.failure(
                                Exception("Firebase не вернул токен")
                            )
                        )
                    }
                }
            }

            addOnFailureListener { error ->
                if (continuation.isActive) {
                    continuation.resumeWith(Result.failure(error))
                }
            }
        }
}
