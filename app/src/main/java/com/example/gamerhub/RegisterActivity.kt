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
class RegisterActivity : AppCompatActivity() {

    // Объект для работы с Firebase Authentication
    private lateinit var auth: FirebaseAuth

    // Объект для работы с базой данных Firestore
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)



        val title = findViewById<TextView>(R.id.logoText)

// Сохраняем приставку и пробел перед названием
        val text = SpannableString("🎮 GamerHub")

// Первые символы — приставка, пробел и Gamer — белые
        text.setSpan(
            ForegroundColorSpan(Color.WHITE),
            0,
            8,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )

// Hub — фиолетовый
        text.setSpan(
            ForegroundColorSpan(Color.rgb(160, 90, 255)),
            8,
            text.length,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        title.text = text

        // Получаем доступ к Firebase Authentication
        auth = FirebaseAuth.getInstance()

        db = FirebaseFirestore.getInstance()

        // Находим элементы интерфейса по их ID
        val usernameInput = findViewById<EditText>(R.id.usernameInput)
        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val registerButton = findViewById<Button>(R.id.registerButton)
        val loginLink = findViewById<TextView>(R.id.loginLink)

        // Обрабатываем нажатие на кнопку регистрации
        registerButton.setOnClickListener {

            val username = usernameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()

            if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Заполни все поля", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Создаём аккаунт в Firebase
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->

                    if (task.isSuccessful) {

                        // Получаем созданный аккаунт
                        val user = auth.currentUser


                        if (user != null) {

                            // Личные данные пользователя
                            val profile = hashMapOf(
                                "uid" to user.uid,
                                "email" to email,
                                "username" to username,
                                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            )

                            val publicProfile = hashMapOf(
                                "uid" to user.uid,
                                "username" to username
                            )

                            // Готовим запись в две коллекции
                            val batch = db.batch()

                            batch.set(
                                db.collection("users").document(user.uid),
                                profile
                            )

                            batch.set(
                                db.collection("publicProfiles").document(user.uid),
                                publicProfile
                            )

                            // Сохраняем обе записи
                            batch.commit()
                                .addOnSuccessListener {
                                    Toast.makeText(
                                        this,
                                        "Аккаунт создан! Профиль сохранён.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                .addOnFailureListener { error ->
                                    Toast.makeText(
                                        this,
                                        "Ошибка сохранения профиля: ${error.localizedMessage}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        }


                    } else {

                        Toast.makeText(
                            this,
                            task.exception?.localizedMessage
                                ?: "Не удалось создать аккаунт",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }

        loginLink.setOnClickListener {
            startActivity(
                Intent(this, LoginActivity::class.java)
            )
        }
    }
}
