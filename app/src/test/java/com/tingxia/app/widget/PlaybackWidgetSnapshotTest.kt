package com.tingxia.app.widget

import com.tingxia.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackWidgetSnapshotTest {
    @Test
    fun selectsWidgetLayoutForAvailableHeight() {
        assertEquals(R.layout.playback_widget_compact, widgetLayoutForHeight(72))
        assertEquals(R.layout.playback_widget_compact, widgetLayoutForHeight(119))
        assertEquals(R.layout.playback_widget, widgetLayoutForHeight(120))
        assertEquals(R.layout.playback_widget, widgetLayoutForHeight(0))
    }

    @Test
    fun coverSpec_followsTheSlotRatioSoTheCoverMeetsThePanel() {
        // A 68dp x 72dp strip slot: the bitmap must be squarer than 3:4, or fitting it leaves the
        // gap between cover and panel that this replaces.
        val strip = widgetCoverSpec(slotWidthDp = 68, slotHeightDp = 72)
        assertEquals(330, strip.widthPx)
        assertEquals(349, strip.heightPx) // 72dp bucketed to 72 -> 330 * 72 / 68
        assertEquals(14f * 330 / 68, strip.radiusPx, 0.01f)

        // The expanded panel is a fixed 100dp x 132dp tile, so its cover stays near 3:4.
        val panel = widgetCoverSpec(slotWidthDp = 100, slotHeightDp = 132)
        assertEquals(330, panel.widthPx)
        assertEquals(448, panel.heightPx) // 132dp bucketed to 136 -> 330 * 136 / 100

        // Heights are bucketed to 8dp, so nearby slots share one cached bitmap.
        assertEquals(
            widgetCoverSpec(68, 74).heightPx,
            widgetCoverSpec(68, 71).heightPx,
        )
    }

    @Test
    fun chapterLine_joinsTitleAndCountAndSurvivesEitherMissing() {
        assertEquals("008 摊牌 · 8/1500 章", widgetChapterLine("008 摊牌", "8/1500 章"))
        assertEquals("008 摊牌", widgetChapterLine("008 摊牌", ""))
        assertEquals("8/1500 章", widgetChapterLine("", "8/1500 章"))
        assertEquals("", widgetChapterLine("", ""))
    }

    @Test
    fun panelTint_keepsTheHueButLandsOnOneDarkLevel() {
        // Whatever the cover, the panel comes out dark enough for white text: luminance is forced to
        // one level instead of following the cover's own brightness.
        val covers = intArrayOf(
            0xFFB3121A.toInt(), // 十日终焉: black-and-red woodcut
            0xFFF2E4C9.toInt(), // a pale paper cover
            0xFF101014.toInt(), // near black
            0xFF3F8FD8.toInt(), // a blue photograph
        )
        covers.forEach { cover ->
            val tint = widgetPanelTint(cover)
            val luminance = 0.299f * ((tint shr 16) and 0xFF) +
                0.587f * ((tint shr 8) and 0xFF) +
                0.114f * (tint and 0xFF)
            assertEquals(0xFF, (tint ushr 24) and 0xFF)
            // Near-black covers cannot be scaled up to the target, so allow the floor.
            assertTrue("$cover -> $tint", luminance <= 95f)
            if (cover != 0xFF101014.toInt()) assertTrue("$cover -> $tint", luminance >= 70f)
        }

        // Hue survives: a red cover stays red-dominant, a blue one blue-dominant.
        val red = widgetPanelTint(0xFFB3121A.toInt())
        assertTrue(((red shr 16) and 0xFF) > (red and 0xFF))
        val blue = widgetPanelTint(0xFF3F8FD8.toInt())
        assertTrue((blue and 0xFF) > ((blue shr 16) and 0xFF))

        // Grey in, grey out: no chroma is invented.
        val grey = widgetPanelTint(0xFF808080.toInt())
        assertEquals((grey shr 16) and 0xFF, (grey shr 8) and 0xFF)
    }

    @Test
    fun headline_mergesChapterOnlyForTheStrip() {
        assertEquals(
            "我不是戏神 · 006 陈氏编导法则",
            widgetHeadline("我不是戏神", "006 陈氏编导法则", merged = true),
        )
        assertEquals("我不是戏神", widgetHeadline("我不是戏神", "006 陈氏编导法则", merged = false))
        assertEquals("我不是戏神", widgetHeadline("我不是戏神", "", merged = true))
    }

    @Test
    fun coverInitial_skipsPunctuationAndBrackets() {
        // 《10日终焉》番茄唱… used to render its opening bracket as the cover initial.
        assertEquals("1", widgetCoverInitial("《10日终焉》番茄唱工"))
        assertEquals("三", widgetCoverInitial("三体"))
        assertEquals("A", widgetCoverInitial("\"A Study in Scarlet\""))
        assertEquals("听", widgetCoverInitial(""))
        assertEquals("听", widgetCoverInitial("《》 —— ·"))
    }

    @Test
    fun progressPermille_clampsPositionToDuration() {
        assertEquals(
            250,
            PlaybackWidgetSnapshot(positionMs = 2_500L, durationMs = 10_000L).progressPermille,
        )
        assertEquals(
            1_000,
            PlaybackWidgetSnapshot(positionMs = 12_000L, durationMs = 10_000L).progressPermille,
        )
        assertEquals(
            0,
            PlaybackWidgetSnapshot(positionMs = -1L, durationMs = 10_000L).progressPermille,
        )
        assertEquals(
            1_000,
            PlaybackWidgetSnapshot(positionMs = Long.MAX_VALUE, durationMs = Long.MAX_VALUE).progressPermille,
        )
    }

    @Test
    fun formatDuration_supportsMinutesAndHours() {
        assertEquals("00:00", formatWidgetDuration(-1L))
        assertEquals("02:05", formatWidgetDuration(125_000L))
        assertEquals("1:02:03", formatWidgetDuration(3_723_000L))
    }
}
