package com.rhys.relation

import android.graphics.Typeface
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.network.NetworkConfig
import com.example.network.bean.UserDto
import com.example.network.session.SessionManager
import com.example.network.session.SessionStore
import com.rhys.relation.request.FriendRequestsFragment
import com.rhys.relation.search.FriendSearchFragment
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Real Fragment/adapter/repository tests; only MMKV's native persistence is replaced. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [MemorySessionStore::class])
@LooperMode(LooperMode.Mode.PAUSED)
class RelationFragmentReturnTest {
    private lateinit var server: ServerSocket
    private lateinit var activity: ActivityController<FragmentActivity>
    private val responses = LinkedBlockingQueue<Reply>()
    private val requests = LinkedBlockingQueue<String>()
    private val sockets = CopyOnWriteArrayList<Socket>()
    private val replies = mutableListOf<Reply>()

    @Before
    fun setUp() {
        server = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort))
        }
        serverPort = server.localPort
        thread(isDaemon = true, name = "relation-test-http") {
            while (!server.isClosed) {
                try {
                    val socket = server.accept()
                    sockets.add(socket)
                    thread(isDaemon = true) { serve(socket) }
                } catch (_: IOException) {
                    if (!server.isClosed) throw AssertionError("Test HTTP server failed")
                }
            }
        }
        NetworkConfig.baseUrl = "http://127.0.0.1:${server.localPort}/api/v1/"
        login(10, "viewer")
        activity = Robolectric.buildActivity(FragmentActivity::class.java)
        activity.get().setTheme(androidx.appcompat.R.style.Theme_AppCompat_Light_NoActionBar)
        activity.setup()
        activity.get().setContentView(LinearLayout(activity.get()).apply { id = CONTAINER_ID })
    }

    @After
    fun tearDown() {
        if (::activity.isInitialized) activity.pause().stop().destroy()
        SessionManager.logout()
        server.close()
        sockets.forEach { it.close() }
        replies.forEach { it.release.countDown() }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun incomingRequestRowsReturnAfterTheirViewWasDestroyed() {
        respond(requestPage("incoming", listOf(1, 2)))
        val fragment = open(FriendRequestsFragment())
        awaitRows(fragment, R.id.rv_requests, 2)
        assertEquals("Applicant 1", rowText(fragment, R.id.rv_requests, 0, R.id.tv_nickname))

        returnFromAnotherPage(fragment)

        assertEquals(2, recycler(fragment, R.id.rv_requests).adapter!!.itemCount)
        assertEquals(View.VISIBLE, recycler(fragment, R.id.rv_requests).visibility)
        assertEquals("Applicant 1", rowText(fragment, R.id.rv_requests, 0, R.id.tv_nickname))
    }

    @Test
    fun searchRowsAndKeywordReturnAfterTheirViewWasDestroyed() {
        val fragment = open(FriendSearchFragment())
        respond(userPage(listOf(1, 2)))
        search(fragment, "cook")
        awaitRows(fragment, R.id.rv_users, 2)
        assertEquals("Cook 1", rowText(fragment, R.id.rv_users, 0, R.id.tv_nickname))

        returnFromAnotherPage(fragment)

        assertEquals("cook", fragment.requireView().findViewById<EditText>(R.id.et_keyword).text.toString())
        assertEquals(2, recycler(fragment, R.id.rv_users).adapter!!.itemCount)
        assertEquals(View.VISIBLE, recycler(fragment, R.id.rv_users).visibility)
        assertEquals("Cook 1", rowText(fragment, R.id.rv_users, 0, R.id.tv_nickname))
    }

    @Test
    fun outgoingTabRestoresItsRecipientRows() {
        respond(requestPage("incoming", emptyList()))
        val fragment = open(FriendRequestsFragment())
        awaitEmpty(fragment, R.string.relation_request_empty_incoming)
        respond(requestPage("outgoing", listOf(3)))
        fragment.requireView().findViewById<View>(R.id.tv_tab_outgoing).performClick()
        awaitRows(fragment, R.id.rv_requests, 1)

        returnFromAnotherPage(fragment)

        assertEquals(1, recycler(fragment, R.id.rv_requests).adapter!!.itemCount)
        assertEquals("Recipient 3", rowText(fragment, R.id.rv_requests, 0, R.id.tv_nickname))
        val afterTab = fragment.requireView().findViewById<TextView>(R.id.tv_tab_outgoing)
        assertEquals(Typeface.BOLD, afterTab.typeface.style)
        assertEquals(fragment.requireContext().getColor(com.android.common.R.color.color_primary), afterTab.currentTextColor)
    }

    @Test
    fun emptyRequestTabsRestoreTheirEmptyMessage() {
        respond(requestPage("incoming", emptyList()))
        val fragment = open(FriendRequestsFragment())
        awaitEmpty(fragment, R.string.relation_request_empty_incoming)
        returnFromAnotherPage(fragment)
        assertEmpty(fragment, R.string.relation_request_empty_incoming)

        respond(requestPage("outgoing", emptyList()))
        fragment.requireView().findViewById<View>(R.id.tv_tab_outgoing).performClick()
        awaitEmpty(fragment, R.string.relation_request_empty_outgoing)
        returnFromAnotherPage(fragment)
        assertEmpty(fragment, R.string.relation_request_empty_outgoing)
    }

    @Test
    fun emptySearchRestoresNoResultsInsteadOfInitialPrompt() {
        val fragment = open(FriendSearchFragment())
        respond(userPage(emptyList()))
        search(fragment, "missing")
        awaitEmpty(fragment, R.string.relation_search_empty)

        returnFromAnotherPage(fragment)

        assertEmpty(fragment, R.string.relation_search_empty)
    }

    @Test
    fun requestsContinueWithTheNextPageAfterReturning() {
        respond(requestPage("incoming", (1..20).toList(), total = 21))
        val fragment = open(FriendRequestsFragment())
        awaitRows(fragment, R.id.rv_requests, 20)
        assertRequest("/api/v1/friends/requests?direction=incoming&status=all&page=1&pageSize=20")
        returnFromAnotherPage(fragment)
        assertEquals(20, recycler(fragment, R.id.rv_requests).adapter!!.itemCount)

        respond(requestPage("incoming", listOf(21), page = 2, total = 21))
        scrollToBottom(fragment, R.id.rv_requests)

        awaitRows(fragment, R.id.rv_requests, 21)
        assertRequest("/api/v1/friends/requests?direction=incoming&status=all&page=2&pageSize=20")
    }

    @Test
    fun searchContinuesItsKeywordAndNextPageAfterReturning() {
        val fragment = open(FriendSearchFragment())
        respond(userPage((1..20).toList(), total = 21))
        search(fragment, "cook")
        awaitRows(fragment, R.id.rv_users, 20)
        assertRequest("/api/v1/users?q=cook&page=1&pageSize=20")
        returnFromAnotherPage(fragment)
        assertEquals(20, recycler(fragment, R.id.rv_users).adapter!!.itemCount)

        respond(userPage(listOf(21), page = 2, total = 21))
        scrollToBottom(fragment, R.id.rv_users)

        awaitRows(fragment, R.id.rv_users, 21)
        assertRequest("/api/v1/users?q=cook&page=2&pageSize=20")
    }

    @Test
    fun returningAfterAnAccountSwitchLoadsRequestsForTheNewUser() {
        respond(requestPage("incoming", listOf(1)))
        val fragment = open(FriendRequestsFragment())
        awaitRows(fragment, R.id.rv_requests, 1)
        requests.clear()
        val next = Reply(requestPage("incoming", listOf(3)), CountDownLatch(1))

        returnFromAnotherPage(fragment) {
            SessionManager.logout()
            login(20, "other")
            respond(next)
        }

        assertEquals(0, recycler(fragment, R.id.rv_requests).adapter!!.itemCount)
        assertRequest("/api/v1/friends/requests?direction=incoming&status=all&page=1&pageSize=20")
        next.release.countDown()
        awaitRows(fragment, R.id.rv_requests, 1)
        assertEquals("Applicant 3", rowText(fragment, R.id.rv_requests, 0, R.id.tv_nickname))
    }

    @Test
    fun returningAfterAnAccountSwitchDoesNotRestoreOldSearchRelations() {
        val fragment = open(FriendSearchFragment())
        respond(userPage(listOf(1)))
        search(fragment, "cook")
        awaitRows(fragment, R.id.rv_users, 1)
        requests.clear()
        val next = Reply(userPage(listOf(3)), CountDownLatch(1))

        returnFromAnotherPage(fragment) {
            SessionManager.logout()
            login(20, "other")
            respond(next)
        }

        assertEquals(0, recycler(fragment, R.id.rv_users).adapter!!.itemCount)
        assertRequest("/api/v1/users?q=cook&page=1&pageSize=20")
        next.release.countDown()
        awaitRows(fragment, R.id.rv_users, 1)
        assertEquals("Cook 3", rowText(fragment, R.id.rv_users, 0, R.id.tv_nickname))
    }

    @Test
    fun cancelledInitialRequestLoadCanLoadAgainAfterReturning() {
        respond(Reply(requestPage("incoming", listOf(1)), CountDownLatch(1)))
        val fragment = open(FriendRequestsFragment())
        assertRequest("/api/v1/friends/requests?direction=incoming&status=all&page=1&pageSize=20")

        returnFromAnotherPage(fragment) { respond(requestPage("incoming", listOf(3))) }

        awaitRows(fragment, R.id.rv_requests, 1)
        assertEquals("Applicant 3", rowText(fragment, R.id.rv_requests, 0, R.id.tv_nickname))
    }

    @Test
    fun cancelledInitialSearchCanLoadAgainAfterReturning() {
        val fragment = open(FriendSearchFragment())
        respond(Reply(userPage(listOf(1)), CountDownLatch(1)))
        search(fragment, "cook")
        assertRequest("/api/v1/users?q=cook&page=1&pageSize=20")

        returnFromAnotherPage(fragment) { respond(userPage(listOf(3))) }

        awaitRows(fragment, R.id.rv_users, 1)
        assertEquals("Cook 3", rowText(fragment, R.id.rv_users, 0, R.id.tv_nickname))
    }

    @Test
    fun returningWhileLoggedOutShowsNoPreviouslyLoadedRows() {
        respond(requestPage("incoming", listOf(1)))
        val fragment = open(FriendRequestsFragment())
        awaitRows(fragment, R.id.rv_requests, 1)

        returnFromAnotherPage(fragment) { SessionManager.logout() }

        assertEquals(0, recycler(fragment, R.id.rv_requests).adapter!!.itemCount)
        assertEquals(View.VISIBLE, fragment.requireView().findViewById<View>(R.id.ll_login_required).visibility)
    }

    private fun login(id: Int, username: String) {
        respond("""{"token":"test-$id","user":{"id":$id,"username":"$username","nickname":"$username","avatar":null,"bio":null,"createdAt":null}}""")
        runBlocking { SessionManager.login(username, "test-password") }
        assertEquals("/api/v1/auth/login", requests.poll(5, TimeUnit.SECONDS))
    }

    private fun serve(socket: Socket) {
        try {
            socket.use {
                val reader = socket.getInputStream().bufferedReader()
                val requestLine = reader.readLine() ?: return
                var bodyLength = 0
                while (true) {
                    val header = reader.readLine() ?: return
                    if (header.isEmpty()) break
                    if (header.startsWith("Content-Length:", ignoreCase = true)) {
                        bodyLength = header.substringAfter(':').trim().toInt()
                    }
                }
                repeat(bodyLength) { reader.read() }
                val reply = responses.poll(5, TimeUnit.SECONDS)
                requests.add(requestLine.split(' ')[1])
                reply?.release?.await(5, TimeUnit.SECONDS)
                val bytes = (reply?.body ?: "{}").toByteArray()
                socket.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(bytes)
                    flush()
                }
            }
        } catch (_: IOException) {
            // A cancelled Fragment request may close its socket before a response is sent.
        }
    }

    private fun <T : Fragment> open(fragment: T): T {
        activity.get().supportFragmentManager.beginTransaction()
            .replace(CONTAINER_ID, fragment).commitNow()
        shadowOf(Looper.getMainLooper()).idle()
        return fragment
    }

    private fun returnFromAnotherPage(fragment: Fragment, whileAway: () -> Unit = {}) {
        val oldView = fragment.requireView()
        val manager = activity.get().supportFragmentManager
        manager.beginTransaction().replace(CONTAINER_ID, Fragment()).addToBackStack(null).commit()
        manager.executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull("Navigation must destroy the original view", fragment.view)
        whileAway()
        assertTrue(manager.popBackStackImmediate())
        shadowOf(Looper.getMainLooper()).idle()
        assertNotSame(oldView, fragment.requireView())
    }

    private fun search(fragment: Fragment, keyword: String) {
        fragment.requireView().findViewById<EditText>(R.id.et_keyword).setText(keyword)
        fragment.requireView().findViewById<View>(R.id.btn_search).performClick()
    }

    private fun recycler(fragment: Fragment, id: Int): RecyclerView =
        fragment.requireView().findViewById(id)

    private fun awaitRows(fragment: Fragment, id: Int, expected: Int) {
        await("Expected $expected displayed rows") { recycler(fragment, id).adapter!!.itemCount == expected }
    }

    private fun awaitEmpty(fragment: Fragment, messageId: Int) {
        await("Expected completed empty state") {
            val empty = fragment.requireView().findViewById<TextView>(R.id.tv_empty)
            empty.visibility == View.VISIBLE && empty.text == fragment.getString(messageId)
        }
    }

    private fun assertEmpty(fragment: Fragment, messageId: Int) {
        val empty = fragment.requireView().findViewById<TextView>(R.id.tv_empty)
        assertEquals(View.VISIBLE, empty.visibility)
        assertEquals(fragment.getString(messageId), empty.text.toString())
    }

    private fun assertRequest(path: String) {
        assertEquals(path, requests.poll(5, TimeUnit.SECONDS))
    }

    private fun respond(body: String) = respond(Reply(body))

    private fun respond(reply: Reply) {
        replies.add(reply)
        responses.add(reply)
    }

    private fun scrollToBottom(fragment: Fragment, id: Int) {
        rowText(fragment, id, 0, R.id.tv_nickname)
        recycler(fragment, id).scrollBy(0, 10000)
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(message, condition())
    }

    private fun rowText(fragment: Fragment, id: Int, position: Int, textId: Int): String {
        val list = recycler(fragment, id)
        list.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
        )
        list.layout(0, 0, 400, 800)
        return list.findViewHolderForAdapterPosition(position)!!.itemView
            .findViewById<TextView>(textId).text.toString()
    }

    private fun requestPage(direction: String, ids: List<Int>, page: Int = 1, total: Int = ids.size): String {
        val items = ids.joinToString { id ->
            val from = if (direction == "incoming") id else 10
            val to = if (direction == "incoming") 10 else id
            """{"id":$id,"status":"pending","message":"hello","from":{"id":$from,"username":"applicant$from","nickname":"Applicant $from","avatar":null},"to":{"id":$to,"username":"recipient$to","nickname":"Recipient $to","avatar":null},"createdAt":null,"updatedAt":null}"""
        }
        return """{"items":[$items],"page":$page,"pageSize":20,"total":$total}"""
    }

    private fun userPage(ids: List<Int>, page: Int = 1, total: Int = ids.size): String {
        val items = ids.joinToString { id ->
            """{"id":$id,"username":"cook$id","nickname":"Cook $id","avatar":null,"bio":null,"createdAt":null,"isFriend":false}"""
        }
        return """{"items":[$items],"page":$page,"pageSize":20,"total":$total}"""
    }

    private companion object {
        const val CONTAINER_ID = 12345
        // ApiClient intentionally keeps its first base URL for this JVM's lifetime.
        var serverPort = 0
    }

    private data class Reply(val body: String, val release: CountDownLatch = CountDownLatch(0))
}

/** The real SessionStore delegates to MMKV JNI, unavailable in a JVM Android test. */
@Implements(SessionStore::class, isInAndroidSdk = false)
class MemorySessionStore {
    private var token: String? = null
    private var user: UserDto? = null

    @Implementation
    fun token(): String? = token

    @Implementation
    fun user(): UserDto? = user

    @Implementation
    fun save(token: String, user: UserDto) {
        this.token = token
        this.user = user
    }

    @Implementation
    fun clear() {
        token = null
        user = null
    }
}
