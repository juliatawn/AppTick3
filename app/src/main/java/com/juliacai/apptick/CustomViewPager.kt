package com.juliacai.apptick

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager

class CustomViewPager(context: Context, attrs: AttributeSet?) : ViewPager(context, attrs) {

    var isPagingEnabled: Boolean = true

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            performClick()
        }
        return isPagingEnabled && super.onTouchEvent(event)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return isPagingEnabled && super.onInterceptTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}
