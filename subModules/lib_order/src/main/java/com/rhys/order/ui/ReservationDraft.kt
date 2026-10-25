package com.rhys.order.ui

import com.example.network.bean.CreateOrderItem

/**
 * 预订确认弹窗的纯逻辑草稿。
 *
 * 只维护“菜谱 ID -> 数量”和整单备注，不访问网络、数据库或 Android 控件；
 * 提交前按菜谱 ID 合并重复项并排序，数量始终为正整数且合并/自增不溢出。
 * 服务端仍会再次合并、校验好友关系与菜谱可预订性，本类不能替代权限控制。
 */
class ReservationDraft(
    initialQuantities: Map<Long, Int> = emptyMap(),
    initialNote: String? = null
) {

    private val quantities = LinkedHashMap<Long, Int>()

    /** 当前备注原文；长度不超过 [MAX_NOTE_LENGTH]，提交时再 trim。 */
    var note: String = initialNote.orEmpty().take(MAX_NOTE_LENGTH)
        private set

    init {
        initialQuantities.forEach { (recipeId, quantity) ->
            if (recipeId > 0L && quantity > 0) {
                quantities[recipeId] = quantity
            }
        }
    }

    /** 选中的菜谱数量，去重后计数。 */
    val selectedCount: Int
        get() = quantities.size

    /** 选中菜的总份数；使用 Long 避免多道菜累加溢出。 */
    val totalQuantity: Long
        get() = quantities.values.sumOf { it.toLong() }

    /** 至少一道菜才可提交。 */
    val canSubmit: Boolean
        get() = quantities.isNotEmpty()

    fun isSelected(recipeId: Long): Boolean = quantities.containsKey(recipeId)

    /** 未选中返回 0；选中至少返回 1。 */
    fun quantityOf(recipeId: Long): Int = quantities[recipeId] ?: 0

    /**
     * 选中菜谱并累加数量；重复调用等价于重复菜品合并。
     *
     * @return 是否发生变化；菜谱 ID 非法、数量非正或合并后溢出时返回 false。
     */
    fun select(recipeId: Long, quantity: Int = 1): Boolean {
        if (recipeId <= 0L || quantity <= 0) return false
        val current = quantities[recipeId] ?: 0
        val merged = current.toLong() + quantity.toLong()
        if (merged > Int.MAX_VALUE.toLong()) return false
        quantities[recipeId] = merged.toInt()
        return true
    }

    /** 删除一道菜；不存在时返回 false。 */
    fun remove(recipeId: Long): Boolean = quantities.remove(recipeId) != null

    /** 数量 +1，达到 Int 上限时不再变化。 */
    fun increment(recipeId: Long): Boolean {
        if (!quantities.containsKey(recipeId)) return false
        return select(recipeId, 1)
    }

    /** 数量 -1，最小保持 1；未选中或已为 1 时返回 false。 */
    fun decrement(recipeId: Long): Boolean {
        val current = quantities[recipeId] ?: return false
        if (current <= 1) return false
        quantities[recipeId] = current - 1
        return true
    }

    /** 直接设置数量；只接受正整数，未选中时返回 false。 */
    fun setQuantity(recipeId: Long, quantity: Int): Boolean {
        if (quantity <= 0) return false
        if (!quantities.containsKey(recipeId)) return false
        quantities[recipeId] = quantity
        return true
    }

    /** 记录备注；超过 500 字截断，保持与输入框 maxLength 一致。 */
    fun setNote(value: CharSequence?) {
        note = value?.toString().orEmpty().take(MAX_NOTE_LENGTH)
    }

    /**
     * 提交用备注：trim 后为空返回 null，最多 500 字。
     */
    fun normalizedNote(): String? = normalizeNote(note)

    /**
     * 提交用明细：按菜谱 ID 升序，数量为正整数。
     * 空草稿返回空列表，由调用方阻止提交。
     */
    fun toSubmitItems(): List<CreateOrderItem> {
        return quantities.entries
            .asSequence()
            .filter { it.key > 0L && it.value > 0 }
            .sortedBy { it.key }
            .map { CreateOrderItem(recipeId = it.key, quantity = it.value) }
            .toList()
    }

    companion object {
        const val MAX_NOTE_LENGTH = 500

        /** trim 后为空返回 null；超长按 500 字截断。 */
        fun normalizeNote(raw: String?): String? {
            val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return trimmed.take(MAX_NOTE_LENGTH)
        }
    }
}
