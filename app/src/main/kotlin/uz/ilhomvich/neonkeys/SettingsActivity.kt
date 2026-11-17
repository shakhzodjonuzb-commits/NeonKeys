package uz.ilhomvich.neonkeys

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private var anim: ValueAnimator? = null
    private val cyan = 0xFF00F0FF.toInt()
    private val pink = 0xFFFF2BD6.toInt()
    private val lime = 0xFFB6FF00.toInt()
    private var dens = 1f
    private fun dp(v: Int) = (v * dens).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        dens = resources.displayMetrics.density

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(40))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF05050A.toInt())
            addView(col)
        }
        @Suppress("DEPRECATION")
        scroll.setOnApplyWindowInsetsListener { v, ins ->
            v.setPadding(0, ins.systemWindowInsetTop, 0, ins.systemWindowInsetBottom)
            ins
        }

        // Sarlavha (rainbow animatsiyali)
        val title = TextView(this).apply {
            text = "NEON KEYS"
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.15f
            setTextColor(Color.WHITE)
        }
        col.addView(title)
        col.addView(label("Dark  •  Gamer  •  Neon Rainbow klaviatura", 0xFF9A9AB5.toInt(), 14f))
        title.post {
            val w = title.paint.measureText(title.text.toString()).coerceAtLeast(1f)
            val cols = IntArray(9) { i -> Color.HSVToColor(floatArrayOf((i * 45f) % 360f, 0.9f, 1f)) }
            val sh = LinearGradient(0f, 0f, w, 0f, cols, null, Shader.TileMode.REPEAT)
            title.paint.shader = sh
            val m = Matrix()
            anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 4000
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { m.setTranslate(it.animatedFraction * w, 0f); sh.setLocalMatrix(m); title.invalidate() }
                start()
            }
        }

        col.addView(btn("1. Klaviaturani yoqish", cyan) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        col.addView(btn("2. Klaviaturani tanlash", pink) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        val test = EditText(this).apply {
            hint = "Shu yerga yozib sinab koʻring…"
            setHintTextColor(0xFF6A6A85.toInt())
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 2
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(0xFF0C0C15.toInt()); setStroke(dp(2), lime); cornerRadius = dp(14).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(16) }
        }
        col.addView(test)

        col.addView(section("Koʻrinish"))
        col.addView(sw("Neon rainbow animatsiya", { prefs.animate }) { prefs.animate = it })
        col.addView(seek("Animatsiya tezligi", { prefs.speed }) { prefs.speed = it })
        col.addView(seek("Neon yorqinligi (glow)", { prefs.glow }) { prefs.glow = it })
        col.addView(seek("Tugma balandligi", { prefs.keyHeight }) { prefs.keyHeight = it })
        col.addView(sw("Raqamlar qatori", { prefs.numberRow }) { prefs.numberRow = it })

        col.addView(section("Yozish"))
        col.addView(sw("Soʻz takliflari", { prefs.suggestions }) { prefs.suggestions = it })
        col.addView(sw("Avtomatik bosh harf", { prefs.autoCap }) { prefs.autoCap = it })
        col.addView(sw("Tebranish (haptic)", { prefs.haptic }) { prefs.haptic = it })
        col.addView(sw("Tugma ovozi", { prefs.sound }) { prefs.sound = it })
        col.addView(btn("Oʻrganilgan soʻzlarni tozalash", pink) {
            Suggest.get(prefs).clear()
            Toast.makeText(this, "Tozalandi", Toast.LENGTH_SHORT).show()
        })

        col.addView(label("Tillar: English • Oʻzbek • Русский\nGlobus tugmasini bosing — til almashadi; uzoq bosing — klaviatura tanlash.", 0xFF9A9AB5.toInt(), 13f).apply {
            setPadding(0, dp(18), 0, 0)
        })
        setContentView(scroll)
    }

    override fun onDestroy() {
        anim?.cancel()
        super.onDestroy()
    }

    private fun label(t: String, c: Int, size: Float) = TextView(this).apply {
        text = t; setTextColor(c); textSize = size
    }

    private fun section(t: String) = label(t.uppercase(), cyan, 13f).apply {
        letterSpacing = 0.2f
        setPadding(0, dp(26), 0, dp(4))
    }

    private fun btn(t: String, color: Int, f: () -> Unit) = Button(this).apply {
        text = t
        isAllCaps = false
        setTextColor(Color.WHITE)
        textSize = 16f
        background = GradientDrawable().apply {
            setColor(0xFF0C0C15.toInt()); setStroke(dp(2), color); cornerRadius = dp(14).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(12) }
        setOnClickListener { f() }
    }

    private fun sw(t: String, get: () -> Boolean, set: (Boolean) -> Unit) = Switch(this).apply {
        text = t
        setTextColor(Color.WHITE)
        textSize = 16f
        isChecked = get()
        setPadding(0, dp(12), 0, dp(12))
        setOnCheckedChangeListener { _, v -> set(v) }
    }

    private fun seek(t: String, get: () -> Int, set: (Int) -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(10), 0, dp(4))
        addView(label(t, 0xFFC8C8DC.toInt(), 14f))
        addView(SeekBar(context).apply {
            max = 100
            progress = get()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) set(p) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
    }
}
