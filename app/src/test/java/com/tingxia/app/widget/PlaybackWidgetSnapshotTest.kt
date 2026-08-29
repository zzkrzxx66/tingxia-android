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
    fun coverSpec_takesTheWidthTheCoverAsksForInsteadOfCroppingIt() {
        // A 3:4 cover in a 72dp strip: the column narrows to 54dp so the whole cover fits.
        val portrait = widgetCoverSpec(
            slotHeightDp = 72,
            coverAspect = 0.75f,
            defaultWidthDp = 68,
            dynamicWidth = true,
        )
        assertEquals(54, portrait.widthDp) // 72dp bucketed to 72 -> round(72 * 0.75)
        assertEquals(72, portrait.heightDp)
        assertEquals(440, portrait.heightPx) // 330 * 72 / 54
        assertEquals(14f * 330 / 54, portrait.radiusPx, 0.01f)

        // A square cover in the same strip wants a square column.
        val square = widgetCoverSpec(
            slotHeightDp = 72,
            coverAspect = 1f,
            defaultWidthDp = 68,
            dynamicWidth = true,
        )
        assertEquals(72, square.widthDp)
        assertEquals(330, square.heightPx)

        // One freak cover cannot take over the strip: width stays within half again of the default.
        assertEquals(
            102,
            widgetCoverSpec(72, coverAspect = 3f, defaultWidthDp = 68, dynamicWidth = true).widthDp,
        )
        assertEquals(
            34,
            widgetCoverSpec(72, coverAspect = 0.1f, defaultWidthDp = 68, dynamicWidth = true).widthDp,
        )

        // Before Android 12 a RemoteViews child cannot be resized, so the layout width stands and
        // the bitmap is cut to the slot instead.
        val legacy = widgetCoverSpec(72, coverAspect = 0.75f, defaultWidthDp = 68, dynamicWidth = false)
        assertEquals(68, legacy.widthDp)
        assertEquals(349, legacy.heightPx) // 330 * 72 / 68

        // Heights are bucketed to 8dp, so nearby slots share one cached bitmap.
        assertEquals(
            widgetCoverSpec(74, 0.75f, 68, true).heightPx,
            widgetCoverSpec(71, 0.75f, 68, true).heightPx,
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
