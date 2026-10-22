package com.android.common.utils

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.common.base.adapter.refresh.RefreshRecyclerView
import com.android.common.base.adapter.refresh.header.RefreshHeaderAdapter
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
class RvDecorationRefreshTest {
    private lateinit var controller: ActivityController<Activity>
    private lateinit var activity: Activity

    @Before fun setUp() {
        controller = Robolectric.buildActivity(Activity::class.java).setup()
        activity = controller.get()
    }

    @After fun tearDown() { controller.pause().stop().destroy() }

    @Test fun collapsedHeaderWithoutDecorationHasNoHeight() {
        val (rv, header) = createList()
        assertEquals(0, header.measuredHeight)
        assertEquals(0, header.height)
        assertEquals(12, item(rv, 1).top)
    }

    @Test fun linearFirstContentKeepsOnlyConfiguredTopSpacingThroughPullAndReset() {
        val decoration = RvDecoration.Builder().setVertical(30).setHorizontal(10).build()
        val (rv, header) = createList(decoration = decoration)
        assertEquals(0, header.height)
        assertEquals("paddingTop 12 + content top spacing 30", 42, item(rv, 1).top)
        assertEquals(Rect(0, 0, 0, 0), decorationOf(rv, header))
        assertEquals(Rect(10, 30, 10, 30), decorationOf(rv, item(rv, 1)))

        header.onMove(80f, 80f)
        layout(rv)
        assertEquals(80, header.height)
        assertEquals(122, item(rv, 1).top)
        header.onReset()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        layout(rv)
        assertEquals(0, header.height)
        assertEquals(42, item(rv, 1).top)
    }

    @Test fun ordinaryLinearListRetainsItsConfiguredSpacing() {
        val rv = RefreshRecyclerView(activity).apply {
            setPadding(0, 12, 0, 0)
            layoutManager = LinearLayoutManager(activity)
            addItemDecoration(RvDecoration.Builder().setVertical(30).setHorizontal(10).build())
            adapter = ContentAdapter()
        }
        attach(rv)
        assertEquals(42, item(rv, 0).top)
        assertEquals(Rect(10, 30, 10, 30), decorationOf(rv, item(rv, 0)))
        assertEquals(Rect(10, 0, 10, 30), decorationOf(rv, item(rv, 1)))
    }

    @Test fun gridFirstContentRowKeepsItsTopAndColumnSpacingAfterFullSpanHeader() {
        val grid = GridLayoutManager(activity, 2)
        val (rv, header) = createList(grid,
            RvDecoration.Builder().setVertical(30).setHorizontal(10).build())
        assertEquals(0, header.height)
        assertEquals(Rect(0, 0, 0, 0), decorationOf(rv, header))
        assertEquals(42, item(rv, 1).top)
        assertEquals(42, item(rv, 2).top)
        assertEquals(Rect(0, 30, 5, 30), decorationOf(rv, item(rv, 1)))
        assertEquals(Rect(5, 30, 0, 30), decorationOf(rv, item(rv, 2)))
        assertEquals(Rect(0, 0, 5, 30), decorationOf(rv, item(rv, 3)))
    }

    @Test fun gridSpacingUsesRealSpanIndexAndSpanSizeForContent() {
        val grid = GridLayoutManager(activity, 3).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) = when (position) {
                    0, 1 -> 2
                    2 -> 1
                    3 -> 3
                    else -> 1
                }
            }
        }
        val (rv, _) = createList(grid,
            RvDecoration.Builder().setVertical(30).setHorizontal(12).build())
        assertEquals(Rect(0, 30, 4, 30), decorationOf(rv, item(rv, 1)))
        // 第二个内容项跨两列，必须换到第二行，不能按 position % spanCount 计算。
        assertEquals(Rect(0, 0, 4, 30), decorationOf(rv, item(rv, 2)))
        assertEquals(Rect(8, 0, 0, 30), decorationOf(rv, item(rv, 3)))
        assertEquals(Rect(0, 0, 0, 30), decorationOf(rv, item(rv, 4)))
        assertEquals(42, item(rv, 1).top)
        assertEquals(172, item(rv, 2).top)
        assertEquals(172, item(rv, 3).top)
    }

    @Test fun dividerDoesNotDrawForHeaderEvenWhenItIsNotFirstAttachedChild() {
        val header = RefreshHeaderView(activity)
        val decoration = RvDecoration.Builder().setDivider(Color.RED, 1f).build()
        val rv = RefreshRecyclerView(activity).apply {
            setPadding(0, 12, 0, 0)
            layoutManager = LinearLayoutManager(activity)
            adapter = ConcatAdapter(ContentAdapter(1), RefreshHeaderAdapter(header), ContentAdapter(3))
        }
        attach(rv)
        val canvas = RecordingCanvas()
        decoration.onDraw(canvas, rv, RecyclerView.State())
        assertFalse("header must not contribute a divider", canvas.rectangles.any { it.top == header.bottom.toFloat() })
        assertEquals(3, canvas.rectangles.size)
    }

    private fun createList(
        manager: RecyclerView.LayoutManager = LinearLayoutManager(activity),
        decoration: RvDecoration? = null
    ): Pair<RefreshRecyclerView, RefreshHeaderView> {
        val header = RefreshHeaderView(activity)
        val rv = RefreshRecyclerView(activity).apply {
            setPadding(0, 12, 0, 0)
            layoutManager = manager
            decoration?.let(::addItemDecoration)
            adapter = ConcatAdapter(RefreshHeaderAdapter(header), ContentAdapter())
        }
        attach(rv)
        return rv to header
    }

    private fun attach(rv: RecyclerView) {
        activity.setContentView(rv)
        layout(rv)
        shadowOf(Looper.getMainLooper()).idle()
        layout(rv)
    }

    private fun layout(rv: RecyclerView) {
        rv.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY))
        rv.layout(0, 0, 600, 900)
    }

    private fun item(rv: RecyclerView, position: Int): View =
        requireNotNull(rv.layoutManager?.findViewByPosition(position))

    private fun decorationOf(rv: RecyclerView, view: View): Rect = Rect().also {
        rv.layoutManager!!.calculateItemDecorationsForChild(view, it)
    }

    private class ContentAdapter(private val count: Int = 8) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = count
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val text = TextView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 100)
            }
            return object : RecyclerView.ViewHolder(text) {}
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            (holder.itemView as TextView).text = "Row $position"
        }
    }

    private class RecordingCanvas : Canvas() {
        val rectangles = mutableListOf<RectF>()
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
            rectangles += RectF(left, top, right, bottom)
        }
    }
}
