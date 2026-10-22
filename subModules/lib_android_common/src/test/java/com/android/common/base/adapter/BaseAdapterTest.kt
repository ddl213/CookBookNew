package com.android.common.base.adapter

import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class BaseAdapterTest {
    @Test
    fun initialSubmissionSnapshotsMutableSource() {
        val adapter = RecordingAdapter()
        val source = mutableListOf("one", "two")

        adapter.submitList(source)
        source.add("three")

        assertEquals(listOf("one", "two"), adapter.currentList)
        assertEquals(2, adapter.itemCount)
    }

    @Test
    fun sameMutableSourceResubmitsUpdatedContents() {
        val adapter = RecordingAdapter()
        val source = mutableListOf("one")
        val inserted = mutableListOf<Pair<Int, Int>>()
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                inserted += positionStart to itemCount
            }
        })
        submitAndAwait(adapter, source)
        inserted.clear()
        source.add("two")

        submitAndAwait(adapter, source)

        assertEquals(listOf("one", "two"), adapter.currentList)
        assertEquals(listOf(1 to 1), inserted)
        source.clear()
        assertEquals(listOf("one", "two"), adapter.currentList)
    }

    @Test
    fun replacementAndClearCommitCallbacks() {
        val adapter = RecordingAdapter()
        submitAndAwait(adapter, listOf("one", "two"))
        submitAndAwait(adapter, listOf("replacement"))
        assertEquals(listOf("replacement"), adapter.currentList)

        submitAndAwait(adapter, emptyList())
        assertEquals(0, adapter.itemCount)
        submitAndAwait(adapter, listOf("restored"))
        submitAndAwait(adapter, null)
        assertEquals(emptyList<String>(), adapter.currentList)
    }

    @Test
    fun additionsIncludeReplacementAndEarlierAdditionsStillBeingDiffed() {
        val adapter = RecordingAdapter()
        submitAndAwait(adapter, listOf("old"))
        val committed = AtomicBoolean()

        adapter.submitList(listOf("replacement"))
        adapter.addItems(listOf("first addition"))
        adapter.addItems(listOf("second addition"), Runnable { committed.set(true) })
        awaitCommit(committed)

        assertEquals(listOf("replacement", "first addition", "second addition"), adapter.currentList)
    }

    @Test
    fun additionsAfterClearDoNotRestorePreviousItems() {
        val adapter = RecordingAdapter()
        submitAndAwait(adapter, listOf("old"))
        adapter.submitList(null)
        val committed = AtomicBoolean()

        adapter.addItems(listOf("new"), Runnable { committed.set(true) })
        awaitCommit(committed)

        assertEquals(listOf("new"), adapter.currentList)
    }

    @Test
    fun addedItemsAreAlsoSnapshotted() {
        val adapter = RecordingAdapter()
        val source = mutableListOf("one", "two")

        adapter.addItems(source)
        source.clear()

        assertEquals(listOf("one", "two"), adapter.currentList)
    }

    @Test
    fun additionsAfterPendingEmptyListUseTheClearedSnapshot() {
        val adapter = RecordingAdapter()
        submitAndAwait(adapter, listOf("old"))
        val committed = AtomicBoolean()

        adapter.submitList(emptyList())
        adapter.addItems(listOf("new"), Runnable { committed.set(true) })
        awaitCommit(committed)

        assertEquals(listOf("new"), adapter.currentList)
    }

    @Test
    fun initializerCanUseWithBinding() {
        val adapter = RecordingAdapter()
        adapter.initViewHolder { holder ->
            holder.withBinding<TestBinding> { first.text = "initialized" }
        }

        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)

        assertEquals("initialized", (holder.binding as TestBinding).first.text.toString())
    }

    @Test
    fun initializerClickListenerRemainsWhenAdapterHasNoClickListener() {
        val adapter = RecordingAdapter()
        var clickCount = 0
        adapter.initViewHolder { holder ->
            holder.itemView.setOnClickListener { clickCount++ }
        }

        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        holder.itemView.performClick()

        assertEquals(1, clickCount)
    }

    @Test
    fun holderKeepsTrailingLambdaInitializerCompatibility() {
        val binding = TestBinding.inflate(LayoutInflater.from(context), null, false)

        BaseViewHolder(binding) { holder ->
            holder.withBinding<TestBinding> { first.text = "initialized" }
        }

        assertEquals("initialized", binding.first.text.toString())
    }

    @Test
    fun localViewTypeIsAvailableDuringInitializationAndInsideConcat() {
        val header = RecordingAdapter().apply { submitList(listOf("header")) }
        val adapter = MultiRecordingAdapter().apply { submitList(listOf("one")) }
        var initializedType: Int? = null
        adapter.initViewHolder { holder ->
            initializedType = holder.localViewType
            holder.withBinding<TestBinding> { first.text = "initialized" }
        }

        val recycler = layout(ConcatAdapter(header, adapter))
        val holder = recycler.findViewHolderForAdapterPosition(1) as BaseViewHolder<*>

        assertEquals(7, initializedType)
        assertEquals(7, holder.localViewType)
    }

    @Test
    fun multiAdapterSnapshotsLayoutMappingAfterFirstUse() {
        val adapter = MultiRecordingAdapter()
        adapter.onCreateViewHolder(FrameLayout(context), 7)

        adapter.layouts.clear()
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 7)

        assertTrue(holder.binding is TestBinding)
    }

    @Test
    fun clickUsesBindingPositionInsideConcat() {
        val header = RecordingAdapter().apply { submitList(listOf("header")) }
        val adapter = RecordingAdapter().apply { submitList(listOf("one")) }
        val positions = mutableListOf<Int>()
        adapter.setOnItemClickListener { _, _, position -> positions += position }
        adapter.setOnItemLongClickListener { _, _, position -> positions += position; true }
        adapter.addChildClickViewIds(android.R.id.text1)
        adapter.setOnItemChildClickListener { _, _, position -> positions += position }
        val recycler = layout(ConcatAdapter(header, adapter))
        val holder = recycler.findViewHolderForAdapterPosition(1)!!

        holder.itemView.performClick()
        holder.itemView.performLongClick()
        holder.itemView.findViewById<View>(android.R.id.text1).performClick()

        assertEquals(listOf(0, 0, 0), positions)
    }

    @Test
    fun existingHolderUsesLatestListeners() {
        val adapter = RecordingAdapter().apply { submitList(listOf("one")) }
        val calls = mutableListOf<String>()
        adapter.addChildClickViewIds(android.R.id.text1)
        adapter.setOnItemClickListener { _, _, _ -> calls += "old click" }
        adapter.setOnItemLongClickListener { _, _, _ -> calls += "old long"; true }
        adapter.setOnItemChildClickListener { _, _, _ -> calls += "old child" }
        val recycler = layout(adapter)
        val holder = recycler.findViewHolderForAdapterPosition(0)!!

        adapter.setOnItemClickListener { _, _, _ -> calls += "new click" }
        adapter.setOnItemLongClickListener { _, _, _ -> calls += "new long"; true }
        adapter.setOnItemChildClickListener { _, _, _ -> calls += "new child" }
        holder.itemView.performClick()
        holder.itemView.performLongClick()
        holder.itemView.findViewById<View>(android.R.id.text1).performClick()

        assertEquals(listOf("new click", "new long", "new child"), calls)
    }

    @Test
    fun listenerAddedAfterHolderCreationTakesEffect() {
        val adapter = RecordingAdapter().apply { submitList(listOf("one")) }
        val recycler = layout(adapter)
        val holder = recycler.findViewHolderForAdapterPosition(0)!!
        var clickCount = 0

        adapter.setOnItemClickListener { _, _, _ -> clickCount++ }
        holder.itemView.performClick()

        assertEquals(1, clickCount)
    }

    @Test
    fun addingChildIdUpdatesExistingHolder() {
        val adapter = RecordingAdapter().apply { submitList(listOf("one")) }
        val clickedIds = mutableListOf<Int>()
        adapter.addChildClickViewIds(android.R.id.text1)
        adapter.setOnItemChildClickListener { _, view, _ -> clickedIds += view.id }
        val recycler = layout(adapter)
        val holder = recycler.findViewHolderForAdapterPosition(0)!!

        adapter.addChildClickViewIds(android.R.id.text2)
        holder.itemView.findViewById<View>(android.R.id.text1).performClick()
        holder.itemView.findViewById<View>(android.R.id.text2).performClick()

        assertEquals(listOf(android.R.id.text1, android.R.id.text2), clickedIds)
    }

    @Test
    fun unboundHolderIgnoresClicks() {
        val adapter = RecordingAdapter()
        var clicks = 0
        adapter.setOnItemClickListener { _, _, _ -> clicks++ }
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)

        holder.itemView.performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun singleAdapterNormalizesEmptyPayloadToFullBindCallback() {
        val adapter = RecordingAdapter().apply { submitList(listOf("one")) }
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)

        adapter.onBindViewHolder(holder, 0, mutableListOf())
        assertNull(adapter.payloads.single())
        adapter.onBindViewHolder(holder, 0, mutableListOf("changed"))
        assertEquals(listOf("changed"), adapter.payloads.last())
    }

    @Test
    fun multiAdapterNormalizesEmptyPayloadToFullBindCallback() {
        val adapter = MultiRecordingAdapter().apply { submitList(listOf("one")) }
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 7)

        adapter.onBindViewHolder(holder, 0, mutableListOf())
        assertNull(adapter.payloads.single())
        adapter.onBindViewHolder(holder, 0, mutableListOf("changed"))
        assertEquals(listOf("changed"), adapter.payloads.last())
    }

    private fun submitAndAwait(adapter: RecordingAdapter, items: List<String>?) {
        val committed = AtomicBoolean()
        adapter.submitList(items, Runnable { committed.set(true) })
        awaitCommit(committed)
    }

    private fun awaitCommit(committed: AtomicBoolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!committed.get() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(1)
        }
        assertTrue("List submission did not commit", committed.get())
    }

    private fun layout(adapter: RecyclerView.Adapter<*>): RecyclerView =
        RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
            measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
            )
            layout(0, 0, 400, 800)
        }

    class RecordingAdapter : SingleAdapter<String, TestBinding>(TestBinding::class.java, stringDiff) {
        val payloads = mutableListOf<List<Any>?>()

        override fun setData(
            holder: BaseViewHolder<TestBinding>,
            position: Int,
            item: String,
            payloads: List<Any>?
        ) {
            holder.binding.first.text = item
            this.payloads += payloads
        }
    }

    class MultiRecordingAdapter : MutableAdapter<String>({ _, _ -> 7 }, stringDiff) {
        val layouts = mutableMapOf<Int, Class<out ViewBinding>>(7 to TestBinding::class.java)
        val payloads = mutableListOf<List<Any>?>()

        override fun setLayouts(): MutableMap<Int, Class<out ViewBinding>> = layouts

        override fun setData(
            holder: BaseViewHolder<ViewBinding>,
            position: Int,
            item: String,
            payloads: List<Any>?
        ) {
            holder.withBinding<TestBinding> { first.text = item }
            this.payloads += payloads
        }
    }

    class TestBinding(private val view: LinearLayout) : ViewBinding {
        val first: TextView = view.findViewById(android.R.id.text1)
        override fun getRoot(): View = view

        companion object {
            @JvmStatic
            fun inflate(inflater: LayoutInflater, parent: ViewGroup?, attachToParent: Boolean): TestBinding {
                val root = LinearLayout(inflater.context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 80)
                    addView(TextView(context).apply { id = android.R.id.text1 })
                    addView(TextView(context).apply { id = android.R.id.text2 })
                }
                if (attachToParent) parent!!.addView(root)
                return TestBinding(root)
            }
        }
    }

    companion object {
        private val context get() = RuntimeEnvironment.getApplication()
        private val stringDiff = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String) = oldItem == newItem
            override fun areContentsTheSame(oldItem: String, newItem: String) = oldItem == newItem
        }
    }
}
