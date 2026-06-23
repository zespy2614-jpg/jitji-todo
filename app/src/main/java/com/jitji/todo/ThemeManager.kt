package com.jitji.todo

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

object ThemeManager {

    private const val PREFS_NAME = "jitji_theme"
    private const val KEY_THEME_INDEX = "theme_index"
    private const val KEY_FONT_SCALE = "font_scale"

    // Each preset: bgColor (ARGB int), textColor (ARGB int)
    data class Preset(val bgColor: Int, val textColor: Int)

    val presets = listOf(
        Preset(Color.WHITE, Color.parseColor("#333333")),            // 기본
        Preset(Color.parseColor("#2C2C2E"), Color.parseColor("#F5F5F7")), // 다크
        Preset(Color.parseColor("#F4ECD8"), Color.parseColor("#4A3B2A")), // 세피아
        Preset(Color.parseColor("#E6F4F1"), Color.parseColor("#1E3A34"))  // 민트
    )

    fun getThemeIndex(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_THEME_INDEX, 0)

    fun setThemeIndex(ctx: Context, index: Int) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_THEME_INDEX, index).apply()
    }

    fun getFontScale(ctx: Context): Float =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_FONT_SCALE, 1.0f)

    fun setFontScale(ctx: Context, scale: Float) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_FONT_SCALE, scale).apply()
    }

    private const val TAG_BASE_SIZE = "theme_base_size"

    fun apply(activity: AppCompatActivity) {
        val index = getThemeIndex(activity)
        val scale = getFontScale(activity)
        val preset = presets.getOrElse(index) { presets[0] }

        val root = activity.window.decorView.findViewById<View>(android.R.id.content)
        root?.setBackgroundColor(preset.bgColor)

        applyToView(root, preset.textColor, scale)
    }

    private fun applyToView(view: View?, textColor: Int, scale: Float) {
        if (view == null) return
        if (view is TextView) {
            // Use tag to store base size once, to avoid compounding on repeated apply
            val tag = view.getTag(R.id.tag_base_text_size)
            val baseSize: Float = if (tag is Float) {
                tag
            } else {
                val s = view.textSize // px
                view.setTag(R.id.tag_base_text_size, s)
                s
            }
            // Apply scale relative to base size (in pixels), then set in px
            view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, baseSize * scale)
            view.setTextColor(textColor)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyToView(view.getChildAt(i), textColor, scale)
            }
        }
    }
}
