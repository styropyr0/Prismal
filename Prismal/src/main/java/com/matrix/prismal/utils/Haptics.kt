package com.matrix.prismal.utils

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

internal object Haptics {
    fun toggle(view: View, on: Boolean) {
        val type = when {
            Build.VERSION.SDK_INT >= 34 ->
                if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
            else -> HapticFeedbackConstants.CLOCK_TICK
        }
        view.performHapticFeedback(type)
    }

    fun selectionTick(view: View) {
        val type = if (Build.VERSION.SDK_INT >= 34) {
            HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        view.performHapticFeedback(type)
    }
}
