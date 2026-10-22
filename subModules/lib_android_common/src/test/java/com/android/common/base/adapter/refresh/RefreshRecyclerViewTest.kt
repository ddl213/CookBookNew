package com.android.common.base.adapter.refresh

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.common.base.adapter.refresh.header.RefreshHeaderAdapter
import com.android.common.base.adapter.refresh.header.RefreshHeaderEnum
import com.android.common.base.adapter.refresh.header.RefreshHeaderView
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
class RefreshRecyclerViewTest {
    private lateinit var controller: ActivityController<Activity>
    private lateinit var activity: Activity
    private lateinit var rv: RefreshRecyclerView
    private lateinit var header: RefreshHeaderView
    private var refreshes = 0
    private var clicks = 0
    private var downTime = 0L

    @Before fun setUp() {
        controller = Robolectric.buildActivity(Activity::class.java).setup()
        activity = controller.get()
        rv = RefreshRecyclerView(activity)
        rv.overScrollMode = View.OVER_SCROLL_NEVER
        header = RefreshHeaderView(activity)
        rv.layoutManager = LinearLayoutManager(activity)
        rv.setRefreshHeader(header)
        rv.adapter = ConcatAdapter(RefreshHeaderAdapter(header), ContentAdapter { clicks++ })
        rv.setOnRefreshListener { refreshes++ }
        activity.setContentView(rv)
        layout()
        shadowOf(Looper.getMainLooper()).idle()
        layout()
    }

    @After fun tearDown() { controller.pause().stop().destroy() }

    @Test fun slowPullAccumulatesAcrossMovesAndStartsRefreshOnce() {
        val step = (ViewConfiguration.get(activity).scaledTouchSlop / 2).coerceAtLeast(1)
        touch(MotionEvent.ACTION_DOWN, 100f, 400f)
        for (y in 100 + step..700 step step) touch(MotionEvent.ACTION_MOVE, y.toFloat(), 400f)
        touch(MotionEvent.ACTION_UP, 700f, 400f)
        assertEquals(1, refreshes)
        assertEquals(RecyclerView.SCROLL_STATE_IDLE, rv.scrollState)
    }

