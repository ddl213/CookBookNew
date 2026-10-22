package com.android.common.base.adapter

import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.android.common.base.adapter.refresh.RefreshRecyclerView
import org.junit.Assert.assertEquals
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
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class AdapterBuilderTest {
    @Test
    fun bindMayPrecedeConfigurationAndUsesFinalDiff() {
        val comparisons = AtomicInteger()
        val builder = AdapterBuilder<String>().apply {
            bind<TestBinding> { holder, _, item, _ -> holder.binding.first.text = item }
            setLayout(OtherBinding::class.java)
            setLayout(TestBinding::class.java)
            setDiff({ _, _ -> error("The earlier diff must not be retained") }, { _, _ -> false })
            setDiff({ old, new -> comparisons.incrementAndGet(); old == new }, { old, new -> old == new })
            setList(listOf("old"))
        }

        val adapter = builder.build()
        submitAndAwait(adapter, listOf("new"))
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        adapter.bindViewHolder(holder, 0)

        assertTrue(comparisons.get() > 0)
        assertEquals("new", (holder.binding as TestBinding).first.text.toString())
    }

    @Test
    fun singleLayoutMismatchReportsBothBindingTypesAtBuildTime() {
        val builder = singleBuilder().apply { setLayout(OtherBinding::class.java) }

        val error = assertThrows(IllegalStateException::class.java) { builder.build() }

        assertTrue(error.message!!.contains(TestBinding::class.java.name))
        assertTrue(error.message!!.contains(OtherBinding::class.java.name))
    }

    @Test
    fun missingRequiredConfigurationFailsAtBuildTime() {
        val builder = AdapterBuilder<String>()
        assertBuildFailure(builder, "bind")
        builder.bind<TestBinding> { _, _, _, _ -> }
        assertBuildFailure(builder, "setDiff")
        builder.setDiff({ old, new -> old == new }, { old, new -> old == new })
        assertBuildFailure(builder, "setLayout")
        builder.bindMulti { _, _, _, _ -> }
        assertBuildFailure(builder, "setLayout")
        builder.setLayout(10, TestBinding::class.java)
        assertBuildFailure(builder, "setViewTypeDelegate")
    }

    @Test
    fun bindMultiUsesFinalConfigurationAndSnapshotsLayouts() {
        val builder = AdapterBuilder<String>().apply {
            bindMulti { _, _, _, _ -> }
            setLayout(10, TestBinding::class.java)
            setLayout(10, OtherBinding::class.java)
            setViewTypeDelegate { _, _ -> 1 }
            setViewTypeDelegate { _, _ -> 10 }
            setDiff({ old, new -> old == new }, { old, new -> old == new })
            setList(listOf("one"))
        }
        val adapter = builder.build()
        builder.setLayout(10, TestBinding::class.java)
        builder.setViewTypeDelegate { _, _ -> 99 }

        assertEquals(10, adapter.getItemViewType(0))
        assertTrue(adapter.onCreateViewHolder(FrameLayout(context), 10).binding is OtherBinding)
    }

    @Test
    fun setListCopiesTheInitialSnapshotBeforeBuild() {
        val source = mutableListOf("one", "two")
        val builder = singleBuilder().setList(source)
        source.clear()

        val adapter = builder.build()
        builder.setList(listOf("next build only"))

        assertEquals(listOf("one", "two"), adapter.currentList)
        assertEquals(listOf("next build only"), builder.build().currentList)
    }

    @Test
    fun repeatedChildClickConfigurationAccumulatesIds() {
        val adapter = singleBuilder().apply {
            addChildClickViewIds(android.R.id.text1)
            addChildClickViewIds(android.R.id.text2, android.R.id.text1)
            setOnItemChildClickListener { _, _, _ -> }
            setList(listOf("one"))
        }.build()
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        adapter.bindViewHolder(holder, 0)

        assertTrue(holder.itemView.findViewById<View>(android.R.id.text1).hasOnClickListeners())
        assertTrue(holder.itemView.findViewById<View>(android.R.id.text2).hasOnClickListeners())
    }

    @Test
    fun nullablePayloadRequestsAFullItemChange() {
        var payloadRequested = false
        val adapter = singleBuilder().apply {
            setDiff({ _, _ -> true }, { old, new -> old == new }, { _, _ ->
                payloadRequested = true
                null
            })
            setList(listOf("old"))
        }.build()
        val payloads = mutableListOf<Any?>()
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                payloads.add(payload)
            }
        })

        submitAndAwait(adapter, listOf("new"))

        assertTrue(payloadRequested)
        assertEquals(listOf<Any?>(null), payloads)
    }

    @Test
    fun footerFailsExplicitlyBeforeBuildingAnAdapter() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            AdapterBuilder<String>().buildRefresh(RefreshRecyclerView(context), header = false, footer = true)
        }

        assertTrue(error.message!!.contains("footer"))
    }

    private fun singleBuilder() = AdapterBuilder<String>().apply {
        setDiff({ old, new -> old == new }, { old, new -> old == new })
        setLayout(TestBinding::class.java)
        bind<TestBinding> { _, _, _, _ -> }
    }

    private fun assertBuildFailure(builder: AdapterBuilder<String>, expectedMessage: String) {
        val error = assertThrows(IllegalStateException::class.java) { builder.build() }
        assertTrue(error.message!!.contains(expectedMessage))
    }

    private fun submitAndAwait(adapter: BaseAdapter<String>, items: List<String>) {
        val committed = AtomicBoolean()
        adapter.submitList(items, Runnable { committed.set(true) })
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!committed.get() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(1)
        }
        assertTrue("List submission did not commit", committed.get())
    }

    class TestBinding(private val view: LinearLayout) : ViewBinding {
        val first: TextView = view.findViewById(android.R.id.text1)
        override fun getRoot(): View = view

        companion object {
            @JvmStatic
            fun inflate(inflater: LayoutInflater, parent: ViewGroup?, attachToParent: Boolean): TestBinding {
                val root = LinearLayout(inflater.context).apply {
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 80)
                    addView(TextView(context).apply { id = android.R.id.text1 })
                    addView(TextView(context).apply { id = android.R.id.text2 })
                }
                if (attachToParent) parent!!.addView(root)
                return TestBinding(root)
            }
        }
    }

    class OtherBinding(private val view: View) : ViewBinding {
        override fun getRoot(): View = view

        companion object {
            @JvmStatic
            fun inflate(inflater: LayoutInflater, parent: ViewGroup?, attachToParent: Boolean): OtherBinding {
                val root = View(inflater.context)
                if (attachToParent) parent!!.addView(root)
                return OtherBinding(root)
            }
        }
    }

    companion object {
        private val context get() = RuntimeEnvironment.getApplication()
    }
}
