
package com.example.gamerhub

import android.app.AlertDialog
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

class ProfileManager(
    private val view: View
) {
    fun setup() {
        val editButton = view.findViewById<View>(R.id.editProfileButton)

        editButton.setOnClickListener {
            showEditDialog()
        }
    }

    private fun showEditDialog() {
        val usernameView = view.findViewById<TextView>(R.id.profileUsername)
        val tagView = view.findViewById<TextView>(R.id.profileTag)
        val bioView = view.findViewById<TextView>(R.id.profileBio)

        val fields = android.widget.LinearLayout(view.context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }

        val usernameInput = EditText(view.context).apply {
            hint = "Имя пользователя"
            setText(usernameView.text)
        }

        val tagInput = EditText(view.context).apply {
            hint = "@username"
            setText(tagView.text)
        }

        val bioInput = EditText(view.context).apply {
            hint = "О себе"
            setText(bioView.text)
            minLines = 3
            gravity = android.view.Gravity.TOP
        }

        fields.addView(usernameInput)
        fields.addView(tagInput)
        fields.addView(bioInput)

        AlertDialog.Builder(view.context)
            .setTitle("Редактировать профиль")
            .setView(fields)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить") { _, _ ->
                val username = usernameInput.text.toString().trim()
                val tag = tagInput.text.toString().trim()
                val bio = bioInput.text.toString().trim()

                if (username.isBlank() || tag.isBlank()) {
                    Toast.makeText(
                        view.context,
                        "Имя и тег не должны быть пустыми",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                usernameView.text = username
                tagView.text = if (tag.startsWith("@")) tag else "@$tag"
                bioView.text = bio
            }
            .show()
    }
}
