package com.example.gamerhub

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import android.graphics.Color
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val title = findViewById<TextView>(R.id.logoText)

        val text = SpannableString("🎮 GamerHub")
        val gamerStart = text.indexOf("Gamer")
        val hubStart = text.indexOf("Hub")

// Название Gamer — белое
        text.setSpan(
            ForegroundColorSpan(Color.WHITE),
            gamerStart,
            hubStart,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )

// Название Hub — фиолетовое
        text.setSpan(
            ForegroundColorSpan(Color.rgb(160, 90, 255)),
            hubStart,
            text.length,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        title.text = text
        auth = FirebaseAuth.getInstance()

        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val registerLink = findViewById<TextView>(R.id.registerLink)

        // Обработка нажатия на кнопку входа
        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(
                    this,
                    "Заполни все поля",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            // Проверяем данные через Firebase Authentication
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->

                    if (task.isSuccessful) {
                        Toast.makeText(
                            this,
                            "Вход выполнен!",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Открываем главный экран
                        val intent = Intent(this, MainActivity::class.java)
                        startActivity(intent)
                        finish()

                    } else {
                        Toast.makeText(
                            this,
                            task.exception?.localizedMessage
                                ?: "Не удалось войти",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }

        // Переход к регистрации
        registerLink.setOnClickListener {
            startActivity(
                Intent(this, RegisterActivity::class.java)
            )
        }
    }
}
