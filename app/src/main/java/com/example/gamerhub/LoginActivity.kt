package com.example.gamerhub

// Intent используется для перехода между Activity.
import android.content.Intent

// Bundle используется для передачи и восстановления состояния Activity.
import android.os.Bundle

// Button — кнопка для выполнения действия.
import android.widget.Button

// EditText — поле для ввода электронной почты и пароля.
import android.widget.EditText

// TextView — элемент для отображения текста и ссылок.
import android.widget.TextView

// Toast — небольшое всплывающее сообщение.
import android.widget.Toast

// FirebaseAuth отвечает за регистрацию, авторизацию
// и управление текущим пользователем через Firebase Authentication.
import com.google.firebase.auth.FirebaseAuth

// Color позволяет задавать цвета элементов интерфейса.
import android.graphics.Color

// SpannableString позволяет форматировать отдельные части строки.
import android.text.SpannableString

// ForegroundColorSpan задаёт цвет определённой части текста.
import android.text.style.ForegroundColorSpan
//подключает класс AppCompatActivity, который является основой для экранов Android-приложения.
import androidx.appcompat.app.AppCompatActivity
// LoginActivity — Activity, отвечающая за экран входа в аккаунт.
class LoginActivity : AppCompatActivity() {

    // Объект Firebase Authentication.
    //
    // lateinit означает, что переменная будет инициализирована позже,
    // до того как мы начнём её использовать.
    private lateinit var auth: FirebaseAuth


    // onCreate вызывается при создании Activity.
    // Здесь настраивается интерфейс и обработчики нажатий.
    override fun onCreate(savedInstanceState: Bundle?) {

        // Вызываем реализацию родительского класса.
        super.onCreate(savedInstanceState)

        // Подключаем XML-разметку экрана авторизации.
        setContentView(R.layout.activity_login)


        // Находим TextView, в котором отображается логотип GamerHub.
        val title = findViewById<TextView>(R.id.logoText)

        // Создаём форматированную строку с логотипом.
        val text = SpannableString("🎮 GamerHub")

        // Находим позицию, с которой начинается слово Gamer.
        val gamerStart = text.indexOf("Gamer")

        // Находим позицию, с которой начинается слово Hub.
        val hubStart = text.indexOf("Hub")


        // Название Gamer будет белого цвета.
        text.setSpan(
            ForegroundColorSpan(Color.WHITE),

            // Начальная позиция слова Gamer.
            gamerStart,

            // Конечная позиция слова Gamer.
            // Одновременно это начало слова Hub.
            hubStart,

            // Стиль применяется только к указанному диапазону текста.
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )


        // Название Hub будет фиолетового цвета.
        text.setSpan(
            ForegroundColorSpan(Color.rgb(160, 90, 255)),

            // Начальная позиция слова Hub.
            hubStart,

            // Конец всей строки.
            text.length,

            // Стиль применяется только к указанному диапазону текста.
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Устанавливаем оформленный логотип в TextView.
        title.text = text


        // Получаем экземпляр Firebase Authentication.
        // Через него будем проверять введённые пользователем данные.
        auth = FirebaseAuth.getInstance()


        // Находим поле для ввода электронной почты.
        val emailInput = findViewById<EditText>(R.id.emailInput)

        // Находим поле для ввода пароля.
        val passwordInput = findViewById<EditText>(R.id.passwordInput)

        // Находим кнопку входа.
        val loginButton = findViewById<Button>(R.id.loginButton)

        // Находим текстовую ссылку для перехода к регистрации.
        val registerLink = findViewById<TextView>(R.id.registerLink)


        // Обработка нажатия на кнопку входа.
        loginButton.setOnClickListener {

            // Получаем введённую электронную почту.
            // toString() преобразует содержимое поля в строку.
            // trim() удаляет пробелы в начале и конце.
            val email = emailInput.text.toString().trim()

            // Получаем введённый пароль.
            // Здесь trim() не используется, поскольку пробелы
            // могут быть частью пароля.
            val password = passwordInput.text.toString()


            // Проверяем, заполнены ли оба поля.
            if (email.isEmpty() || password.isEmpty()) {

                // Если хотя бы одно поле пустое,
                // показываем пользователю предупреждение.
                Toast.makeText(
                    this,
                    "Заполни все поля",
                    Toast.LENGTH_SHORT
                ).show()

                // Прерываем обработчик нажатия.
                // Авторизация с пустыми полями не выполняется.
                return@setOnClickListener
            }


            // Проверяем введённые данные через Firebase Authentication.
            //
            // email — электронная почта пользователя.
            // password — введённый пароль.
            //
            // Firebase самостоятельно проверяет,
            // существует ли такой аккаунт и правильный ли пароль.
            auth.signInWithEmailAndPassword(email, password)

                // addOnCompleteListener вызывается после завершения запроса.
                // task содержит результат попытки авторизации.
                .addOnCompleteListener(this) { task ->

                    // Проверяем, успешно ли завершился вход.
                    if (task.isSuccessful) {

                        // Сообщаем пользователю об успешной авторизации.
                        Toast.makeText(
                            this,
                            "Вход выполнен!",
                            Toast.LENGTH_SHORT
                        ).show()


                        // Создаём Intent для перехода на главный экран.
                        val intent = Intent(
                            this,
                            MainActivity::class.java
                        )

                        // Запускаем MainActivity.
                        startActivity(intent)

                        // Закрываем LoginActivity.
                        //
                        // Благодаря этому после входа пользователь
                        // не сможет вернуться на экран авторизации
                        // обычной кнопкой «Назад».
                        finish()

                    } else {

                        // Если авторизация не удалась,
                        // получаем описание ошибки от Firebase.
                        //
                        // localizedMessage — сообщение об ошибке.
                        // Если Firebase не предоставил его,
                        // показываем текст «Не удалось войти».
                        Toast.makeText(
                            this,
                            task.exception?.localizedMessage
                                ?: "Не удалось войти",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }


        // Обработка нажатия на ссылку регистрации.
        registerLink.setOnClickListener {

            // Создаём Intent для перехода на RegisterActivity.
            startActivity(
                Intent(this, RegisterActivity::class.java)
            )
        }
    }
}