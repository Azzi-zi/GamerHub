
package com.example.gamerhub

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatsManager(
    private val activity: AppCompatActivity,
    private val openPage: (Int) -> Unit
) {
    private val db = FirebaseFirestore.getInstance()

    private var currentChatId: String? = null
    private var currentChatUsername = ""
    private var chatListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null

    private data class ChatRowData(
        val chatId: String,
        val username: String,
        val lastMessage: String,
        val lastMessageAt: Timestamp?
    )

    fun setupPage(layoutId: Int, view: View) {
        if (layoutId != R.layout.screen_chats) {
            chatListener?.remove()
            chatListener = null
        }

        if (layoutId != R.layout.screen_private_chat) {
            messagesListener?.remove()
            messagesListener = null
        }

        when (layoutId) {
            R.layout.screen_chats -> {
                setupPlayerSearch(view)
                setupChatList(view)

                view.findViewById<View>(R.id.newChatButton)
                    ?.setOnClickListener {
                        openPage(R.layout.screen_new_chat)
                    }
            }

            R.layout.screen_new_chat -> setupUserSearch(view)

            R.layout.screen_private_chat -> setupPrivateChat(view)
        }
    }

    // ---------------------------------------------------------
    // Поиск пользователей для создания личного чата
    // ---------------------------------------------------------

    private fun setupUserSearch(view: View) {
        val searchInput =
            view.findViewById<EditText>(R.id.userSearchInput)
        val resultsContainer =
            view.findViewById<LinearLayout>(R.id.userResultsContainer)
        val statusText =
            view.findViewById<TextView>(R.id.searchUsersStatus)

        val currentUid = FirebaseAuth.getInstance().currentUser?.uid

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                resultsContainer.removeAllViews()

                if (query.isEmpty()) {
                    statusText.text = "Введи имя пользователя"
                    return
                }

                statusText.text = "Ищем пользователей..."

                db.collection("publicProfiles")
                    .orderBy("username")
                    .startAt(query)
                    .endAt(query + "\uf8ff")
                    .limit(20)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        resultsContainer.removeAllViews()

                        val users = snapshot.documents.filter {
                            it.getString("uid") != currentUid
                        }

                        if (users.isEmpty()) {
                            statusText.text = "Пользователи не найдены"
                            return@addOnSuccessListener
                        }

                        statusText.text = "Найдено: ${users.size}"

                        users.forEach { document ->
                            val uid = document.getString("uid")
                                ?: document.id
                            val username =
                                document.getString("username") ?: "Игрок"

                            val item = TextView(activity).apply {
                                text = "🎮  $username"
                                textSize = 16f
                                setTextColor(Color.WHITE)
                                setPadding(16, 18, 16, 18)
                                gravity = Gravity.CENTER_VERTICAL
                                background = roundedBackground(
                                    Color.rgb(26, 26, 32), 14
                                )
                            }

                            val params = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                bottomMargin = dp(8)
                            }

                            resultsContainer.addView(item, params)

                            item.setOnClickListener {
                                createPrivateChat(uid, username)
                            }
                        }
                    }
                    .addOnFailureListener { error ->
                        statusText.text = "Ошибка поиска пользователей"
                        Toast.makeText(
                            activity,
                            error.message ?: "Не удалось выполнить поиск",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
        })
    }

    // ---------------------------------------------------------
    // Поиск игроков прямо на экране сообщений
    // ---------------------------------------------------------

    private fun setupPlayerSearch(view: View) {
        val search = view.findViewById<EditText>(R.id.chatSearch)
        val results =
            view.findViewById<LinearLayout>(R.id.playerSearchResults)

        val currentUid = FirebaseAuth.getInstance().currentUser?.uid

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) {
                val query = s?.toString()?.trim().orEmpty()
                results.removeAllViews()

                if (query.isEmpty()) {
                    results.visibility = View.GONE
                    return
                }

                results.visibility = View.VISIBLE

                db.collection("publicProfiles")
                    .orderBy("username")
                    .startAt(query)
                    .endAt(query + "\uf8ff")
                    .limit(20)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        results.removeAllViews()

                        var found = false

                        snapshot.documents.forEach { document ->
                            val uid =
                                document.getString("uid") ?: document.id
                            val username =
                                document.getString("username") ?: return@forEach

                            if (uid == currentUid) return@forEach

                            found = true

                            val item = TextView(activity).apply {
                                text = "🎮  $username"
                                setTextColor(Color.WHITE)
                                textSize = 16f
                                setPadding(16, 14, 16, 14)
                                background = roundedBackground(
                                    Color.rgb(26, 26, 32), 12
                                )
                            }

                            val params = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                topMargin = dp(4)
                            }

                            results.addView(item, params)

                            item.setOnClickListener {
                                createPrivateChat(uid, username)
                            }
                        }

                        if (!found) {
                            results.addView(TextView(activity).apply {
                                text = "Игроки не найдены"
                                setTextColor(Color.LTGRAY)
                                setPadding(16, 14, 16, 14)
                            })
                        }
                    }
                    .addOnFailureListener {
                        results.removeAllViews()
                        results.addView(TextView(activity).apply {
                            text = "Не удалось загрузить игроков"
                            setTextColor(Color.LTGRAY)
                            setPadding(16, 14, 16, 14)
                        })
                    }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    // ---------------------------------------------------------
    // Создание чата или открытие уже существующего
    // ---------------------------------------------------------

    private fun createPrivateChat(
        otherUid: String,
        otherUsername: String
    ) {
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser == null) {
            Toast.makeText(
                activity, "Сначала войди в аккаунт",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (currentUser.uid == otherUid) {
            Toast.makeText(
                activity, "Нельзя создать чат с собой",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val participants = listOf(currentUser.uid, otherUid).sorted()
        val chatId = "${participants[0]}_${participants[1]}"
        val chatRef = db.collection("chats").document(chatId)

        chatRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    openPrivateChat(chatId, otherUsername)
                    return@addOnSuccessListener
                }

                val chatData = hashMapOf(
                    "chatId" to chatId,
                    "participants" to participants,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "lastMessage" to "",
                    "lastMessageAt" to FieldValue.serverTimestamp()
                )

                chatRef.set(chatData)
                    .addOnSuccessListener {
                        openPrivateChat(chatId, otherUsername)
                    }
                    .addOnFailureListener { error ->
                        Toast.makeText(
                            activity,
                            "Не удалось создать чат: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .addOnFailureListener { error ->
                Toast.makeText(
                    activity,
                    "Ошибка открытия чата: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun openPrivateChat(chatId: String, username: String) {
        currentChatId = chatId
        currentChatUsername = username
        openPage(R.layout.screen_private_chat)
    }

    // ---------------------------------------------------------
    // Загрузка реального списка переписок из Firestore
    // ---------------------------------------------------------

    private fun setupChatList(view: View) {
        val container =
            view.findViewById<LinearLayout>(R.id.chatsListContainer)
        val emptyText =
            view.findViewById<TextView>(R.id.chatsEmptyText)

        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (uid == null) {
            emptyText.text = "Войди в аккаунт, чтобы увидеть чаты"
            return
        }

        chatListener = db.collection("chats")
            .whereArrayContains("participants", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    emptyText.visibility = View.VISIBLE
                    emptyText.text = "Не удалось загрузить чаты"
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                val documents = snapshot.documents.sortedByDescending {
                    it.getTimestamp("lastMessageAt")?.toDate()?.time ?: 0L
                }

                container.removeAllViews()

                if (documents.isEmpty()) {
                    emptyText.visibility = View.VISIBLE
                    emptyText.text =
                        "У тебя пока нет чатов.\nНажми +, чтобы найти игрока."
                    return@addSnapshotListener
                }

                emptyText.visibility = View.GONE

                // Получаем имена собеседников. После загрузки всех профилей
                // отображаем карточки в порядке последнего сообщения.
                val rows = arrayOfNulls<ChatRowData>(documents.size)
                var remaining = documents.size

                documents.forEachIndexed { index, document ->
                    val participants =
                        document.get("participants") as? List<*>

                    val otherUid = participants
                        ?.filterIsInstance<String>()
                        ?.firstOrNull { it != uid }

                    if (otherUid == null) {
                        remaining--
                        if (remaining == 0) renderChatRows(container, rows)
                        return@forEachIndexed
                    }

                    db.collection("publicProfiles")
                        .document(otherUid)
                        .get()
                        .addOnSuccessListener { profile ->
                            rows[index] = ChatRowData(
                                chatId = document.id,
                                username = profile.getString("username")
                                    ?: "Игрок",
                                lastMessage = document.getString("lastMessage")
                                    ?: "Начните общение!",
                                lastMessageAt =
                                    document.getTimestamp("lastMessageAt")
                            )

                            remaining--
                            if (remaining == 0) {
                                renderChatRows(container, rows)
                            }
                        }
                        .addOnFailureListener {
                            rows[index] = ChatRowData(
                                chatId = document.id,
                                username = "Игрок",
                                lastMessage = document.getString("lastMessage")
                                    ?: "Начните общение!",
                                lastMessageAt =
                                    document.getTimestamp("lastMessageAt")
                            )

                            remaining--
                            if (remaining == 0) {
                                renderChatRows(container, rows)
                            }
                        }
                }
            }
    }

    private fun renderChatRows(
        container: LinearLayout,
        rows: Array<ChatRowData?>
    ) {
        container.removeAllViews()

        rows.filterNotNull().forEach { chat ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = roundedBackground(
                    Color.rgb(26, 26, 32), 14
                )
                isClickable = true
                isFocusable = true
            }

            val avatar = TextView(activity).apply {
                text = "🎮"
                textSize = 23f
                gravity = Gravity.CENTER
                background = roundedBackground(
                    Color.rgb(48, 38, 62), 12
                )
            }

            row.addView(
                avatar,
                LinearLayout.LayoutParams(dp(50), dp(50))
            )

            val textColumn = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
            }

            val name = TextView(activity).apply {
                text = chat.username
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                maxLines = 1
            }

            val lastMessage = TextView(activity).apply {
                text = chat.lastMessage.ifBlank { "Начните общение!" }
                textSize = 13f
                setTextColor(Color.rgb(146, 146, 157))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            textColumn.addView(name)
            textColumn.addView(lastMessage)

            val textParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ).apply {
                marginStart = dp(12)
            }
            row.addView(textColumn, textParams)

            val time = TextView(activity).apply {
                text = formatTime(chat.lastMessageAt)
                textSize = 11f
                setTextColor(Color.rgb(119, 119, 128))
            }

            val timeParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(8)
            }
            row.addView(time, timeParams)

            val rowParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
            container.addView(row, rowParams)

            row.setOnClickListener {
                currentChatId = chat.chatId
                currentChatUsername = chat.username
                openPage(R.layout.screen_private_chat)
            }
        }
    }

    // ---------------------------------------------------------
    // Экран переписки: загрузка истории и отправка сообщений
    // ---------------------------------------------------------

    private fun setupPrivateChat(view: View) {
        val chatId = currentChatId ?: run {
            Toast.makeText(
                activity, "Не удалось открыть чат",
                Toast.LENGTH_SHORT
            ).show()
            openPage(R.layout.screen_chats)
            return
        }

        val title = view.findViewById<TextView>(R.id.chatTitle)
        val backButton = view.findViewById<TextView>(R.id.backButton)
        val input = view.findViewById<EditText>(R.id.messageInput)
        val sendButton = view.findViewById<TextView>(R.id.sendButton)
        val messagesContainer =
            view.findViewById<LinearLayout>(R.id.messagesContainer)
        val messagesScroll =
            view.findViewById<ScrollView>(R.id.messagesScroll)

        title.text = currentChatUsername

        backButton.setOnClickListener {
            openPage(R.layout.screen_chats)
        }


        fun sendMessage() {
            val text = input.text.toString().trim()

            if (text.isEmpty()) return

            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid == null) {
                Toast.makeText(
                    activity,
                    "Сначала войди в аккаунт",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            val chatRef = db.collection("chats").document(chatId)
            val messageRef = chatRef.collection("messages").document()

            val message = hashMapOf(
                "senderId" to uid,
                "text" to text,
                "createdAt" to FieldValue.serverTimestamp(),
                "readBy" to listOf(uid)
            )

            sendButton.isEnabled = false

            messageRef.set(message)
                .addOnSuccessListener {
                    chatRef.update(
                        mapOf(
                            "lastMessage" to text,
                            "lastMessageAt" to FieldValue.serverTimestamp()
                        )
                    ).addOnFailureListener { error ->
                        Toast.makeText(
                            activity,
                            "Сообщение отправлено, но превью чата не обновилось: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    input.text.clear()
                    sendButton.isEnabled = true
                }
                .addOnFailureListener { error ->
                    sendButton.isEnabled = true

                    Toast.makeText(
                        activity,
                        "Не удалось отправить сообщение: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }


        sendButton.setOnClickListener { sendMessage() }

        input.setOnEditorActionListener { _, actionId, event ->
            val sendAction = actionId == EditorInfo.IME_ACTION_SEND
            val enterPressed =
                event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                        event.action == KeyEvent.ACTION_UP &&
                        event.isShiftPressed.not()

            if (sendAction || enterPressed) {
                sendMessage()
                true
            } else {
                false
            }
        }

        messagesListener = db.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("createdAt")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(
                        activity, "Не удалось загрузить сообщения",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                messagesContainer.removeAllViews()


                val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

                snapshot.documents.forEach { document ->
                    val senderId = document.getString("senderId") ?: ""
                    val text = document.getString("text") ?: return@forEach
                    val isMine = senderId == currentUid

                    // Получаем список пользователей, прочитавших сообщение
                    val readBy = (document.get("readBy") as? List<*>)
                        ?.filterIsInstance<String>()
                        ?: emptyList()

                    // Если это чужое сообщение, отмечаем его прочитанным
                    if (!isMine && currentUid.isNotEmpty() && currentUid !in readBy) {
                        document.reference.update(
                            "readBy",
                            FieldValue.arrayUnion(currentUid)
                        )
                    }

                    // Проверяем, прочитал ли сообщение собеседник
                    val isReadByRecipient = isMine && readBy.any { it != senderId }

                    // Получаем время сообщения
                    val timestamp = document.getTimestamp("createdAt")
                    val timeText = timestamp?.toDate()?.let {
                        java.text.SimpleDateFormat(
                            "HH:mm",
                            java.util.Locale.getDefault()
                        ).format(it)
                    } ?: ""

                    // Контейнер сообщения
                    val wrapper = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = if (isMine) Gravity.END else Gravity.START
                    }

                    // Контейнер текста и времени
                    val bubble = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(14), dp(10), dp(14), dp(8))

                        background = roundedBackground(
                            if (isMine) Color.rgb(112, 58, 180)
                            else Color.rgb(40, 40, 48),
                            16
                        )
                    }

                    // Текст сообщения
                    val messageText = TextView(activity).apply {
                        this.text = text
                        textSize = 15f
                        setTextColor(Color.WHITE)
                    }

                    // Время и галочки
                    val metadata = TextView(activity).apply {
                        val status = if (isMine) {
                            if (isReadByRecipient) "✓✓" else "✓"
                        } else {
                            ""
                        }

                        this.text = listOf(timeText, status)
                            .filter { it.isNotEmpty() }
                            .joinToString("  ")

                        textSize = 11f
                        setTextColor(Color.rgb(210, 195, 230))
                        gravity = Gravity.END
                    }

                    bubble.addView(messageText)
                    bubble.addView(metadata)

                    val bubbleParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dp(8)
                        marginStart = dp(36)
                        marginEnd = dp(36)
                    }

                    wrapper.addView(bubble, bubbleParams)

                    messagesContainer.addView(
                        wrapper,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                }


                messagesScroll.post {
                    messagesScroll.fullScroll(View.FOCUS_DOWN)
                }
            }
    }

    // ---------------------------------------------------------
    // Вспомогательные функции оформления
    // ---------------------------------------------------------

    private fun roundedBackground(
        color: Int,
        radiusDp: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun dp(value: Int): Int {
        return (value * activity.resources.displayMetrics.density).toInt()
    }

    private fun formatTime(timestamp: Timestamp?): String {
        if (timestamp == null) return ""

        val date = timestamp.toDate()
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            .format(Date())
        val messageDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            .format(date)

        val pattern = if (today == messageDay) "HH:mm" else "dd.MM"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(date)
    }
}
