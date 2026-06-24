package com.jitji.todo

import android.os.Bundle
import android.view.MenuItem
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatActivity
import com.jitji.todo.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val fontScales = listOf(0.85f, 1.0f, 1.15f, 1.3f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarSettings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings)

        // Restore current selections
        val currentTheme = ThemeManager.getThemeIndex(this)
        val currentScale = ThemeManager.getFontScale(this)

        // Select the matching theme radio
        val themeRadios = listOf(
            binding.radioTheme0,
            binding.radioTheme1,
            binding.radioTheme2,
            binding.radioTheme3
        )
        themeRadios.getOrNull(currentTheme)?.isChecked = true

        // Select the matching font radio
        val fontRadios = listOf(
            binding.radioFontSmall,
            binding.radioFontNormal,
            binding.radioFontLarge,
            binding.radioFontXLarge
        )
        val fontIdx = fontScales.indexOfFirst { Math.abs(it - currentScale) < 0.01f }
        fontRadios.getOrNull(if (fontIdx < 0) 1 else fontIdx)?.isChecked = true

        // Theme change
        binding.radioGroupTheme.setOnCheckedChangeListener { _, checkedId ->
            val idx = when (checkedId) {
                R.id.radioTheme0 -> 0
                R.id.radioTheme1 -> 1
                R.id.radioTheme2 -> 2
                R.id.radioTheme3 -> 3
                else -> 0
            }
            ThemeManager.setThemeIndex(this, idx)
            recreate()
        }

        // Font size change
        binding.radioGroupFont.setOnCheckedChangeListener { _, checkedId ->
            val scale = when (checkedId) {
                R.id.radioFontSmall -> 0.85f
                R.id.radioFontNormal -> 1.0f
                R.id.radioFontLarge -> 1.15f
                R.id.radioFontXLarge -> 1.3f
                else -> 1.0f
            }
            ThemeManager.setFontScale(this, scale)
            recreate()
        }

        // 글씨 색상 복원
        val tcRadios = listOf(
            binding.radioTc0, binding.radioTc1, binding.radioTc2,
            binding.radioTc3, binding.radioTc4, binding.radioTc5
        )
        tcRadios.getOrNull(ThemeManager.getTextColorIndex(this))?.isChecked = true

        // 글씨 색상 변경
        binding.radioGroupTextColor.setOnCheckedChangeListener { _, checkedId ->
            val idx = when (checkedId) {
                R.id.radioTc0 -> 0
                R.id.radioTc1 -> 1
                R.id.radioTc2 -> 2
                R.id.radioTc3 -> 3
                R.id.radioTc4 -> 4
                R.id.radioTc5 -> 5
                else -> 0
            }
            ThemeManager.setTextColorIndex(this, idx)
            recreate()
        }

        ThemeManager.apply(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
