package zone.vgs.khoa

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/**
 * Màn phụ huynh trên tivi: đặt PIN, bật hoặc tắt mode trẻ em, chọn app và mức toán.
 * Mở bằng intent `zone.vgs.khoa.PHU_HUYNH`. Back chỉ đóng màn, không đổi `treMode`.
 */
class ManPhuHuynh : Activity() {
    private val tay = Handler(Looper.getMainLooper())
    private val vi get() = Locale.getDefault().language == "vi"
    private lateinit var kho: Kho
    private lateinit var c: LuatTre.CheDo
    private lateinit var tvTieu: TextView
    private lateinit var tvCham: TextView
    private lateinit var tvBao: TextView
    private lateinit var tvTrang: TextView
    private lateinit var tvPhut: TextView
    private val oPhim = mutableListOf<TextView>()
    private val chip = linkedMapOf<Int, TextView>()
    private val hangApp = mutableListOf<TextView>()
    private val goiApp = mutableListOf<String>()
    private val chon = linkedSetOf<String>()
    private var nhapPin = false
    private var laCua = false
    private var pinLan1: String? = null
    private var pin = ""
    private var phut = 30
    private var muc = 10

    private val demChan = object : Runnable {
        override fun run() {
            if (!nhapPin || !laCua) return
            val con = kho.chanDen - System.currentTimeMillis()
            if (con <= 0) {
                tvBao.text = ""
                return
            }
            veChan()
            tay.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        kho = Kho(this)
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { finish() }
        }
        val c0 = kho.cheDo()
        when {
            !c0.coPin -> hienDatPin()
            LuatTre.locDuoc(c0) -> hienCuaPin()
            else -> hienCai()
        }
    }

