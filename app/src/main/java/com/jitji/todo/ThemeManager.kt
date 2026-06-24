package com.jitji.todo

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

object ThemeManager {

    private const val PREFS_NAME = "jitji_theme"
    private const val KEY_THEME_INDEX = "theme_index"
    private const val KEY_FONT_SCALE = "font_scale"
    private const val KEY_TEXT_COLOR = "text_color_index"

    data class Preset(val bgColor: Int, val textColor: Int)

    // index 0 = 기본(업데이트 전 원래 다크그레이 스타일).
    // apply()는 index 0일 때 배경/글씨를 건드리지 않아 원래 스타일을 그대로 유지한다.
    val presets = listOf(
        Preset(Color.parseColor("#3A3A3C"), Color.parseColor("#F5F5F7")), // 기본(원래)
        Preset(Color.WHITE, Color.parseColor("#333333")),                 // 화이트
        Preset(Color.parseColor("#F4ECD8"), Color.parseColor("#4A3B2A")), // 세피아
        Preset(Color.parseColor("#E6F4F1"), Color.parseColor("#1E3A34"))  // 민트
    )

    // index 0 = 테마 기본(글씨색 미지정). 나머지는 지정 색.
    val textColors = listOf(
        0,
        Color.parseColor("#F5F5F7"), // 흰색
        Color.parseColor("#1C1C1E"), // 검정
        Color.parseColor("#9E9E9E"), // 회색
        Color.parseColor("#4A90D9"), // 파랑
        Color.parseColor("#E2574C")  // 빨강
    )

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getThemeIndex(ctx: Context): Int = prefs(ctx).getInt(KEY_THEME_INDEX, 0)
    fun setThemeIndex(ctx: Context, i: Int) { prefs(ctx).edit().putInt(KEY_THEME_INDEX, i).apply() }

    fun getFontScale(ctx: Context): Float = prefs(ctx).getFloat(KEY_FONT_SCALE, 1.0f)
    fun setFontScale(ctx: Context, s: Float) { prefs(ctx).edit().putFloat(KEY_FONT_SCALE, s).apply() }

    fun getTextColorIndex(ctx: Context): Int = prefs(ctx).getInt(KEY_TEXT_COLOR, 0)
    fun setTextColorIndex(ctx: Context, i: Int) { prefs(ctx).edit().putInt(KEY_TEXT_COLOR, i).apply() }

    fun apply(activity: AppCompatActivity) {
        val themeIdx = getThemeIndex(activity)
        val textIdx = getTextColorIndex(activity)
        val scale = getFontScale(activity)

        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val root: View = if (content.childCount > 0) content.getChildAt(0) else content

        // 배경: 기본(0)이면 원래 배경 유지, 그 외 테마만 덮어쓴다.
        if (themeIdx != 0) {
            val bg = presets.getOrElse(themeIdx) { presets[0] }.bgColor
            root.setBackgroundColor(bg)
            content.setBackgroundColor(bg)
        }

        // 글씨색: 명시 선택(>0) 우선 → 비기본 테마 글씨색 → 기본+미지정은 null(원래 유지)
        val textColor: Int? = when {
            textIdx > 0 -> textColors[textIdx]
            themeIdx != 0 -> presets.getOrElse(themeIdx) { presets[0] }.textColor
            else -> null
        }

        applyToView(root, textColor, scale)
    }

    private fun applyToView(view: View?, textColor: Int?, scale: Float) {
        if (view == null) return
        if (view is TextView) {
            val tag = view.getTag(R.id.tag_base_text_size)
            val baseSize: Float = if (tag is Float) tag else {
                val s = view.textSize
                view.setTag(R.id.tag_base_text_size, s)
                s
            }
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseSize * scale)
            // 배경이 있는 위젯(카테고리 칩·버튼 등)은 대비가 깨질 수 있어 글씨색 변경에서 제외
            if (textColor != null && view.background == null) {
                view.setTextColor(textColor)
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) applyToView(view.getChildAt(i), textColor, scale)
        }
    }
}
