package com.tingxia.app.widget

import android.content.ComponentName
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingxia.app.R
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackWidgetLayoutTest {
    @Test
    fun widgetLayout_inflatesWithRequiredControls() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val remoteViews = RemoteViews(context.packageName, R.layout.playback_widget)

        val root = remoteViews.apply(context, FrameLayout(context))

        assertNotNull(root.findViewById(R.id.widget_artwork))
        assertNotNull(root.findViewById(R.id.widget_previous))
        assertNotNull(root.findViewById(R.id.widget_play_pause))
        assertNotNull(root.findViewById(R.id.widget_next))
        assertNotNull(root.findViewById(R.id.widget_progress))
    }

    @Test
    fun widgetProvider_isRegistered() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val receiver = context.packageManager.getReceiverInfo(
            ComponentName(context, PlaybackWidgetProvider::class.java),
            0,
        )

        assertNotNull(receiver)
    }
}
