package com.example.gamerhub

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent

import androidx.recyclerview.widget.RecyclerView


class NonScrollableRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {


    // Запрещаем RecyclerView
    // самостоятельно прокручиваться

    override fun onInterceptTouchEvent(
        event: MotionEvent
    ): Boolean {

        return false
    }


    // Не позволяем RecyclerView
    // запрещать родительскому ScrollView
    // перехватывать свайп

    override fun requestDisallowInterceptTouchEvent(
        disallowIntercept: Boolean
    ) {

        // Ничего не делаем
    }
}