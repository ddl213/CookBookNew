package com.android.common.utils

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * 全局日志工具类。
 * 提供了不同级别的日志方法 (v, d, i, w, e)，支持自定义 Tag，
 * 自动获取调用者信息，以及全局日志开关和级别控制。
 */
object LogUtils { // 使用 object 关键字创建单例对象
    // 日志级别常量
    const val VERBOSE = Log.VERBOSE // 2
    const val DEBUG = Log.DEBUG   // 3
    const val INFO = Log.INFO     // 4
    const val WARN = Log.WARN     // 5
    const val ERROR = Log.ERROR   // 6
    const val ASSERT = Log.ASSERT // 7
    const val NOTHING = 8         // 不打印任何日志

    // 日志限制常量
    const val TAG_LIMIT = 0x00000001
    const val COUNT_LIMIT = 0x00000010
    const val SEPARATOR_LIMIT = 0x00000100
    const val BOTH_LIMIT = TAG_LIMIT or COUNT_LIMIT
    const val NO_LIMIT = 0x00000000

    // ==================== 配置相关 ====================
    @Volatile
    private var baseTag: String = "myLog"

    @Volatile
    private var logLevel: Int = DEBUG

    @Volatile
    private var limit: Int = NO_LIMIT

    @Volatile
    private var limitCount: Int = 10

    @Volatile
    private var separatorFormat: String = System.lineSeparator()

    // ==================== 状态管理 ====================
    private data class TagState(
        var lastTag: String? = null,
        var currentLimitCount: Int = 0,
        var separatorLimitCount: Int = 0
    )

    private val tagStates = ConcurrentHashMap<Thread, TagState>()
    private val tagCache: MutableMap<Int, String> = ConcurrentHashMap()

    @Volatile
    private var tagFilter: MutableSet<String>? = null

    @Volatile
    private var addCurrentTag: Boolean = false

    // ==================== 公共方法 ====================

    // Verbose
    @JvmStatic
    fun v(format: String, vararg args: Any?) {
        logIfEnabled(VERBOSE, format.format(*args))
    }

    @JvmStatic
    fun v(msg: String, tag: String? = null) {
        logIfEnabled(VERBOSE, msg, tag)
    }

    // Debug
    @JvmStatic
    fun d(msg: String, tag: String? = null) {
        logIfEnabled(DEBUG, msg, tag)
    }

    @JvmStatic
    fun d(vararg args: Any?,format: String) {
        logIfEnabled(DEBUG, format.format(*args))
    }

    @JvmStatic
    fun d(vararg args: Any?) {
        val message = args.joinToString(" ::: ")
        logIfEnabled(DEBUG, message)
    }

    // Info
    @JvmStatic
    fun i(msg: String, tag: String? = null) {
        logIfEnabled(INFO, msg, tag)
    }

    @JvmStatic
    fun i(format: String, vararg args: Any?) {
        logIfEnabled(INFO, format.format(*args))
    }

    // Warn
    @JvmStatic
    fun w(msg: String, tag: String? = null) {
        logIfEnabled(WARN, msg, tag)
    }

    @JvmStatic
    fun w(format: String, vararg args: Any?) {
        logIfEnabled(WARN, format.format(*args))
    }

    // Error
    @JvmStatic
    fun e(tr: Throwable?) {
        logIfEnabled(ERROR, "", throwable = tr)
    }

    @JvmStatic
    fun e(vararg args: Any?) {
        val message = args.joinToString(" ::: ")
        logIfEnabled(ERROR, message)
    }

    @JvmStatic
    fun e(tr: Throwable? = null,msg: String, tag: String? = null) {
        logIfEnabled(ERROR, msg, tag, tr)
    }

