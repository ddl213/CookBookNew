package com.rhys.chat.data

import com.example.network.AuthException
import com.example.network.ChatErrorMapper
import com.example.network.bean.ChatMessageDto
import com.example.network.bean.CursorPage
import com.rhys.chat.data.crypto.ChatCipher
import com.rhys.chat.data.crypto.ChatCipherException
import com.rhys.chat.data.crypto.ChatDecryption
import com.rhys.chat.data.crypto.KeystoreChatCipher
import com.rhys.chat.data.local.ChatDao
import com.rhys.chat.data.local.ChatDatabase
import com.rhys.chat.data.local.ChatDatabaseProvider
import com.rhys.chat.data.local.ChatEntityMapper
import com.rhys.chat.data.local.ChatMessageEntity
import com.rhys.chat.domain.ChatConversation
import com.rhys.chat.domain.ChatMessage
import com.rhys.chat.domain.ChatMessageStatus
import com.rhys.chat.domain.ChatNotification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 聊天数据访问层：远端/Mock 数据源 + 加密 Room 本地缓存。
 *
 * 关键不变量：
 * - 只有登录用户可读写缓存；未登录时观察流为空、写操作抛 `UNAUTHORIZED`。
 * - 所有用户可见文本加密后落盘；解密失败仅展示安全占位。
 * - 发送先落本地 SENDING，成功以同一 `clientMessageId` 更新为 SENT，
 *   失败标记 FAILED 并抛出可展示异常，绝不新增第二条消息。
 * - 远端拉取按 `clientMessageId` upsert，保留本地 SENDING/FAILED 状态，
 *   历史分页不删除已缓存内容。
 *
 * 构造参数默认面向生产；测试可注入 in-memory 数据库、JVM AES Cipher、
 * 固定/可变 owner 与自定义时钟。
 */
