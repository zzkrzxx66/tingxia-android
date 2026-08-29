package com.tingxia.app.widget

import com.tingxia.app.R
import org.junit.Assert.assertEquals
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
