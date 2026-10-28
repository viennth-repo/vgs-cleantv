package zone.vgs.khoa

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar
import java.util.Locale

/**
 * Dịch vụ khoá (23/10/2026): trong khung giờ phụ huynh đặt, phủ một lớp kín màn hình (lớp trợ năng nằm trên mọi
 * app, kể cả HDMI) và nuốt mọi phím remote — chỉ còn số / mũi tên / OK để nhập PIN. Đúng PIN thì mở tạm
 * moTamPhut (mặc định 60). Sai 5 lần thì chờ 60 giây. Đang khoá thì dừng tiếng (giành audio focus + tắt tiếng nhạc).
 * Mode trẻ em: YouTube ở trước đủ phút thì phủ bài cộng trừ. Lớp khoá giờ vẫn ưu tiên và nuốt mọi phím.
 */
class KhoaDichVu : AccessibilityService() {
    companion object {
        @Volatile var dangChay: KhoaDichVu? = null
        @Volatile var dangKhoa = false
        private const val NHIP_MS = 15_000L
        private const val SAI_TOI_DA = 5
        private const val CHAN_MS = 60_000L
        private val PHIM = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓")
    }

    private val tay = Handler(Looper.getMainLooper())
    private lateinit var kho: Kho
    private var lop: View? = null
    private lateinit var tvPhu: TextView
    private lateinit var tvCham: TextView
    private lateinit var tvBao: TextView
    private val oPhim = mutableListOf<TextView>()
    private var chon = 4
    private var pin = ""
    private var focus: AudioFocusRequest? = null
    private var mat: String? = null
    private var bai: LuatTre.Bai? = null
    private var lopBai: View? = null
    private var moc = SystemClock.elapsedRealtime()
    private var chonBai = 0
    private var mucBai = 10
    private var hangDap: LinearLayout? = null
    private var tvPhep: TextView? = null
    private val nhanDap = mutableListOf<TextView>()
    private var dapHien = listOf<Int>()
    private val vi get() = Locale.getDefault().language == "vi"
    private fun t(v: String, e: String) = if (vi) v else e

    private val nhip = object : Runnable {
        override fun run() { capNhat(); tay.postDelayed(this, NHIP_MS) }
    }

    override fun onServiceConnected() {
        kho = Kho(this)
        dangChay = this
        CanhGac.hen(this)
        tay.post(nhip)
    }

