package com.example.gamerhub

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// Объект для создания и хранения экземпляра Retrofit.
// object означает, что экземпляр будет только один на всё приложение.
object RetrofitInstance {

    // Базовый адрес RAWG API.
    // Все запросы к API будут формироваться относительно этого адреса.
    private const val BASE_URL = "https://api.rawg.io/api/"

    // Создаём API-интерфейс лениво (lazy).
    // Это означает, что Retrofit будет создан только при первом обращении к api.
    // После создания экземпляр будет использоваться повторно.
    val api: RawgApi by lazy {

        Retrofit.Builder()

            // Указываем базовый адрес сервера.
            .baseUrl(BASE_URL)

            // Подключаем Gson для преобразования JSON-ответов сервера
            // в Kotlin-объекты.
            .addConverterFactory(GsonConverterFactory.create())

            // Создаём экземпляр Retrofit.
            .build()

            // На основе интерфейса RawgApi создаём объект
            // для выполнения запросов к RAWG API.
            .create(RawgApi::class.java)
    }
}