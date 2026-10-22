package com.android.common.ext

import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.android.common.base.adapter.AdapterBuilder
import com.android.common.base.adapter.BaseAdapter
import com.android.common.base.adapter.BaseViewHolder
import com.android.common.base.adapter.refresh.RefreshRecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
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
class RecyclerViewExtTest {
    @Test
    fun plainRecyclerDisplaysReplacementAndAppendedItems() {
        val recycler = RecyclerView(context).liner()
        val adapter: BaseAdapter<String> = recycler.buildAdapter { configureRows() }
        assertSame(adapter, recycler.adapter)

        submitAndAwait(recycler, listOf("one", "two"))
        layout(recycler)
        assertVisibleRows(recycler, listOf("one", "two"))

        appendAndAwait(recycler, listOf("three"))
        layout(recycler)
        assertVisibleRows(recycler, listOf("one", "two", "three"))

        submitAndAwait(recycler, listOf("replacement"))
        layout(recycler)
        assertEquals(1, recycler.adapter!!.itemCount)
        assertVisibleRows(recycler, listOf("replacement"))
    }

    @Test
    fun refreshRecyclerDisplaysRowsAndPreservesItsHeader() {
        val recycler = RefreshRecyclerView(context).liner()
        val wrapper = recycler.buildRefreshAdapter<String>(header = true) { configureRows() }
        val header = wrapper.adapters.first()

        submitAndAwait(recycler, listOf("one", "two"))
        layout(recycler)
        assertVisibleRows(recycler, listOf("one", "two"), headerCount = 1)

        appendAndAwait(recycler, listOf("three"))
        layout(recycler)
        assertVisibleRows(recycler, listOf("one", "two", "three"), headerCount = 1)
        assertSame(header, wrapper.adapters.first())
        assertEquals(4, wrapper.itemCount)
    }

    @Test
    fun refreshRecyclerWithoutHeaderUsesTheSameSubmissionApi() {
        val recycler = RefreshRecyclerView(context).liner()
        val wrapper = recycler.buildRefreshAdapter<String> { configureRows() }

        submitAndAwait(recycler, listOf("one"))
        layout(recycler)

        assertEquals(1, wrapper.adapters.size)
        assertVisibleRows(recycler, listOf("one"))
    }

    @Test
    fun nullAndUntypedEmptyListsClearTheContents() {
        val recycler = RecyclerView(context)
        recycler.buildAdapter<String> { configureRows() }
        submitAndAwait(recycler, listOf("one"))
        recycler.submitList(null)
        assertEquals(0, recycler.adapter!!.itemCount)
        submitAndAwait(recycler, listOf("two"))
        val committed = AtomicBoolean()

        recycler.submitList(emptyList(), Runnable { committed.set(true) })
        awaitCommit(committed)
        recycler.addItems(emptyList())

        assertEquals(0, recycler.adapter!!.itemCount)
    }

    @Test
    fun nestedConcatTargetsOnlyItsUniqueBaseAdapter() {
        val recycler = RecyclerView(context)
        val data = recycler.buildAdapter<String> { configureRows() }
        val firstHeader = HeaderAdapter()
        val secondHeader = HeaderAdapter()
        val wrapper = ConcatAdapter(firstHeader, ConcatAdapter(secondHeader, data))
        recycler.adapter = wrapper

        submitAndAwait(recycler, listOf("one", "two"))
        appendAndAwait(recycler, listOf("three"))

        assertEquals(listOf("one", "two", "three"), data.currentList)
        assertEquals(5, wrapper.itemCount)
        assertEquals(1, firstHeader.itemCount)
        assertEquals(1, secondHeader.itemCount)
    }

    @Test
    fun missingAdapterFailsClearlyForBothOperations() {
        val recycler = RecyclerView(context)

        val submitError = assertThrows(IllegalStateException::class.java) { recycler.submitList(listOf("one")) }
        val appendError = assertThrows(IllegalStateException::class.java) { recycler.addItems(listOf("one")) }

        assertTrue(submitError.message!!.contains("设置 Adapter"))
        assertTrue(appendError.message!!.contains("设置 Adapter"))
    }