    override fun onDestroy() {
        tay.removeCallbacksAndMessages(null)
        anKhoa()
        if (lopBai != null) {
            dongBai(phat = false)
            imTieng(false)
        }
        dangChay = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) return
        tay.post { doiMat(pkg) }
    }
    override fun onInterrupt() {}

    private fun laYoutube(pkg: String?) = pkg in LuatTre.YOUTUBE

    private fun doiMat(pkg: String) {
        if (pkg == mat) return
        ghiNhip()
        mat = pkg
        moc = SystemClock.elapsedRealtime()
    }

    fun capNhatSau() = tay.post { capNhat() }

    private fun capNhat() {
        ghiNhip()
        val c = kho.cauHinh
        val bay = Calendar.getInstance()
        val khoa = c != null && Luat.trongGio(c, bay) && System.currentTimeMillis() >= kho.moTamDen
        if (khoa && lop == null) hienKhoa()
        if (!khoa && lop != null) anKhoa()
        if (khoa) {
            val het = Luat.hetKhoaLuc(c!!, bay)?.let { Luat.chuGio(it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)) }
            tvPhu.text = (if (het != null) t("Mở lại lúc $het. ", "Unlocks at $het. ") else "") +
                t("Nhập mã PIN để mở ${c.moTamPhut} phút.", "Enter the PIN to unlock for ${c.moTamPhut} minutes.")
            veChan()
        }
    }

    // ---------- Lớp khoá ----------

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun chu(s: String, sp: Float, mau: Int = Color.WHITE, dam: Boolean = false) = TextView(this).apply {
        text = s; setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); setTextColor(mau); gravity = Gravity.CENTER
        if (dam) typeface = Typeface.DEFAULT_BOLD
    }

    private fun hienKhoa() {
        pin = ""; chon = 4
        val cot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        cot.addView(chu("🔒", 44f))
        cot.addView(chu(t("Tivi đang nghỉ", "The TV is resting"), 34f, dam = true).apply { setPadding(0, dp(8), 0, dp(6)) })
        tvPhu = chu("", 18f, Color.parseColor("#C9C3FF")); cot.addView(tvPhu, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        tvCham = chu("", 34f).apply { letterSpacing = 0.4f; setPadding(0, dp(18), 0, dp(10)) }; cot.addView(tvCham)
        val luoi = GridLayout(this).apply { columnCount = 3 }
        oPhim.clear()
        PHIM.forEach { k ->
            val o = chu(k, 24f, dam = true).apply {
                layoutParams = GridLayout.LayoutParams().apply { width = dp(72); height = dp(50); setMargins(dp(5), dp(5), dp(5), dp(5)) }
            }
            oPhim += o; luoi.addView(o)
        }
        cot.addView(luoi, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL })
        tvBao = chu("", 16f, Color.parseColor("#FF8FA3")).apply { setPadding(0, dp(12), 0, 0) }; cot.addView(tvBao)
        cot.addView(chu(t("Phụ huynh đổi giờ khoá trong app CleanTV.", "Parents change the schedule in the CleanTV app."), 13f, Color.parseColor("#8A84B8")).apply { setPadding(0, dp(18), 0, 0) })
        val nen = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#FF0A0718"))
            addView(cot, FrameLayout.LayoutParams(dp(640), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT)
        runCatching { getSystemService(WindowManager::class.java).addView(nen, lp) }.onSuccess {
            lop = nen; dangKhoa = true
            performGlobalAction(GLOBAL_ACTION_HOME) // app đang chạy lui về nền
            imTieng(true)
            vePin(); veChon()
        }
    }

    private fun anKhoa() {
        lop?.let { v -> runCatching { getSystemService(WindowManager::class.java).removeView(v) } }
        if (lop != null) imTieng(false)
        lop = null; dangKhoa = false
        if (lopBai != null) imTieng(true)
    }

    private fun imTieng(bat: Boolean) {
        val am = getSystemService(AudioManager::class.java) ?: return
        runCatching {
            if (bat) {
                val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()).build()
                am.requestAudioFocus(r); focus = r
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            } else {
                focus?.let { am.abandonAudioFocusRequest(it) }; focus = null
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            }
        }
    }

    private fun vePin() { tvCham.text = (0 until 4).joinToString(" ") { if (it < pin.length) "●" else "○" } }

    private fun veChon() = oPhim.forEachIndexed { i, o ->
        o.background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(if (i == chon) Color.parseColor("#8B5CF6") else Color.parseColor("#221B45"))
        }
    }

    private fun veChan() {
        val con = kho.chanDen - System.currentTimeMillis()
        if (con > 0) tvBao.text = t("Sai nhiều lần. Thử lại sau ${(con + 999) / 1000} giây.", "Too many tries. Try again in ${(con + 999) / 1000} s.")
    }

    // ---------- Bài toán ----------

    private fun ghiNhip() {
        if (!::kho.isInitialized) return
        val nay = SystemClock.elapsedRealtime()
        val them = ((nay - moc) / 1000).coerceIn(0L, 20L)
        moc = nay
        val c = kho.cheDo()
        if (!LuatTre.locDuoc(c)) {
            if (lopBai != null) dongBai(phat = true)
            return
        }
        var d = LuatTre.cong(kho.dongHo, laYoutube(mat), them)
        if (d.dangChoBai && laYoutube(mat) && lopBai == null && lop == null) hienBai(c.mucToan)
        else if (LuatTre.denLuc(d, c.phutToan) && laYoutube(mat) && lop == null) {
            d = LuatTre.moBai(d)
            kho.dongHo = d
            dungPhat()
            hienBai(c.mucToan)
            return
        }
        kho.dongHo = d
    }

    private fun guiMedia(ma: Int) {
        val am = getSystemService(AudioManager::class.java) ?: return
        val t = SystemClock.uptimeMillis()
        runCatching {
            am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, ma, 0))
            am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, ma, 0))
        }
    }

    private fun dungPhat() = guiMedia(KeyEvent.KEYCODE_MEDIA_PAUSE)

    private fun phatLai() {
        guiMedia(KeyEvent.KEYCODE_MEDIA_PLAY)
        imTieng(false)
        if (lop != null) imTieng(true)
    }

    private fun hienBai(muc: Int) {
        if (lopBai != null || lop != null) return
        mucBai = muc
        chonBai = 0
        val moi = LuatTre.sinh(muc, System.nanoTime(), bai)
        bai = moi
        val cot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val phep = chu("${moi.a} ${moi.op} ${moi.b}", 48f, dam = true).apply { setPadding(0, dp(8), 0, dp(18)) }
        tvPhep = phep
        cot.addView(phep)
        val hang = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        hangDap = hang
        ganDap(hang, moi)
        cot.addView(hang)
        val nen = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#FF0A0718"))
            addView(cot, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT)
        runCatching { getSystemService(WindowManager::class.java).addView(nen, lp) }.onSuccess {
            lopBai = nen
            imTieng(true)
        }
    }

    private fun ganDap(hang: LinearLayout, b: LuatTre.Bai) {
        hang.removeAllViews()
        nhanDap.clear()
        val ds = b.dapAn(System.nanoTime())
        dapHien = ds
        ds.forEach { so ->
            val o = chu(so.toString(), 32f, dam = true).apply {
                layoutParams = LinearLayout.LayoutParams(dp(132), dp(68)).apply { setMargins(dp(8), 0, dp(8), 0) }
            }
            nhanDap += o
            hang.addView(o)
        }
        veChonBai()
    }

    private fun veChonBai() = nhanDap.forEachIndexed { i, o ->
        o.background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(if (i == chonBai) Color.parseColor("#8B5CF6") else Color.parseColor("#221B45"))
        }
    }

    private fun dongBai(phat: Boolean) {
        lopBai?.let { v -> runCatching { getSystemService(WindowManager::class.java).removeView(v) } }
        lopBai = null
        hangDap = null
        tvPhep = null
        nhanDap.clear()
        dapHien = emptyList()
        if (phat) {
            kho.dongHo = LuatTre.dungBai(kho.dongHo)
            phatLai()
        }
    }

    private fun saiBai() {
        val hang = hangDap ?: return
        val moi = LuatTre.sinh(mucBai, System.nanoTime(), bai)
        bai = moi
        chonBai = 0
        tvPhep?.text = "${moi.a} ${moi.op} ${moi.b}"
        ganDap(hang, moi)
    }

    // ---------- Phím remote ----------

    override fun onKeyEvent(e: KeyEvent): Boolean {
        if (lop != null) {
            if (e.action == KeyEvent.ACTION_DOWN) tay.post { xuLy(e.keyCode) }
            return true // đang khoá: nuốt mọi phím, kể cả âm lượng, Home, Back
        }
        if (lopBai == null) return false
        if (e.keyCode == KeyEvent.KEYCODE_VOLUME_UP || e.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            e.keyCode == KeyEvent.KEYCODE_VOLUME_MUTE || e.keyCode == KeyEvent.KEYCODE_POWER) return false
        if (e.action == KeyEvent.ACTION_DOWN) tay.post { phimBai(e.keyCode) }
        return true
    }

    private fun phimBai(k: Int) {
        if (lop != null || lopBai == null) return
        when (k) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (chonBai > 0) { chonBai--; veChonBai() }
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (chonBai < 2) { chonBai++; veChonBai() }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> chonDap()
        }
    }

    private fun chonDap() {
        val dung = bai?.dung ?: return
        val chon = dapHien.getOrNull(chonBai) ?: return
        if (chon == dung) dongBai(true) else saiBai()
    }

    private fun xuLy(k: Int) {
        if (lop == null) return
        when (k) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> go(('0' + (k - KeyEvent.KEYCODE_0)).toString())
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> go(('0' + (k - KeyEvent.KEYCODE_NUMPAD_0)).toString())
            KeyEvent.KEYCODE_DPAD_LEFT -> { if (chon % 3 > 0) chon--; veChon() }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { if (chon % 3 < 2) chon++; veChon() }
            KeyEvent.KEYCODE_DPAD_UP -> { if (chon >= 3) chon -= 3; veChon() }
            KeyEvent.KEYCODE_DPAD_DOWN -> { if (chon < 9) chon += 3; veChon() }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> bam(PHIM[chon])
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_BACK -> bam("⌫")
        }
    }

    private fun bam(nhan: String) = when (nhan) {
        "⌫" -> { pin = pin.dropLast(1); vePin() }
        "✓" -> if (pin.length == 4) thu() else Unit
        else -> go(nhan)
    }

    private fun go(so: String) {
        if (System.currentTimeMillis() < kho.chanDen) { veChan(); return }
        if (pin.length >= 4) return
        pin += so; tvBao.text = ""; vePin()
        if (pin.length == 4) tay.postDelayed({ thu() }, 150)
    }

    private fun thu() {
        val c = kho.cauHinh ?: return
        if (Luat.dungPin(c, pin)) {
            kho.sai = 0
            kho.moTamDen = System.currentTimeMillis() + c.moTamPhut * 60_000L
            pin = ""; anKhoa()
            return
        }
        pin = ""; vePin()
        kho.sai = kho.sai + 1
        if (kho.sai >= SAI_TOI_DA) { kho.sai = 0; kho.chanDen = System.currentTimeMillis() + CHAN_MS; veChan() }
        else tvBao.text = t("Sai mã PIN, còn ${SAI_TOI_DA - kho.sai} lần.", "Wrong PIN, ${SAI_TOI_DA - kho.sai} tries left.")
    }
}
