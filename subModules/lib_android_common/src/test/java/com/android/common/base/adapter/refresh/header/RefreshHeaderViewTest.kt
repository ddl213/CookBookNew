package com.android.common.base.adapter.refresh.header

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class RefreshHeaderViewTest {
    private lateinit var controller: ActivityController<Activity>
    private lateinit var container: FrameLayout
    private lateinit var header: RefreshHeaderView

    @Before fun setUp() {
        controller = Robolectric.buildActivity(Activity::class.java).setup()
        container = FrameLayout(controller.get())
        header = RefreshHeaderView(controller.get())
        container.addView(header, FrameLayout.LayoutParams(500, FrameLayout.LayoutParams.WRAP_CONTENT))
        controller.get().setContentView(container)
        container.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY))
        container.layout(0, 0, 500, 900)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After fun tearDown() { controller.pause().stop().destroy() }

    @Test fun alreadyRefreshingAndDoneDoNotReportANewRefresh() {
        header.onMove(400f, 400f)
        assertTrue(header.onRelease())
        assertFalse(header.onRelease())
        header.refreshComplete()
        assertFalse(header.onRelease())
    }

    @Test fun newPullCancelsPreviousCollapseWithoutExecutingItsEndCallback() {
        header.onMove(60f, 60f)
        assertFalse(header.onRelease())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))
        header.onMove(400f, 400f)
        assertTrue(header.onRelease())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        val refreshingHeight = header.getVisibleHeight()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertEquals(refreshingHeight, header.getVisibleHeight())
        assertTrue(header.getVisibleHeight() > 0)
    }

    @Test fun resetCancelsPendingCompletionBeforeANewRefresh() {
        header.onMove(400f, 400f)
        assertTrue(header.onRelease())
        header.refreshComplete()
        header.onReset()
        header.onMove(400f, 400f)
        assertTrue(header.onRelease())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        val refreshingHeight = header.getVisibleHeight()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500))
        assertEquals(refreshingHeight, header.getVisibleHeight())
        assertFalse(header.onRelease())
    }

    @Test fun detachingCompletedHeaderRemovesItsPendingCollapse() {
        header.onMove(400f, 400f)
        assertTrue(header.onRelease())
        header.refreshComplete()
        container.removeView(header)
        container.addView(header)
        header.onMove(400f, 400f)
        assertTrue(header.onRelease())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        val refreshingHeight = header.getVisibleHeight()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500))
        assertEquals(refreshingHeight, header.getVisibleHeight())
    }

    @Test fun inFlightRefreshSurvivesDetachAndCanFinishAfterReattach() {
        header.onMove(400f, 400f)
        assertTrue(header.onRelease())
        container.removeView(header)
        assertEquals(RefreshHeaderEnum.REFRESHING, header.state)
        container.addView(header)
        assertFalse(header.onRelease())
        header.refreshComplete()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500))
        assertEquals(RefreshHeaderEnum.NORMAL, header.state)
        assertEquals(0, header.getVisibleHeight())
    }

    @Test fun completingWhileDetachedDoesNotLeaveAStaleDoneState() {
        header.onMove(400f, 400f)
        assertTrue(header.onRelease())
        container.removeView(header)
        header.refreshComplete()
        container.addView(header)
        assertEquals(RefreshHeaderEnum.NORMAL, header.state)
        assertEquals(0, header.getVisibleHeight())
        header.onMove(400f, 400f)
        assertTrue(header.onRelease())
    }
}