class ChatRepository(
    private val dataSource: ChatDataSource = ChatDataSourceConfig.create(),
    private val realtimeSource: ChatRealtimeSource = UnavailableChatRealtimeSource(),
    private val databaseProvider: () -> ChatDatabase = { ChatDatabaseProvider.get() },
    private val cipher: ChatCipher = KeystoreChatCipher(),
    private val ownerProvider: ChatOwnerProvider = SessionChatOwnerProvider,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val clientMessageIdFactory: () -> String = { UUID.randomUUID().toString() }
) {

    private val mapper = ChatEntityMapper(cipher)

    /** 当前数据源是否为本地演示模式；UI 必须据此展示演示标识。 */
    val isMock: Boolean
        get() = dataSource.isMock

    /** 实时消息扩展点；后端未就绪时为不可用实现。 */
    val realtime: ChatRealtimeSource
        get() = realtimeSource

    fun observeConversations(): Flow<List<ChatConversation>> = ownerScopedList { owner ->
        databaseProvider().chatDao().observeConversations(owner)
            .map { rows -> rows.map(mapper::conversationToDomain) }
    }

    fun observeMessages(conversationId: Long): Flow<List<ChatMessage>> {
        require(conversationId > 0) { "conversationId must be positive" }
        return ownerScopedList { owner ->
            databaseProvider().chatDao().observeMessages(owner, conversationId)
                .map { rows -> rows.map(mapper::messageToDomain) }
        }
    }

    fun observeNotifications(): Flow<List<ChatNotification>> = ownerScopedList { owner ->
        databaseProvider().chatDao().observeNotifications(owner)
            .map { rows -> rows.map(mapper::notificationToDomain) }
    }

    /** 拉取会话列表并 upsert；返回本次数据源的页结果。 */
    suspend fun refreshConversations(cursor: String? = null): CursorPage<ChatConversation> {
        val owner = requireOwner()
        val page = dataSource.listConversations(
            cursor = ChatDataSource.normalizedCursor(cursor)
        )
        val items = withContext(ioDispatcher) {
            ensureOwner(owner)
            val dao = databaseProvider().chatDao()
            page.items.map { dto ->
                // 服务端可能返回不完整会话；缺省字段保留本地已有值。
                val existing = dao.findConversation(owner, dto.id)
                val entity = mapper.conversationToEntity(owner, dto, existing)
                dao.upsertConversation(entity)
                mapper.conversationToDomain(entity)
            }
        }
        return CursorPage(items = items, nextCursor = page.nextCursor)
    }

    /** 打开/创建与好友的会话；结果写入本地缓存后返回。 */
    suspend fun openConversation(peerUserId: Long): ChatConversation {
        require(peerUserId > 0) { "peerUserId must be positive" }
        val owner = requireOwner()
        val dto = dataSource.openConversation(peerUserId)
        return withContext(ioDispatcher) {
            ensureOwner(owner)
            val dao = databaseProvider().chatDao()
            val existing = dao.findConversation(owner, dto.id)
            val entity = mapper.conversationToEntity(owner, dto, existing)
            dao.upsertConversation(entity)
            mapper.conversationToDomain(entity)
        }
    }

    /**
     * 拉取历史消息并 upsert。
     *
     * 同一条远端消息（按 clientMessageId，缺失时用 `server:<id>`）不会重复入库；
     * 本地 SENDING/FAILED 记录保留原状态与正文，只补充 serverId 等字段。
     */
    suspend fun refreshMessages(
        conversationId: Long,
        before: String? = null
    ): CursorPage<ChatMessage> {
        require(conversationId > 0) { "conversationId must be positive" }
        val owner = requireOwner()
        val page = dataSource.listMessages(
            conversationId = conversationId,
            before = ChatDataSource.normalizedCursor(before)
        )
        val items = withContext(ioDispatcher) {
            ensureOwner(owner)
            val dao = databaseProvider().chatDao()
            page.items.map { dto -> upsertRemoteMessage(dao, owner, dto) }
        }
        return CursorPage(items = items, nextCursor = page.nextCursor)
    }

    /**
     * 发送文字消息。
     *
     * 先生成 UUID `clientMessageId` 并加密写入本地 SENDING，再调用数据源；
     * 成功用同一主键更新为 SENT，失败标记 FAILED 并抛可展示异常。
     */
    suspend fun sendMessage(conversationId: Long, content: String): ChatMessage {
        require(conversationId > 0) { "conversationId must be positive" }
        val owner = requireOwner()
        val safeContent = ChatDataSource.normalizedContent(content)
        val clientMessageId = ChatDataSource.normalizedClientMessageId(
            clientMessageIdFactory()
        )
        val now = clock()
        val pending = ChatMessage(
            serverId = null,
            clientMessageId = clientMessageId,
            conversationId = conversationId,
            senderId = owner,
            receiverId = null,
            content = safeContent,
            createdAt = now,
            createdAtRaw = ChatTime.formatIso(now),
            status = ChatMessageStatus.SENDING
        )
        val entity = withContext(ioDispatcher) {
            ensureOwner(owner)
            mapper.messageToEntity(owner, pending).also {
                databaseProvider().chatDao().upsertMessage(it)
            }
        }
        return deliver(entity, safeContent)
    }

    /**
     * 重试失败/发送中的消息。
     *
     * 复用原 `clientMessageId` 与已加密正文；成功后原记录变为 SENT，不新增第二条。
     */
    suspend fun retryMessage(clientMessageId: String): ChatMessage {
        val owner = requireOwner()
        val safeClientMessageId =
            ChatDataSource.normalizedClientMessageId(clientMessageId)
        val row = withContext(ioDispatcher) {
            // 读取前复核 owner，避免账号切换窗口内读到旧账号的消息。
            ensureOwner(owner)
            databaseProvider().chatDao().findMessage(owner, safeClientMessageId)
        } ?: throw AuthException(
            code = "CHAT_MESSAGE_NOT_FOUND",
            message = "本地消息不存在，无法重试",
            httpStatus = 404
        )

        if (row.localStatus == ChatMessageStatus.SENT.name && row.serverId != null) {
            return withContext(ioDispatcher) {
                // 提前返回前再次复核，账号已切换则不得返回旧 owner 的已发送消息。
                ensureOwner(owner)
                mapper.messageToDomain(row)
            }
        }

        val plaintext = when (val result = withContext(ioDispatcher) {
            cipher.decrypt(row.contentCipher)
        }) {
            is ChatDecryption.Success -> result.value
            ChatDecryption.Unavailable -> throw ChatCipherException(
                "聊天内容不可用，无法重试"
            )
        }

        val retrying = row.copy(localStatus = ChatMessageStatus.SENDING.name)
        withContext(ioDispatcher) {
            ensureOwner(owner)
            databaseProvider().chatDao().upsertMessage(retrying)
        }
        return deliver(retrying, plaintext)
    }

    /** 拉取预订通知并 upsert；按主键去重，不清空本地聊天消息。 */
    suspend fun refreshNotifications(
        cursor: String? = null
    ): CursorPage<ChatNotification> {
        val owner = requireOwner()
        val page = dataSource.listNotifications(
            cursor = ChatDataSource.normalizedCursor(cursor)
        )
        val items = withContext(ioDispatcher) {
            ensureOwner(owner)
            val dao = databaseProvider().chatDao()
            page.items.map { dto ->
                val entity = mapper.notificationToEntity(owner, dto)
                dao.upsertNotification(entity)
                mapper.notificationToDomain(entity)
            }
        }
        return CursorPage(items = items, nextCursor = page.nextCursor)
    }

    private suspend fun upsertRemoteMessage(
        dao: ChatDao,
        owner: Long,
        dto: ChatMessageDto
    ): ChatMessage {
        require(dto.conversationId > 0) { "conversationId must be positive" }
        val trimmedClientId = dto.clientMessageId.trim()
        val clientMessageId = if (trimmedClientId.isNotEmpty()) {
            trimmedClientId
        } else {
            require(dto.id > 0) { "message must have clientMessageId or positive id" }
            "server:${dto.id}"
        }
        val existing = dao.findMessage(owner, clientMessageId)
        val preserveLocal = existing != null &&
            existing.localStatus != ChatMessageStatus.SENT.name

        val entity = if (preserveLocal) {
            existing!!.copy(
                serverId = dto.id.takeIf { it > 0 } ?: existing.serverId,
                senderId = dto.senderId.takeIf { it > 0 } ?: existing.senderId,
                receiverId = dto.receiverId ?: existing.receiverId,
                type = dto.type.ifBlank { existing.type }
            )
        } else {
            val parsed = ChatTime.parseMillis(dto.createdAt)
            val createdAtMillis = when {
                parsed > 0 -> parsed
                else -> existing?.createdAtMillis ?: 0L
            }
            val createdAtRaw = dto.createdAt?.takeIf { it.isNotBlank() }
                ?: existing?.createdAtRaw
            mapper.messageToEntity(
                ownerUserId = owner,
                dto = dto,
                clientMessageId = clientMessageId,
                status = ChatMessageStatus.SENT.name,
                createdAtMillis = createdAtMillis,
                createdAtRaw = createdAtRaw
            )
        }
        dao.upsertMessage(entity)
        return mapper.messageToDomain(entity)
    }

    /**
     * 调用数据源发送并同步本地状态。任何异常都不会被渲染为成功。
     */
    private suspend fun deliver(
        local: ChatMessageEntity,
        plaintext: String
    ): ChatMessage {
        val dto = try {
            dataSource.sendMessage(
                conversationId = local.conversationId,
                clientMessageId = local.clientMessageId,
                content = plaintext
            )
        } catch (e: CancellationException) {
            // 页面销毁/协程取消不得留下永久 SENDING：在不可取消上下文中落 FAILED，
            // 再原样抛出取消异常，调用方仍能看到真实的取消结果。
            withContext(NonCancellable) {
                runCatching { markFailed(local) }
            }
            throw e
        } catch (e: Exception) {
            markFailed(local)
            throw ChatErrorMapper.map(e, UNAUTHORIZED_MESSAGE)
        }

        val parsed = ChatTime.parseMillis(dto.createdAt)
        val sent = local.copy(
            serverId = dto.id.takeIf { it > 0 } ?: local.serverId,
            // senderId 必须以已认证的本地 owner 为准；数据源返回的其他发送者身份忽略。
            receiverId = dto.receiverId ?: local.receiverId,
            createdAtMillis = if (parsed > 0) parsed else local.createdAtMillis,
            createdAtRaw = dto.createdAt?.takeIf { it.isNotBlank() }
                ?: local.createdAtRaw,
            localStatus = ChatMessageStatus.SENT.name
        )
        return withContext(ioDispatcher) {
            // 数据源返回期间登录账号可能已切换；不得把结果写回旧 owner。
            ensureOwner(local.ownerUserId)
            val dao = databaseProvider().chatDao()
            dao.upsertMessage(sent)
            // 成功发送后同步会话列表的最后消息预览；无会话行时影响 0 行。
            dao.updateConversationPreview(
                ownerUserId = sent.ownerUserId,
                conversationId = sent.conversationId,
                previewCipher = sent.contentCipher,
                atMillis = sent.createdAtMillis,
                atRaw = sent.createdAtRaw
            )
            mapper.messageToDomain(sent)
        }
    }

    private suspend fun markFailed(local: ChatMessageEntity) {
        try {
            withContext(ioDispatcher) {
                // 账号已切换时不得把失败状态写回旧 owner 的缓存行。
                if (ownerProvider.currentOwnerId() != local.ownerUserId) {
                    return@withContext
                }
                databaseProvider().chatDao().updateMessageStatus(
                    ownerUserId = local.ownerUserId,
                    clientMessageId = local.clientMessageId,
                    status = ChatMessageStatus.FAILED.name,
                    serverId = local.serverId
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 本地状态标记失败不应覆盖原始可展示异常。
        }
    }

    private fun requireOwner(): Long =
        ownerProvider.currentOwnerId() ?: throw unauthorized(
            "登录后才能使用聊天功能"
        )

    /** 远端结果落库前复核 owner，防止账号切换后把旧账号数据写回。 */
    private fun ensureOwner(owner: Long) {
        if (ownerProvider.currentOwnerId() != owner) {
            throw unauthorized("登录状态已变更，请刷新后重试")
        }
    }

    private fun unauthorized(message: String): AuthException =
        AuthException(
            code = "UNAUTHORIZED",
            message = message,
            httpStatus = 401
        )

    /**
     * 在 IO 上解析 owner、打开数据库并解密映射；未登录直接发空列表，
     * 不读取任何本地缓存。
     */
    private fun <T> ownerScopedList(
        source: suspend (Long) -> Flow<List<T>>
    ): Flow<List<T>> = flow {
        val owner = ownerProvider.currentOwnerId()
        if (owner == null) {
            // 未登录不得读取本地缓存，返回空而不是抛错，便于页面展示登录引导。
            emit(emptyList())
        } else {
            emitAll(source(owner))
        }
    }.flowOn(ioDispatcher)

    private companion object {
        const val UNAUTHORIZED_MESSAGE = "登录已失效，请重新登录"
    }
}