    @Test
    fun plainAdapterAndNonBaseListAdapterAreRejected() {
        val recycler = RecyclerView(context)
        for (unsupported in listOf(HeaderAdapter(), PlainListAdapter())) {
            recycler.adapter = unsupported
            val submitError = assertThrows(IllegalStateException::class.java) { recycler.submitList(listOf("one")) }
            val appendError = assertThrows(IllegalStateException::class.java) { recycler.addItems(listOf("one")) }
            assertTrue(submitError.message!!.contains("未找到"))
            assertTrue(appendError.message!!.contains("未找到"))
        }
    }

    @Test
    fun multipleBaseAdaptersAreRejectedWithoutChangingAnyData() {
        val recycler = RecyclerView(context)
        val first = recycler.buildAdapter<String> { configureRows(); setList(listOf("first")) }
        val second = recycler.buildAdapter<String> { configureRows(); setList(listOf("second")) }
        recycler.adapter = ConcatAdapter(first, ConcatAdapter(HeaderAdapter(), second))

        val submitError = assertThrows(IllegalStateException::class.java) { recycler.submitList(listOf("replacement")) }
        val appendError = assertThrows(IllegalStateException::class.java) { recycler.addItems(listOf("appended")) }

        assertTrue(submitError.message!!.contains("多个 BaseAdapter"))
        assertTrue(appendError.message!!.contains("多个 BaseAdapter"))
        assertEquals(listOf("first"), first.currentList)
        assertEquals(listOf("second"), second.currentList)
    }

    private fun AdapterBuilder<String>.configureRows() {
        bind<RowBinding> { holder, _, item, _ -> holder.binding.text.text = item }
        setLayout(RowBinding::class.java)
        setDiff({ old, new -> old == new }, { old, new -> old == new })
    }

    private fun submitAndAwait(recycler: RecyclerView, items: List<String>) {
        val committed = AtomicBoolean()
        recycler.submitList(items, Runnable { committed.set(true) })
        awaitCommit(committed)
    }

    private fun appendAndAwait(recycler: RecyclerView, items: List<String>) {
        val committed = AtomicBoolean()
        recycler.addItems(items, Runnable { committed.set(true) })
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

    private fun layout(recycler: RecyclerView) {
        recycler.itemAnimator = null
        recycler.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
        )
        recycler.layout(0, 0, 400, 800)
    }

    private fun assertVisibleRows(recycler: RecyclerView, expected: List<String>, headerCount: Int = 0) {
        assertEquals(expected.size + headerCount, recycler.adapter!!.itemCount)
        expected.forEachIndexed { index, text ->
            val holder = recycler.findViewHolderForAdapterPosition(index + headerCount)
            assertTrue("Row $index must have a visible ViewHolder", holder != null)
            assertEquals(text, (holder!!.itemView as TextView).text.toString())
        }
    }

    class RowBinding(val text: TextView) : ViewBinding {
        override fun getRoot(): View = text

        companion object {
            @JvmStatic
            fun inflate(inflater: LayoutInflater, parent: ViewGroup?, attachToParent: Boolean): RowBinding {
                val text = TextView(inflater.context).apply {
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 80)
                }
                if (attachToParent) parent!!.addView(text)
                return RowBinding(text)
            }
        }
    }

    class HeaderAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            object : RecyclerView.ViewHolder(View(parent.context)) {}
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
        override fun getItemCount() = 1
    }

    class PlainListAdapter : ListAdapter<String, BaseViewHolder<ViewBinding>>(stringDiff) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder<ViewBinding> =
            BaseViewHolder(RowBinding(TextView(parent.context)))
        override fun onBindViewHolder(holder: BaseViewHolder<ViewBinding>, position: Int) = Unit
    }

    companion object {
        private val context get() = RuntimeEnvironment.getApplication()
        private val stringDiff = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String) = oldItem == newItem
            override fun areContentsTheSame(oldItem: String, newItem: String) = oldItem == newItem
        }
    }
}