    override fun onDestroy() {
        tay.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    @Deprecated("Back trên tivi gọi finish, không đổi treMode.")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!nhapPin || event.repeatCount != 0) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                goSo(('0' + (keyCode - KeyEvent.KEYCODE_0)).toString())
                return true
            }
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> {
                goSo(('0' + (keyCode - KeyEvent.KEYCODE_NUMPAD_0)).toString())
                return true
            }
            KeyEvent.KEYCODE_DEL -> {
                xoa()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun hienDatPin() {
        laCua = false
        pinLan1 = null
        hienBanPhim(s(R.string.ph_dat_pin, "Set a 4-digit PIN"))
    }

    private fun hienCuaPin() {
        laCua = true
        pinLan1 = null
        hienBanPhim(s(R.string.ph_nhap_cua, "Enter the PIN to open settings."))
    }

    private fun hienBanPhim(tieu: String) {
        nhapPin = true
        pin = ""
        tay.removeCallbacksAndMessages(null)
        val cot = cot()
        tvTieu = chu(tieu, 28f, dam = true).apply { setPadding(0, dp(8), 0, dp(6)) }
        cot.addView(tvTieu)
        tvCham = chu("", 34f).apply {
            letterSpacing = 0.4f
            setPadding(0, dp(18), 0, dp(10))
        }
        cot.addView(tvCham)
        val luoi = GridLayout(this).apply { columnCount = 3 }
        oPhim.clear()
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓").forEach { k ->
            val o = phim(k)
            oPhim += o
            luoi.addView(o)
        }
        oPhim.forEachIndexed { i, o ->
            if (i % 3 > 0) o.nextFocusLeftId = oPhim[i - 1].id
            if (i % 3 < 2) o.nextFocusRightId = oPhim[i + 1].id
            if (i >= 3) o.nextFocusUpId = oPhim[i - 3].id
            if (i + 3 < oPhim.size) o.nextFocusDownId = oPhim[i + 3].id
        }
        cot.addView(luoi, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        tvBao = chu("", 16f, Color.parseColor("#FF8FA3")).apply { setPadding(0, dp(12), 0, 0) }
        cot.addView(tvBao)
        vePin()
        gan(cot, oPhim.firstOrNull())
        if (laCua && System.currentTimeMillis() < kho.chanDen) {
            veChan()
            tay.postDelayed(demChan, 1000)
        }
    }

    private fun phim(nhan: String) = chu(nhan, 24f, dam = true).apply {
        id = View.generateViewId()
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        defaultFocusHighlightEnabled = false
        layoutParams = GridLayout.LayoutParams().apply {
            width = dp(72)
            height = dp(50)
            setMargins(dp(5), dp(5), dp(5), dp(5))
        }
        background = nen(false)
        setOnFocusChangeListener { v, co -> v.background = nen(co) }
        setOnClickListener {
            when (nhan) {
                "⌫" -> xoa()
                "✓" -> xacNhan()
                else -> goSo(nhan)
            }
        }
    }

    private fun goSo(so: String) {
        if (!nhapPin) return
        if (laCua && System.currentTimeMillis() < kho.chanDen) {
            veChan()
            return
        }
        if (pin.length >= 4) return
        pin += so
        tvBao.text = ""
        vePin()
        if (pin.length == 4) tay.postDelayed({ xacNhan() }, 150)
    }

    private fun xoa() {
        if (!nhapPin || pin.isEmpty()) return
        pin = pin.dropLast(1)
        vePin()
    }

    private fun vePin() {
        tvCham.text = (0 until 4).joinToString(" ") { if (it < pin.length) "●" else "○" }
    }

    private fun veChan() {
        val con = kho.chanDen - System.currentTimeMillis()
        if (con <= 0) return
        val giay = ((con + 999) / 1000).toInt()
        tvBao.setTextColor(Color.parseColor("#FF8FA3"))
        tvBao.text = if (vi) getString(R.string.ph_chan, giay) else "Too many tries. Try again in $giay s."
    }

    private fun xacNhan() {
        if (!nhapPin || pin.length != 4) return
        val da = pin
        if (laCua) cua(da) else dat(da)
    }

    private fun dat(da: String) {
        if (pinLan1 == null) {
            pinLan1 = da
            pin = ""
            vePin()
            tvTieu.text = s(R.string.ph_nhap_lai, "Enter the PIN again")
            tvBao.text = ""
            return
        }
        if (da != pinLan1) {
            pinLan1 = null
            pin = ""
            vePin()
            tvTieu.text = s(R.string.ph_dat_pin, "Set a 4-digit PIN")
            bao(s(R.string.ph_khong_khop, "The two PINs don't match."), true)
            return
        }
        ghiPinMoi(da)
        hienCai()
    }

    /** Ghi muối và PIN đã băm vào JSON hiện có. Không bật `treBat`. */
    private fun ghiPinMoi(pinMoi: String) {
        val muoi = UUID.randomUUID().toString().replace("-", "").take(16)
        val o = runCatching { JSONObject(kho.json ?: "{}") }.getOrElse { JSONObject() }
        o.put("muoi", muoi).put("pinBam", Luat.bam(muoi, pinMoi))
        kho.json = o.toString()
        KhoaDichVu.dangChay?.capNhatSau()
    }

    private fun cua(da: String) {
        if (System.currentTimeMillis() < kho.chanDen) {
            veChan()
            return
        }
        val cau = kho.cauHinh
        if (cau != null && Luat.dungPin(cau, da)) {
            kho.sai = 0
            val moi = LuatTre.sangPhuHuynh(kho.cheDo())
            kho.json = LuatTre.ghiTre(kho.json, moi)
            KhoaDichVu.dangChay?.capNhatSau()
            baoDoi()
            hienCai()
            return
        }
        pin = ""
        vePin()
        kho.sai = kho.sai + 1
        if (kho.sai >= 5) {
            kho.sai = 0
            kho.chanDen = System.currentTimeMillis() + 60_000L
            veChan()
            tay.removeCallbacks(demChan)
            tay.postDelayed(demChan, 1000)
        } else {
            val con = 5 - kho.sai
            tvBao.setTextColor(Color.parseColor("#FF8FA3"))
            tvBao.text = if (vi) getString(R.string.ph_sai_pin, con) else "Wrong PIN, $con tries left."
        }
    }

    private fun hienCai() {
        nhapPin = false
        tay.removeCallbacksAndMessages(null)
        c = kho.cheDo()
        phut = c.phutToan
        muc = c.mucToan
        val ds = lietKe()
        goiApp.clear()
        goiApp.addAll(ds.map { it.goi })
        chon.clear()
        chon.addAll(c.app)
        for (g in goiApp) if (g in LuatTre.YOUTUBE) chon.add(g)
        val cot = cot()
        cot.addView(chu(s(R.string.ph_tieu_de, "Kids settings"), 28f, dam = true).apply { setPadding(0, dp(8), 0, dp(4)) })
        tvTrang = chu("", 18f, Color.parseColor("#C9C3FF"))
        cot.addView(tvTrang)
        tvBao = chu("", 16f, Color.parseColor("#FF8FA3")).apply { setPadding(0, dp(8), 0, dp(8)) }
        cot.addView(tvBao)
        val bat = nut(s(R.string.ph_bat, "Turn on")) { bamBat() }
        val tat = nut(s(R.string.ph_tat, "Turn off")) { bamTat() }
        val ve = nut(s(R.string.ph_ve_tre, "Back to child mode")) { bamVe() }
        cot.addView(bat, day())
        cot.addView(tat, day())
        cot.addView(ve, day())
        cot.addView(chu(s(R.string.ph_muc, "Math level"), 16f, Color.parseColor("#C9C3FF")).apply {
            setPadding(0, dp(16), 0, dp(6))
        })
        val hangMuc = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chip.clear()
        listOf(10, 20, 100).forEach { m ->
            val o = nut(m.toString()) { datMuc(m) }
            chip[m] = o
            hangMuc.addView(o, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            })
        }
        val dsChip = chip.values.toList()
        dsChip.forEachIndexed { i, o ->
            o.setOnFocusChangeListener { _, _ -> veMuc() }
            if (i > 0) o.nextFocusLeftId = dsChip[i - 1].id
            if (i < dsChip.lastIndex) o.nextFocusRightId = dsChip[i + 1].id
        }
        cot.addView(hangMuc, day())
        val tru = nut("−") { doiPhut(-5) }
        val cong = nut("+") { doiPhut(5) }
        tru.nextFocusRightId = cong.id
        cong.nextFocusLeftId = tru.id
        tvPhut = chu("", 22f, dam = true)
        val hangPhut = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(4))
        }
        hangPhut.addView(tru, LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT))
        hangPhut.addView(tvPhut, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        hangPhut.addView(cong, LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT))
        cot.addView(hangPhut, day())
        cot.addView(chu(s(R.string.ph_app, "Allowed apps"), 16f, Color.parseColor("#C9C3FF")).apply {
            setPadding(0, dp(12), 0, dp(4))
        })
        cot.addView(chu(s(R.string.ph_yt, "YouTube stays selected."), 13f, Color.parseColor("#8A84B8")).apply {
            setPadding(0, 0, 0, dp(6))
        })
        hangApp.clear()
        ds.forEach { u ->
            val o = hangUng(u.ten, u.goi)
            hangApp += o
            cot.addView(o, day())
        }
        hangApp.forEachIndexed { i, o ->
            if (i > 0) o.nextFocusUpId = hangApp[i - 1].id
            if (i < hangApp.lastIndex) o.nextFocusDownId = hangApp[i + 1].id
        }
        val luu = nut(s(R.string.ph_luu, "Save")) { bamLuu() }
        cot.addView(luu, day())
        bat.nextFocusDownId = tat.id
        tat.nextFocusUpId = bat.id
        tat.nextFocusDownId = ve.id
        ve.nextFocusUpId = tat.id
        val xuongMuc = dsChip.first().id
        ve.nextFocusDownId = xuongMuc
        dsChip.forEach {
            it.nextFocusUpId = ve.id
            it.nextFocusDownId = tru.id
        }
        val dauDuoi = hangApp.firstOrNull()?.id ?: luu.id
        tru.nextFocusUpId = xuongMuc
        cong.nextFocusUpId = xuongMuc
        tru.nextFocusDownId = dauDuoi
        cong.nextFocusDownId = dauDuoi
        if (hangApp.isEmpty()) luu.nextFocusUpId = tru.id
        else {
            hangApp.first().nextFocusUpId = tru.id
            hangApp.last().nextFocusDownId = luu.id
            luu.nextFocusUpId = hangApp.last().id
        }
        vePhut()
        veMuc()
        veApp()
        veTrangThai()
        gan(cot, bat)
    }

    private fun bamBat() {
        val moi = LuatTre.bat(banNhap()) ?: run {
            bao(s(R.string.ph_chua_pin, "No PIN yet."), true)
            return
        }
        apDung(moi)
    }

    private fun bamTat() {
        val moi = LuatTre.tat(banNhap())
        kho.dongHo = LuatTre.dungBai(kho.dongHo)
        apDung(moi)
    }

    private fun bamVe() {
        apDung(LuatTre.veTre(banNhap()))
    }

    private fun bamLuu() {
        apDung(banNhap())
        bao(s(R.string.ph_da_luu, "Saved."), false)
    }

    private fun banNhap() = c.copy(phutToan = phut, mucToan = muc, app = dsApp())

    /** Ghi mode, phút, mức và app. Đổi phút hoặc mức không tự đóng bài đang hiện. */
    private fun apDung(moi: LuatTre.CheDo) {
        val h = LuatTre.hienThi(moi, kho.dangCai())
        kho.json = LuatTre.ghiTre(kho.json, h)
        c = kho.cheDo()
        phut = c.phutToan
        muc = c.mucToan
        chon.clear()
        chon.addAll(c.app)
        for (g in goiApp) if (g in LuatTre.YOUTUBE) chon.add(g)
        vePhut()
        veMuc()
        veApp()
        veTrangThai()
        KhoaDichVu.dangChay?.capNhatSau()
        baoDoi()
    }

    private fun dsApp(): List<String> = goiApp.filter { it in chon || it in LuatTre.YOUTUBE }

    private fun datMuc(m: Int) {
        muc = m
        veMuc()
    }

    private fun doiPhut(buoc: Int) {
        phut = (phut + buoc).coerceIn(5, 120)
        vePhut()
    }

    private fun doiApp(goi: String) {
        if (goi in LuatTre.YOUTUBE) {
            bao(s(R.string.ph_yt, "YouTube stays selected."), false)
            return
        }
        if (!chon.add(goi)) chon.remove(goi)
        veApp()
    }

    private fun vePhut() {
        tvPhut.text = if (vi) getString(R.string.ph_phut, phut) else "$phut min"
    }

    private fun veMuc() {
        chip.forEach { (m, o) -> o.background = nen(o.hasFocus(), m == muc) }
    }

    private fun veApp() {
        hangApp.forEachIndexed { i, o ->
            val g = goiApp[i]
            val bat = g in chon || g in LuatTre.YOUTUBE
            val ten = o.tag as? String ?: g
            o.text = (if (bat) "☑  " else "☐  ") + ten
        }
    }

    private fun veTrangThai() {
        tvTrang.text = when {
            !c.treBat -> s(R.string.ph_tat_trang, "Off.")
            c.treMode == "phuHuynh" -> s(R.string.ph_phu_trang, "On, parent mode.")
            else -> s(R.string.ph_tre_trang, "On, child mode.")
        }
    }

    private fun baoDoi() {
        val i = Intent(LuatTre.HANH_DOI).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        sendBroadcast(Intent(i).setPackage("com.gathin.tvhome"))
        sendBroadcast(Intent(i).setPackage("com.ts.launcher"))
    }

    private fun bao(s: String, loi: Boolean) {
        tvBao.setTextColor(Color.parseColor(if (loi) "#FF8FA3" else "#C9C3FF"))
        tvBao.text = s
    }

    private data class Ung(val goi: String, val ten: String)

    private fun lietKe(): List<Ung> {
        val thay = linkedSetOf<String>()
        val ra = mutableListOf<Ung>()
        fun them(nhom: List<ResolveInfo>) {
            val ds = mutableListOf<Ung>()
            for (r in nhom) {
                val goi = r.activityInfo?.packageName ?: continue
                if (!thay.add(goi)) continue
                val ten = r.loadLabel(packageManager)?.toString()?.ifBlank { goi } ?: goi
                ds += Ung(goi, ten)
            }
            ds.sortBy { it.ten.lowercase(Locale.getDefault()) }
            ra += ds
        }
        them(hoi(Intent.CATEGORY_LEANBACK_LAUNCHER))
        them(hoi(Intent.CATEGORY_LAUNCHER))
        return ra
    }

    private fun hoi(category: String): List<ResolveInfo> {
        val y = Intent(Intent.ACTION_MAIN).addCategory(category)
        return if (Build.VERSION.SDK_INT >= 33)
            packageManager.queryIntentActivities(y, PackageManager.ResolveInfoFlags.of(0))
        else
            @Suppress("DEPRECATION") packageManager.queryIntentActivities(y, 0)
    }

    private fun hangUng(ten: String, goi: String) = chu("", 18f).apply {
        id = View.generateViewId()
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        defaultFocusHighlightEnabled = false
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        tag = ten
        setPadding(dp(16), dp(10), dp(16), dp(10))
        background = nen(false)
        setOnFocusChangeListener { v, co -> v.background = nen(co) }
        setOnClickListener { doiApp(goi) }
    }

    private fun nut(s: String, click: () -> Unit) = chu(s, 20f, dam = true).apply {
        id = View.generateViewId()
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        defaultFocusHighlightEnabled = false
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = nen(false)
        setOnFocusChangeListener { v, co -> v.background = nen(co) }
        setOnClickListener { click() }
    }

    private fun cot() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8), dp(28), dp(8), dp(28))
    }

    private fun day() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        setMargins(0, dp(5), 0, dp(5))
    }

    private fun gan(cot: LinearLayout, focus: View?) {
        val rong = minOf(dp(680), resources.displayMetrics.widthPixels - dp(48)).coerceAtLeast(dp(280))
        val hang = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(cot, LinearLayout.LayoutParams(rong, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val scroll = ScrollView(this).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            isFillViewport = true
            addView(hang, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        val goc = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#FF0A0718"))
            addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        setContentView(goc)
        focus?.post { focus.requestFocus() }
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun chu(s: String, sp: Float, mau: Int = Color.WHITE, dam: Boolean = false) = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(mau)
        gravity = Gravity.CENTER
        if (dam) typeface = Typeface.DEFAULT_BOLD
    }

    private fun nen(focus: Boolean, chon: Boolean = false) = GradientDrawable().apply {
        cornerRadius = dp(16).toFloat()
        setColor(if (focus) Color.parseColor("#8B5CF6") else Color.parseColor("#221B45"))
        if (chon && !focus) setStroke(dp(3), Color.parseColor("#C4B5FD"))
    }

    private fun s(id: Int, en: String) = if (vi) getString(id) else en
}