    // ==================== 核心日志方法 ====================
    private fun logIfEnabled(level: Int, message: String, tag: String? = null,
                             throwable: Throwable? = null, format: String = "%s:%d") {
        // 检查日志级别
        if (logLevel > level) return

        // 获取线程状态和生成最终tag
        val state = getThreadState()
        val (finalTag, callerInfo) = generateFinalTag(tag, format)

        // 检查标签过滤
        if (!shouldLogWithTag(finalTag)) return

        // 应用日志限制
        if (!applyLogLimits(state, finalTag, callerInfo)) return

        // 构建并打印日志
        val finalMessage = buildLogMessage(callerInfo, message)
        printLog(level, finalTag, finalMessage, throwable)
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取线程状态（线程安全）
     */
    private fun getThreadState(): TagState {
        val thread = Thread.currentThread()
        return tagStates.computeIfAbsent(thread) { TagState() }
    }

    /**
     * 生成最终的Tag和调用者信息
     */
    private fun generateFinalTag(customTag: String?, format: String): Pair<String, String> {
        return if (!customTag.isNullOrBlank()) {
            customTag to ""
        } else {
            val callerInfo = getCallerInfo(format)
            val callerInfoHashCode = callerInfo.hashCode()
            val tag = tagCache.computeIfAbsent(callerInfoHashCode) {
                "$baseTag:${callerInfo.first}"
            }
            tag to callerInfo.second
        }
    }

    /**
     * 获取调用者信息
     */
    private fun getCallerInfo(format: String): Pair<String, String> {
        val stackTrace = Thread.currentThread().stackTrace

        // 从堆栈中找到第一个非日志工具类的调用者
        for (i in 4 until stackTrace.size) {
            val element = stackTrace[i]
            val className = element.className

            Log.e("mLogUtils", "第 $i 个className: $className")

            if (className != LogUtils::class.java.name &&
                !className.startsWith("java.") &&
                !className.startsWith("android.") &&
                !className.startsWith("dalvik.") &&
                !className.startsWith("androidx.")
            ) {
                val simpleClassName = getSimpleClassName(className)
                val methodName = if (element.methodName == "invokeSuspend") {
                    className.split('$')[1]
                } else {
                    element.methodName
                }

                Log.e("mLogUtils", "最终下标$i className: $simpleClassName, methodName: $methodName, " +
                        "fileName: ${element.fileName}, lineNumber: ${element.lineNumber}")
                return String.format("%s.%s", simpleClassName, methodName) to
                        String.format(format, element.fileName, element.lineNumber)
            }
        }

        return "UnknownCaller" to ""
    }

    /**
     * 提取简单类名
     */
    private fun getSimpleClassName(fullClassName: String): String {
        val lastDot = fullClassName.lastIndexOf('.')
        var simpleName = if (lastDot == -1) fullClassName else fullClassName.substring(lastDot + 1)

        // 处理内部类
        val dollar = simpleName.indexOf('$')
        if (dollar != -1) {
            simpleName = simpleName.substring(0, dollar)
        }

        return simpleName
    }

    /**
     * 检查是否应该根据tag过滤打印日志
     */
    private fun shouldLogWithTag(tag: String): Boolean {
        val filter = tagFilter ?: return true

        // 如果正在添加当前tag
        if (addCurrentTag) {
            tagFilter?.add(tag)
            addCurrentTag = false
            return true
        }

        return tag in filter
    }

    /**
     * 应用日志限制规则
     * @return true表示应该打印日志，false表示跳过
     */
    private fun applyLogLimits(state: TagState, finalTag: String, callerInfo: String): Boolean {
        // 处理TAG_LIMIT（标签变化分隔符）
        if ((limit and TAG_LIMIT) == TAG_LIMIT) {
            handleTagLimit(state, finalTag)
        }

        // 处理COUNT_LIMIT（计数限制）
        if ((limit and COUNT_LIMIT) == COUNT_LIMIT) {
            if (!handleCountLimit(state, finalTag)) {
                return false
            }
        }

        // 处理SEPARATOR_LIMIT（分隔符限制）
        if ((limit and SEPARATOR_LIMIT) == SEPARATOR_LIMIT) {
            handleSeparatorLimit(state, finalTag)
        }

        return true
    }

    /**
     * 处理标签限制（标签变化时添加分隔符）
     */
    private fun handleTagLimit(state: TagState, finalTag: String) {
        if (state.lastTag != finalTag) {
            if (state.lastTag != null) {
                Log.i(baseTag, separatorFormat)
            }
            state.lastTag = finalTag
        }
    }

    /**
     * 处理计数限制
     * @return true表示可以打印，false表示跳过
     */
    private fun handleCountLimit(state: TagState, finalTag: String): Boolean {
        if (state.lastTag == finalTag) {
            if (state.currentLimitCount < limitCount) {
                state.currentLimitCount++
                return false
            } else {
                state.currentLimitCount = 0
            }
        }
        return true
    }

    /**
     * 处理分隔符限制
     */
    private fun handleSeparatorLimit(state: TagState, finalTag: String) {
        if (state.separatorLimitCount < limitCount) {
            state.separatorLimitCount++
        } else {
            Log.i(baseTag, separatorFormat)
            state.separatorLimitCount = 0
        }
    }

    /**
     * 构建日志消息
     */
    private fun buildLogMessage(callerInfo: String, message: String): String {
        return if (callerInfo.isNotEmpty()) {
            "$callerInfo：$message"
        } else {
            message
        }
    }

    /**
     * 实际打印日志
     */
    private fun printLog(level: Int, tag: String, message: String, throwable: Throwable?) {
        when (level) {
            VERBOSE -> Log.v(tag, message, throwable)
            DEBUG -> Log.d(tag, message, throwable)
            INFO -> Log.i(tag, message, throwable)
            WARN -> Log.w(tag, message, throwable)
            ERROR -> Log.e(tag, message, throwable)
        }
    }

    // ==================== 配置方法 ====================

    /**
     * 设置基础标签
     */
    fun setBaseTag(newTag: String) {
        if (newTag.isNotBlank()) {
            this.baseTag = newTag
            clearCache()
        }
    }

    /**
     * 设置日志级别
     */
    fun setLogLevel(level: Int) {
        if (level >= VERBOSE && level <= NOTHING) {
            this.logLevel = level
            Log.d(baseTag, "日志级别已设置为: ${getLevelName(level)}")
        } else {
            Log.e(baseTag, "设置的日志级别无效: $level")
        }
    }

    /**
     * 设置日志限制
     */
    fun setLimit(limit: Int, count: Int = 10) {
        this.limit = limit
        this.limitCount = count
    }

    /**
     * 清除日志限制
     */
    fun cleanLimit(clearLimit: Int = NO_LIMIT) {
        this.limit = this.limit and clearLimit.inv()
        this.limitCount = 10
    }

    /**
     * 设置标签分隔符格式
     */
    fun setTagSeparatorFormat(separatorFormat: String) {
        this.separatorFormat = separatorFormat
    }

    /**
     * 设置标签过滤器（排除指定标签）
     */
    fun setFilterWithOutTag(tag: String) {
        val filter = tagFilter ?: HashSet()
        filter.add(tag)
        tagFilter = filter
    }

    /**
     * 移除标签过滤器
     */
    fun removeTagFilter(tag: String) {
        tagFilter?.remove(tag)
    }

    /**
     * 清空标签过滤器
     */
    fun clearTagFilter() {
        tagFilter = null
    }

    /**
     * 添加当前标签到过滤器
     */
    fun addCurrentTagInFilter() {
        addCurrentTag = true
    }

    /**
     * 清除缓存
     */
    fun clearCache() {
        tagCache.clear()
        tagStates.clear()
    }

    /**
     * 获取日志级别名称
     */
    private fun getLevelName(level: Int): String {
        return when (level) {
            VERBOSE -> "VERBOSE"
            DEBUG -> "DEBUG"
            INFO -> "INFO"
            WARN -> "WARN"
            ERROR -> "ERROR"
            ASSERT -> "ASSERT"
            NOTHING -> "NOTHING"
            else -> "UNKNOWN"
        }
    }
}