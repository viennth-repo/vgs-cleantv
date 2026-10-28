package zone.vgs.khoa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LuatTreTest {
    private val yt = "com.google.android.youtube.tv"

    private fun trongMuc(b: LuatTre.Bai, max: Int) {
        assertTrue(b.a in 0..max); assertTrue(b.b in 0..max); assertTrue(b.dung in 0..max)
        assertEquals(if (b.op == '+') b.a + b.b else b.a - b.b, b.dung)
        assertTrue(b.dung >= 0)
        assertEquals(2, b.sai.toSet().size)
        assertFalse(b.dung in b.sai)
        b.sai.forEach { assertTrue(it in 0..max) }
        assertEquals(3, b.dapAn(1L).toSet().size)
        assertTrue(b.dung in b.dapAn(1L))
    }

    @Test fun muc10_khongVuot10() {
        repeat(30) { s ->
            val b = LuatTre.sinh(10, s.toLong(), null)
            trongMuc(b, 10)
            if (b.op == '+') assertTrue(b.dung <= 10)
        }
    }

    @Test fun muc20_coBaiQua10_vaTrongPhamVi() {
        val qua = LuatTre.sinhLoai(20, "qua10", 1L)
        trongMuc(qua, 20)
        assertTrue(LuatTre.qua10(qua))
        repeat(20) { s -> trongMuc(LuatTre.sinh(20, s.toLong(), null), 20) }
    }

    @Test fun muc100_coNho_vaTrongPhamVi() {
        val nho = LuatTre.sinhLoai(100, "nho", 1L)
        trongMuc(nho, 100)
        assertTrue(LuatTre.coNho(nho))
        repeat(20) { s -> trongMuc(LuatTre.sinh(100, s.toLong(), null), 100) }
    }

    @Test fun chonSai_raPhepKhac() {
        val cu = LuatTre.sinh(10, 1L, null)
        val moi = LuatTre.sinh(10, 2L, cu)
        assertFalse(moi.a == cu.a && moi.b == cu.b && moi.op == cu.op)
    }

    @Test fun doc_hong_thiTat() {
        val c = LuatTre.doc("{")
        assertFalse(c.treBat)
        assertFalse(LuatTre.locDuoc(c.copy(coPin = true)))
    }

    @Test fun chuaPin_khongBat() {
        val c = LuatTre.doc("""{"treBat":false}""").copy(coPin = false)
        assertNull(LuatTre.bat(c))
        assertFalse(LuatTre.locDuoc(c))
    }

    @Test fun bat_vaoModeTre_vaGiuTuyChonDaSua() {
        val c = LuatTre.doc("""{"treBat":false,"phutToan":15,"mucToan":20,"appChoPhep":["$yt","com.netflix.ninja"]}""").copy(coPin = true)
        val bat = LuatTre.bat(c)!!
        assertTrue(bat.treBat)
        assertEquals("tre", bat.treMode)
        assertEquals(15, bat.phutToan)
        assertEquals(20, bat.mucToan)
        assertTrue(LuatTre.locDuoc(bat))
        val phu = LuatTre.sangPhuHuynh(bat)
        assertEquals("phuHuynh", phu.treMode)
        assertFalse(LuatTre.locDuoc(phu))
        assertEquals("phuHuynh", LuatTre.doc(LuatTre.ghiTre(null, phu)).treMode)
        assertEquals("tre", LuatTre.veTre(phu).treMode)
        assertFalse(LuatTre.tat(phu).treBat)
    }

    @Test fun thieuYoutubeDangCai_thiThemLai() {
        val c = LuatTre.doc("""{"appChoPhep":["com.netflix.ninja"]}""")
        val h = LuatTre.hienThi(c, setOf(yt))
        assertTrue(yt in h.app)
        assertTrue("com.netflix.ninja" in h.app)
    }

    @Test fun dongHo_chiTangKhiYoutubeTruoc_vaConSauKhiDoc() {
        var d = LuatTre.DongHo(0, false)
        d = LuatTre.cong(d, youtubeTruoc = false, them = 15)
        assertEquals(0, d.giay)
        d = LuatTre.cong(d, youtubeTruoc = true, them = 15)
        assertEquals(15, d.giay)
        assertFalse(LuatTre.denLuc(d, 30))
        d = LuatTre.cong(d, true, 30 * 60 - 15)
        assertTrue(LuatTre.denLuc(d, 30))
        d = LuatTre.moBai(d)
        assertEquals(30 * 60, LuatTre.cong(d, true, 15).giay)
        val lai = LuatTre.docDongHo(LuatTre.ghiDongHo(d))
        assertEquals(d.giay, lai.giay)
        assertTrue(lai.dangChoBai)
        assertEquals(0, LuatTre.dungBai(d).giay)
        assertFalse(LuatTre.dungBai(d).dangChoBai)
    }

    @Test fun gopJson_giuKhoaTreKhiLuuGio() {
        val cu = """{"bat":true,"treBat":true,"treMode":"tre","phutToan":15,"khung":[]}"""
        val gop = LuatTre.gopJson(cu, """{"bat":false,"khung":[]}""")!!
        assertTrue(LuatTre.doc(gop).treBat)
        assertEquals(15, LuatTre.doc(gop).phutToan)
        assertNull(LuatTre.gopJson(cu, "{"))
    }
}
