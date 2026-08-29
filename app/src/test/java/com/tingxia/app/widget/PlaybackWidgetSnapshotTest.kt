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
    fun slotHeight_readsTheEndOfTheRangeTheLauncherActuallyHandsOut() {
        // A one-cell strip reports 72..87dp: portrait gets the top of that range, landscape the bottom.
        assertEquals(87, widgetSlotHeightDp(72, 87, portrait = true, fallbackDp = 160))
        assertEquals(72, widgetSlotHeightDp(72, 87, portrait = false, fallbackDp = 160))

        // Missing options fall back to whatever is present, then to the default.
        assertEquals(72, widgetSlotHeightDp(72, 0, portrait = true, fallbackDp = 160))
        assertEquals(87, widgetSlotHeightDp(0, 87, portrait = false, fallbackDp = 160))
        assertEquals(160, widgetSlotHeightDp(0, 0, portrait = true, fallbackDp = 160))
    }

    @Test
    fun coverSpec_matchesTheColumnAndTheMeasuredCellHeight() {
        // The strip's cover column is 64dp wide and as tall as the cell, so the bitmap is rendered at
        // that ratio: nothing is left for the ImageView to crop or letterbox.
        val strip = widgetCoverSpec(slotWidthDp = 64, slotHeightDp = 87)
        assertEquals(330, strip.widthPx)
        assertEquals(449, strip.heightPx) // round(330 * 87 / 64)
        assertEquals(14f * 330 / 64, strip.radiusPx, 0.01f)

        // The tall size's tile is 120dp wide, which is 3:4 at the ~160dp two cells give.
        val panel = widgetCoverSpec(slotWidthDp = 120, slotHeightDp = 160)
        assertEquals(440, panel.heightPx) // round(330 * 160 / 120)
        assertEquals(14f * 330 / 120, panel.radiusPx, 0.01f)
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
