
package com.example.gamerhub

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FollowListManager(
    private val activity: AppCompatActivity,
    private val onUserClick: (String) -> Unit
) {

    private val db = FirebaseFirestore.getInstance()

    private val purple = Color.rgb(155, 122, 255)
    private val white = Color.WHITE
    private val gray = Color.rgb(170, 170, 180)
    private val dark = Color.rgb(26, 26, 32)

    fun showFollowers(userId: String) {
        showUsers(userId, "followers", "Подписчики")
    }

    fun showFollowing(userId: String) {
        showUsers(userId, "following", "Подписки")
    }

    private fun showUsers(
        userId: String,
        collectionName: String,
        title: String
    ) {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 12)
        }

        val loading = TextView(activity).apply {
            text = "Загрузка списка..."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(gray)
            setPadding(12, 32, 12, 32)
        }

        container.addView(loading)

        val scrollView = ScrollView(activity).apply {
            addView(container)
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(scrollView)
            .setNegativeButton("Закрыть", null)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(
                ColorDrawable(dark)
            )

            dialog.findViewById<TextView>(
                activity.resources.getIdentifier(
                    "alertTitle",
                    "id",
                    "android"
                )
            )?.setTextColor(white)

            dialog.getButton(
                AlertDialog.BUTTON_NEGATIVE
            )?.setTextColor(purple)
        }

        dialog.show()

        activity.lifecycleScope.launch {
            try {
                val documents = db.collection("users")
                    .document(userId)
                    .collection(collectionName)
                    .get()
                    .await()

                container.removeAllViews()

                if (documents.isEmpty) {
                    container.addView(
                        TextView(activity).apply {
                            text = if (collectionName == "followers") {
                                "Пока нет подписчиков"
                            } else {
                                "Пока нет подписок"
                            }

                            textSize = 14f
                            gravity = Gravity.CENTER
                            setTextColor(gray)
                            setPadding(12, 32, 12, 32)
                        }
                    )

                    return@launch
                }

                for (document in documents.documents) {
                    val targetUid = document.getString("uid")
                        ?: document.id

                    if (targetUid.isBlank()) continue

                    val profile = try {
                        db.collection("publicProfiles")
                            .document(targetUid)
                            .get()
                            .await()
                    } catch (_: Exception) {
                        null
                    }

                    val username = profile
                        ?.getString("username")
                        ?.takeIf { it.isNotBlank() }
                        ?: document.getString("username")
                        ?: "Игрок GamerHub"

                    val row = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(16, 18, 16, 18)
                        isClickable = true
                        isFocusable = true
                        setBackgroundResource(
                            android.R.drawable.list_selector_background
                        )
                    }

                    val name = TextView(activity).apply {
                        text = username
                        textSize = 16f
                        setTextColor(white)
                        setTypeface(null, Typeface.BOLD)
                    }

                    val tag = TextView(activity).apply {
                        text = profile?.getString("tag")
                            ?: "@${username.lowercase().replace(" ", "_")}"
                        textSize = 12f
                        setTextColor(gray)
                    }

                    row.addView(name)
                    row.addView(tag)

                    row.setOnClickListener {
                        dialog.dismiss()
                        onUserClick(targetUid)
                    }

                    container.addView(
                        row,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )

                    val separator = android.view.View(activity).apply {
                        setBackgroundColor(
                            Color.rgb(48, 48, 57)
                        )
                    }

                    container.addView(
                        separator,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            1
                        )
                    )
                }

            } catch (e: Exception) {
                container.removeAllViews()

                container.addView(
                    TextView(activity).apply {
                        text = "Не удалось загрузить список.\nПроверь правила Firestore."
                        textSize = 14f
                        gravity = Gravity.CENTER
                        setTextColor(gray)
                        setPadding(12, 32, 12, 32)
                    }
                )

                Toast.makeText(
                    activity,
                    "Ошибка загрузки: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
