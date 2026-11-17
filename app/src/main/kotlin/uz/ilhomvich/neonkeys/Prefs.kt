package uz.ilhomvich.neonkeys

import android.content.Context
import android.content.SharedPreferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private class IntP(private val sp: SharedPreferences, private val k: String, private val d: Int) :
    ReadWriteProperty<Any?, Int> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): Int = sp.getInt(k, d)
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
        sp.edit().putInt(k, value).apply()
    }
}

private class BoolP(private val sp: SharedPreferences, private val k: String, private val d: Boolean) :
    ReadWriteProperty<Any?, Boolean> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = sp.getBoolean(k, d)
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
        sp.edit().putBoolean(k, value).apply()
    }
}

private class StrP(private val sp: SharedPreferences, private val k: String, private val d: String) :
    ReadWriteProperty<Any?, String> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): String = sp.getString(k, d) ?: d
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
        sp.edit().putString(k, value).apply()
    }
}

class Prefs(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("neonkeys", Context.MODE_PRIVATE)
    var speed by IntP(sp, "speed", 55)
    var glow by IntP(sp, "glow", 70)
    var keyHeight by IntP(sp, "kh", 50)
    var lang by IntP(sp, "lang", 0)
    var animate by BoolP(sp, "anim", true)
    var haptic by BoolP(sp, "haptic", true)
    var sound by BoolP(sp, "sound", false)
    var numberRow by BoolP(sp, "numrow", true)
    var autoCap by BoolP(sp, "autocap", true)
    var suggestions by BoolP(sp, "sugg", true)
    var learned by StrP(sp, "learned", "")
}