    @Test fun dragStartingOnClickableItemUsesItsOwnDownCoordinates() {
        touch(MotionEvent.ACTION_DOWN, 600f)
        touch(MotionEvent.ACTION_MOVE, 630f)
        touch(MotionEvent.ACTION_UP, 630f)
        assertEquals(0, refreshes)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350))
        layout()
        touch(MotionEvent.ACTION_DOWN, 100f)
        touch(MotionEvent.ACTION_UP, 100f)
        assertEquals(1, clicks)
        assertEquals(RecyclerView.SCROLL_STATE_IDLE, rv.scrollState)
    }

    @Test fun canceledPullDoesNotRefresh() {
        touch(MotionEvent.ACTION_DOWN, 100f, 400f)
        touch(MotionEvent.ACTION_MOVE, 700f, 400f)
        touch(MotionEvent.ACTION_CANCEL, 700f, 400f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350))
        assertEquals(0, refreshes)
        assertEquals(0, header.getVisibleHeight())
        assertEquals(RecyclerView.SCROLL_STATE_IDLE, rv.scrollState)
    }

    @Test fun pullingAfterNativeScrollingEndsTheOriginalScrollState() {
        touch(MotionEvent.ACTION_DOWN, 500f, 400f)
        touch(MotionEvent.ACTION_MOVE, 100f, 400f)
        assertEquals(RecyclerView.SCROLL_STATE_DRAGGING, rv.scrollState)
        touch(MotionEvent.ACTION_MOVE, 650f, 400f)
        touch(MotionEvent.ACTION_MOVE, 850f, 400f)
        touch(MotionEvent.ACTION_MOVE, 1300f, 400f)
        touch(MotionEvent.ACTION_UP, 1300f, 400f)
        assertEquals(1, refreshes)
        assertEquals(RecyclerView.SCROLL_STATE_IDLE, rv.scrollState)
    }

    @Test fun completingDuringABlockedGestureDoesNotReleaseItsTailToRecyclerView() {
        pull()
        assertEquals(1, refreshes)
        touch(MotionEvent.ACTION_DOWN, 100f)
        rv.refreshComplete()
        touch(MotionEvent.ACTION_MOVE, 700f)
        touch(MotionEvent.ACTION_UP, 700f)
        assertEquals(1, refreshes)
        assertEquals(RecyclerView.SCROLL_STATE_IDLE, rv.scrollState)
    }

    @Test fun replacingTheAdapterClearsTheOldHeaderAndRestoresClicks() {
        pull()
        assertEquals(1, refreshes)
        val previousHeader = header
        rv.setRefreshHeader(null)
        rv.adapter = ContentAdapter { clicks++ }
        layout()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1600))
        assertEquals(RefreshHeaderEnum.NORMAL, previousHeader.state)
        assertEquals(0, previousHeader.getVisibleHeight())
        touch(MotionEvent.ACTION_DOWN, 100f)
        touch(MotionEvent.ACTION_UP, 100f)
        assertEquals(1, clicks)
    }

    @Test fun missingRefreshListenerDoesNotLeaveTheListLocked() {
        rv = RefreshRecyclerView(activity)
        rv.overScrollMode = View.OVER_SCROLL_NEVER
        header = RefreshHeaderView(activity)
        rv.layoutManager = LinearLayoutManager(activity)
        rv.setRefreshHeader(header)
        rv.adapter = ConcatAdapter(RefreshHeaderAdapter(header), ContentAdapter { clicks++ })
        activity.setContentView(rv)
        layout()
        shadowOf(Looper.getMainLooper()).idle()
        layout()
        touch(MotionEvent.ACTION_DOWN, 100f, 400f)
        touch(MotionEvent.ACTION_MOVE, 700f, 400f)
        touch(MotionEvent.ACTION_UP, 700f, 400f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350))
        assertEquals(0, header.getVisibleHeight())
        touch(MotionEvent.ACTION_DOWN, 100f)
        touch(MotionEvent.ACTION_UP, 100f)
        assertEquals(1, clicks)
    }

    @Test fun completionDoesNotRefreshAgainOrScrollTheUserBackToTop() {
        pull()
        rv.refreshComplete()
        touch(MotionEvent.ACTION_DOWN, 100f)
        touch(MotionEvent.ACTION_MOVE, 700f)
        touch(MotionEvent.ACTION_UP, 700f)
        assertEquals(1, refreshes)
        rv.scrollToPosition(12)
        layout()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1600))
        layout()
        assertTrue((rv.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() > 0)
    }

    @Test fun gridHeaderUsesFullRowAndPreservesContentLookupLocalPositions() {
        val grid = GridLayoutManager(activity, 3)
        grid.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = if (position == 0) 2 else 1
        }
        rv.layoutManager = grid
        assertEquals(3, grid.spanSizeLookup.getSpanSize(0))
        assertEquals(2, grid.spanSizeLookup.getSpanSize(1))
        assertEquals(1, grid.spanSizeLookup.getSpanSize(2))
        rv.adapter = rv.adapter
        assertEquals(2, grid.spanSizeLookup.getSpanSize(1))
        rv.swapAdapter(ContentAdapter {}, true)
        assertEquals(2, grid.spanSizeLookup.getSpanSize(0))
    }

    @Test fun gridConfiguredBeforeAdapterAlsoWrapsHeader() {
        val other = RefreshRecyclerView(activity)
        val grid = GridLayoutManager(activity, 2)
        other.layoutManager = grid
        val otherHeader = RefreshHeaderView(activity)
        other.setRefreshHeader(otherHeader)
        other.adapter = ConcatAdapter(RefreshHeaderAdapter(otherHeader), ContentAdapter {})
        assertEquals(2, grid.spanSizeLookup.getSpanSize(0))
        assertEquals(1, grid.spanSizeLookup.getSpanSize(1))
    }

    @Test fun changingHeadersInsideConcatAdapterKeepsGesturesAndGridInSync() {
        val combined = rv.adapter as ConcatAdapter
        val oldHeaderAdapter = combined.adapters.first()
        val grid = GridLayoutManager(activity, 2)
        rv.layoutManager = grid
        combined.removeAdapter(oldHeaderAdapter)
        layout()
        pull()
        assertEquals(0, refreshes)
        assertEquals(1, grid.spanSizeLookup.getSpanSize(0))

        val replacement = RefreshHeaderView(activity)
        combined.addAdapter(0, RefreshHeaderAdapter(replacement))
        layout()
        pull()
        layout()
        assertEquals(1, refreshes)
        assertEquals(RefreshHeaderEnum.REFRESHING, replacement.state)
        assertTrue(replacement.height > 1)
        assertEquals(2, grid.spanSizeLookup.getSpanSize(0))
    }

    @Test fun explicitlyClearingHeaderRemainsEffectiveAcrossLayoutAndGestures() {
        rv.setRefreshHeader(null)
        layout()
        pull()
        assertEquals(0, refreshes)
        assertEquals(RefreshHeaderEnum.NORMAL, header.state)
    }

    private fun pull() {
        touch(MotionEvent.ACTION_DOWN, 100f)
        touch(MotionEvent.ACTION_MOVE, 700f)
        touch(MotionEvent.ACTION_UP, 700f)
    }

    private fun touch(action: Int, y: Float, x: Float = 100f) {
        if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        try { rv.dispatchTouchEvent(event) } finally { event.recycle() }
        // View 将 performClick 投递到主线程消息队列；模拟当前事件处理完成。
        if (action == MotionEvent.ACTION_UP) shadowOf(Looper.getMainLooper()).idle()
    }

    private fun layout() {
        rv.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY))
        rv.layout(0, 0, 500, 900)
    }

    private class ContentAdapter(private val click: () -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = 30
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val item = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 100)
                addView(TextView(parent.context).apply { setOnClickListener { click() } },
                    FrameLayout.LayoutParams(200, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            return object : RecyclerView.ViewHolder(item) {}
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            ((holder.itemView as FrameLayout).getChildAt(0) as TextView).text = "Row $position"
        }
    }
}
