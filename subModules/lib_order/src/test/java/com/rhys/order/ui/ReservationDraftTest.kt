package com.rhys.order.ui

import com.example.network.bean.CreateOrderItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReservationDraftTest {

    @Test
    fun `empty draft cannot submit`() {
        val draft = ReservationDraft()

        assertFalse(draft.canSubmit)
        assertEquals(0, draft.selectedCount)
        assertEquals(0L, draft.totalQuantity)
        assertTrue(draft.toSubmitItems().isEmpty())
    }

    @Test
    fun `selected dishes are submitted sorted by recipe id`() {
        val draft = ReservationDraft()
        draft.select(30L)
        draft.select(10L)
        draft.select(20L)

        assertEquals(
            listOf(
                CreateOrderItem(10L, 1),
                CreateOrderItem(20L, 1),
                CreateOrderItem(30L, 1)
            ),
            draft.toSubmitItems()
        )
        assertEquals(3, draft.selectedCount)
        assertEquals(3L, draft.totalQuantity)
        assertTrue(draft.canSubmit)
    }

    @Test
    fun `duplicate selection merges quantity`() {
        val draft = ReservationDraft()

        assertTrue(draft.select(10L, 2))
        assertTrue(draft.select(10L, 3))

        assertEquals(1, draft.selectedCount)
        assertEquals(5, draft.quantityOf(10L))
        assertEquals(listOf(CreateOrderItem(10L, 5)), draft.toSubmitItems())
    }

    @Test
    fun `quantity increment and decrement keep positive integers`() {
        val draft = ReservationDraft()
        draft.select(10L)

        assertTrue(draft.increment(10L))
        assertEquals(2, draft.quantityOf(10L))
        assertTrue(draft.decrement(10L))
        assertEquals(1, draft.quantityOf(10L))
        assertFalse(draft.decrement(10L))
        assertEquals(1, draft.quantityOf(10L))
    }

    @Test
    fun `merged quantity cannot overflow int`() {
        val draft = ReservationDraft()
        assertTrue(draft.select(10L, Int.MAX_VALUE))

        assertFalse(draft.select(10L, 1))
        assertEquals(Int.MAX_VALUE, draft.quantityOf(10L))
        assertFalse(draft.increment(10L))
        assertEquals(Int.MAX_VALUE, draft.quantityOf(10L))
    }

    @Test
    fun `set quantity rejects non positive values`() {
        val draft = ReservationDraft()
        draft.select(10L, 2)

        assertFalse(draft.setQuantity(10L, 0))
        assertFalse(draft.setQuantity(10L, -3))
        assertEquals(2, draft.quantityOf(10L))
        assertFalse(draft.setQuantity(99L, 3))
    }

    @Test
    fun `removing a dish drops it from submission`() {
        val draft = ReservationDraft()
        draft.select(10L)
        draft.select(20L)

        assertTrue(draft.remove(10L))
        assertFalse(draft.remove(10L))
        assertEquals(listOf(CreateOrderItem(20L, 1)), draft.toSubmitItems())
    }

    @Test
    fun `invalid recipe id is rejected`() {
        val draft = ReservationDraft()

        assertFalse(draft.select(0L))
        assertFalse(draft.select(-1L))
        assertFalse(draft.select(10L, 0))
        assertFalse(draft.isSelected(10L))
    }

    @Test
    fun `note is trimmed and blank becomes null`() {
        val draft = ReservationDraft()
        draft.setNote("  少放辣  ")

        assertEquals("  少放辣  ", draft.note)
        assertEquals("少放辣", draft.normalizedNote())

        draft.setNote("   ")
        assertNull(draft.normalizedNote())
    }

    @Test
    fun `note longer than limit is truncated`() {
        val draft = ReservationDraft()
        val longNote = "菜".repeat(ReservationDraft.MAX_NOTE_LENGTH + 20)

        draft.setNote(longNote)

        assertEquals(ReservationDraft.MAX_NOTE_LENGTH, draft.note.length)
        assertEquals(
            ReservationDraft.MAX_NOTE_LENGTH,
            draft.normalizedNote()?.length
        )
        assertEquals(
            ReservationDraft.MAX_NOTE_LENGTH,
            ReservationDraft.normalizeNote(longNote)?.length
        )
    }

    @Test
    fun `initial quantities and note are kept`() {
        val draft = ReservationDraft(
            initialQuantities = mapOf(10L to 2, 20L to 1),
            initialNote = " 不要香菜 "
        )

        assertEquals(3L, draft.totalQuantity)
        assertEquals("不要香菜", draft.normalizedNote())
        assertEquals(
            listOf(CreateOrderItem(10L, 2), CreateOrderItem(20L, 1)),
            draft.toSubmitItems()
        )
    }
}
