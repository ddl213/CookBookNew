package com.rhys.chat.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.android.common.helper.AppHelper

/**
 * 加密聊天本地缓存数据库。
 *
 * 使用 Room version 1，`exportSchema = false`。
 * 明确约束：后续任何 schema 变更必须新增 Migration（不得使用破坏性迁移）；
 * 一旦丢失旧表结构，已加密聊天记录将无法恢复。
 */
@Database(
    entities = [
        ChatConversationEntity::class,
        ChatMessageEntity::class,
        ChatNotificationEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ChatDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatDao

    companion object {
        const val DATABASE_NAME = "cookbook_chat.db"

        /** 生产数据库（磁盘持久化）。 */
        fun build(context: Context): ChatDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ChatDatabase::class.java,
                DATABASE_NAME
            ).build()

        /** 单元测试使用的 in-memory 数据库，不写磁盘。 */
        fun buildInMemory(context: Context): ChatDatabase =
            Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                ChatDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
    }
}

/**
 * 生产数据库单例。
 *
 * 首次访问时基于 `AppHelper.application` 创建；应用启动前不会初始化。
 * 聊天缓存只由 [com.rhys.chat.data.ChatRepository] 与
 * [com.rhys.chat.data.session.ChatSessionGuard] 使用。
 */
object ChatDatabaseProvider {

    @Volatile
    private var instance: ChatDatabase? = null

    fun get(): ChatDatabase = get(AppHelper.application)

    fun get(context: Context): ChatDatabase {
        return instance ?: synchronized(this) {
            instance ?: ChatDatabase.build(context).also { instance = it }
        }
    }

    /** 仅供本地测试清理；正式代码不应调用。 */
    internal fun resetForTests() {
        synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}
