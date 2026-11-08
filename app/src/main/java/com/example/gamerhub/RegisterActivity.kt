package com.example.gamerhub

import android.content.Intent
import android.graphics.Color
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

class RegisterActivity : AppCompatActivity() {

    // Объект для работы с Firebase Authentication.
    private lateinit var auth: FirebaseAuth

    // Объект для работы с базой данных Cloud Firestore.
    private lateinit var db: FirebaseFirestore


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Подключаем XML-разметку экрана регистрации.
        setContentView(R.layout.activity_register)


        // =====================================================
        // ОФОРМЛЕНИЕ ЛОГОТИПА
        // =====================================================

        val title = findViewById<TextView>(R.id.logoText)

        val text = SpannableString("🎮 GamerHub")

        // Эмодзи, пробел и Gamer окрашиваем в белый цвет.
        text.setSpan(
            ForegroundColorSpan(Color.WHITE),
            0,
            8,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Hub окрашиваем в фиолетовый цвет.
        text.setSpan(
            ForegroundColorSpan(Color.rgb(160, 90, 255)),
            8,
            text.length,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        title.text = text


        // =====================================================
        // ПОДКЛЮЧЕНИЕ FIREBASE
        // =====================================================

        auth = FirebaseAuth.getInstance()

        db = FirebaseFirestore.getInstance()


        // =====================================================
        // ПОЛУЧЕНИЕ ЭЛЕМЕНТОВ ИНТЕРФЕЙСА
        // =====================================================

        val usernameInput = findViewById<EditText>(R.id.usernameInput)

        val emailInput = findViewById<EditText>(R.id.emailInput)

        val passwordInput = findViewById<EditText>(R.id.passwordInput)

        val registerButton = findViewById<Button>(R.id.registerButton)

        val loginLink = findViewById<TextView>(R.id.loginLink)


        // =====================================================
        // РЕГИСТРАЦИЯ ПОЛЬЗОВАТЕЛЯ
        // =====================================================

        registerButton.setOnClickListener {

            // Получаем данные из полей ввода.
            val username = usernameInput.text.toString().trim()

            val email = emailInput.text.toString().trim()

            val password = passwordInput.text.toString()


            // Проверяем, заполнены ли все поля.
            if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {

                Toast.makeText(
                    this,
                    "Заполни все поля",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }


            // Создаём аккаунт через Firebase Authentication.
            auth.createUserWithEmailAndPassword(email, password)

                .addOnCompleteListener(this) { task ->

                    if (task.isSuccessful) {

                        // Получаем созданный аккаунт.
                        val user = auth.currentUser

                        if (user != null) {

                            // ---------------------------------
                            // ЛИЧНЫЙ ПРОФИЛЬ
                            // ---------------------------------

                            val profile = hashMapOf(
                                "uid" to user.uid,
                                "email" to email,
                                "username" to username,
                                "createdAt" to FieldValue.serverTimestamp()
                            )


                            // ---------------------------------
                            // ПУБЛИЧНЫЙ ПРОФИЛЬ
                            // ---------------------------------

                            val publicProfile = hashMapOf(
                                "uid" to user.uid,
                                "username" to username
                            )


                            // ---------------------------------
                            // СОХРАНЕНИЕ ДАННЫХ
                            // ---------------------------------

                            // Создаём пакет операций Firestore.
                            val batch = db.batch()

                            // Сохраняем личный профиль.
                            batch.set(
                                db.collection("users").document(user.uid),
                                profile
                            )

                            // Сохраняем публичный профиль.
                            batch.set(
                                db.collection("publicProfiles").document(user.uid),
                                publicProfile
                            )


                            // Выполняем сохранение.
                            batch.commit()

                                .addOnSuccessListener {

                                    Toast.makeText(
                                        this,
                                        "Аккаунт создан! Добро пожаловать в GamerHub!",
                                        Toast.LENGTH_LONG
                                    ).show()


                                    // ---------------------------------
                                    // ПЕРЕХОД НА ГЛАВНЫЙ ЭКРАН
                                    // ---------------------------------

                                    // Создаём Intent для открытия MainActivity.
                                    val intent = Intent(
                                        this,
                                        MainActivity::class.java
                                    )

                                    // Очищаем предыдущие экраны из стека.
                                    // Пользователь не сможет вернуться
                                    // кнопкой «Назад» на регистрацию.
                                    intent.flags =
                                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                                Intent.FLAG_ACTIVITY_CLEAR_TASK

                                    // Открываем главный экран GamerHub.
                                    startActivity(intent)

                                    // Закрываем экран регистрации.
                                    finish()
                                }

                                .addOnFailureListener { error ->

                                    // Если Firestore не сохранил профиль,
                                    // показываем сообщение об ошибке.
                                    Toast.makeText(
                                        this,
                                        "Ошибка сохранения профиля: ${error.localizedMessage}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        }

                    } else {

                        // Если Firebase Authentication не смог создать аккаунт.
                        Toast.makeText(
                            this,
                            task.exception?.localizedMessage
                                ?: "Не удалось создать аккаунт",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }


        // =====================================================
        // ПЕРЕХОД НА ЭКРАН АВТОРИЗАЦИИ
        // =====================================================

        loginLink.setOnClickListener {

            startActivity(
                Intent(this, LoginActivity::class.java)
            )
        }
    }
}