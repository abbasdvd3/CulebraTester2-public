package com.dtmilano.android.culebratester2

import android.app.UiAutomation
import android.content.Context
import android.graphics.Point
import android.view.Display
import android.view.WindowManager
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiSelector
import com.dtmilano.android.culebratester2.location.OidObj
import com.google.gson.Gson
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.contentType
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.swagger.server.models.BooleanResponse
import io.swagger.server.models.CurrentPackageName
import io.swagger.server.models.DisplayHeight
import io.swagger.server.models.DisplayRealSize
import io.swagger.server.models.DisplayRotationEnum
import io.swagger.server.models.DisplaySizeDp
import io.swagger.server.models.DisplayWidth
import io.swagger.server.models.Help
import io.swagger.server.models.LastTraversedText
import io.swagger.server.models.ObjectRef
import io.swagger.server.models.ProductName
import io.swagger.server.models.Selector
import io.swagger.server.models.StatusCode
import io.swagger.server.models.StatusResponse
import io.swagger.server.models.SwipeBody
import io.swagger.server.models.Text
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Test
import org.junit.contrib.java.lang.system.ExpectedSystemExit
import org.junit.runner.RunWith
import org.mockito.ArgumentMatcher
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.OutputStream
import java.lang.ref.WeakReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.text.Regex.Companion.escape


/**
 * Ktor Application Test.
 */
@KtorExperimentalLocationsAPI
// We need Robolectric to run these tests because some classes use Log.[we]
@RunWith(RobolectricTestRunner::class)
@Config(minSdk = 26, maxSdk = 31)
class KtorApplicationTest {

    companion object {

        @ClassRule
        @JvmField
        val exit: ExpectedSystemExit = ExpectedSystemExit.none()

        val appComponent: ApplicationComponent = CulebraTesterApplication().appComponent

        var holder: Holder = appComponent.holder().instance

        var objectStore: ObjectStore = appComponent.objectStore()

        private val realSize = mapOf("x" to 1080, "y" to 2400)

        fun setDisplaySize(point: Point) {
            println("🖥 setDisplaySize called")
            point.x = realSize["x"] ?: error("missing value")
            point.y = realSize["y"] ?: error("missing value")
        }

        class MatchPoint : ArgumentMatcher<Point> {
            override fun matches(point: Point): Boolean {
                setDisplaySize(point)
                return true
            }
        }

        // <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
        const val WINDOW_HIERARCHY = """
            <hierarchy rotation="0">
              <node index="0" text="" resource-id="com.android.systemui:id/navigation_bar_frame" class="android.widget.FrameLayout" package="com.android.systemui" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" visible-to-user="true" bounds="[0,1794][1080,1920]">
                <node index="0" text="" resource-id="com.google.android.deskclock:id/tabs" class="android.widget.HorizontalScrollView" package="com.google.android.deskclock" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="true" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" bounds="[0,66][969,264]"/>
              </node>
            </hierarchy>
        """

        class MatchOutputStream : ArgumentMatcher<OutputStream> {
            override fun matches(output: OutputStream?): Boolean {
                output?.write(WINDOW_HIERARCHY.trimIndent().toByteArray())
                return true
            }
        }

        const val MATCHES = "MATCHES"
        const val DOES_NOT_MATCH = "DOES_NOT_MATCH"

        class BySelectorMatcherRes : ArgumentMatcher<BySelector> {
            override fun matches(selector: BySelector?): Boolean {
                // There's no way of comparing other than via String
                val res = "BySelector [RES='\\Q${DOES_NOT_MATCH}\\E']"
                if (selector.toString().matches(Regex(escape(res)))) {
                    return false
                }
                return true
            }
        }

        class UiSelectorMatcherRes : ArgumentMatcher<UiSelector> {
            override fun matches(selector: UiSelector?): Boolean {
                // There's no way of comparing other than via String
                val res = "UiSelector [RES='\\Q${DOES_NOT_MATCH}\\E']"
                if (selector.toString().matches(Regex(escape(res)))) {
                    return false
                }
                return true
            }
        }

        class BySelectorMatcherClassMatches : ArgumentMatcher<BySelector> {
            override fun matches(bySelector: BySelector?): Boolean {
                // There's no way of comparing other than via String
                val res = "BySelector [CLASS='\\Q${MATCHES}\\E']"
                return bySelector.toString().matches(Regex(escape(res)))
            }
        }

        class BySelectorMatcherClassDoesNotMatch : ArgumentMatcher<BySelector> {
            override fun matches(bySelector: BySelector?): Boolean {
                // There's no way of comparing other than via String
                val res = "BySelector [CLASS='\\Q${DOES_NOT_MATCH}\\E']"
                return bySelector.toString().matches(Regex(escape(res)))
            }
        }

        val display: Display = mock {
            on { getRealSize(argThat(matcher = MatchPoint())) } doAnswer {}
        }

        val windowManager = mock<WindowManager> {
            on { defaultDisplay } doReturn display
        }

        private const val MOCK_CLASS_NAME = "MockClassName"

        val targetContext = mock<WeakReference<Context>> {

        }

        val uiObject = mock<UiObject> {
            on { className } doReturn MOCK_CLASS_NAME
            on { text } doReturn "Hello Culebra!"
        }

        val uiObject2 = mock<UiObject2> {
            on { className } doReturn MOCK_CLASS_NAME
            on { text } doReturn "Hello Culebra!"
        }
        val uiDevice = mock<UiDevice> {
            val x = realSize["x"] ?: error("x not defined")
            val y = realSize["y"] ?: error("y not defined")
            val p = Point()
            setDisplaySize(p)

            on { click(any(), any()) } doReturn true
            on { displayWidth } doReturn x
            on { displayHeight } doReturn y
            on { displaySizeDp } doReturn p
            on { dumpWindowHierarchy(argThat(MatchOutputStream())) } doAnswer {}
            on { findObject(argThat(BySelectorMatcherRes())) } doReturn uiObject2
            on { findObject(argThat(UiSelectorMatcherRes())) } doReturn uiObject
            on { hasObject(argThat(BySelectorMatcherClassDoesNotMatch())) } doReturn false
            on { hasObject(argThat(BySelectorMatcherClassMatches())) } doReturn true
            on { pressBack() } doReturn true
            on { pressEnter() } doReturn true
            on { pressDelete() } doReturn true
            on { pressHome() } doReturn true
            on { pressKeyCode(anyInt(), anyInt()) } doReturn true
            on { pressRecentApps() } doReturn true
            on { takeScreenshot(any(), any(), any()) } doReturn true
        }

        val uiDeviceNoScreenshot = mock<UiDevice> {
            on { takeScreenshot(any(), any(), any()) } doReturn false
        }

        val uiAutomation = mock<UiAutomation> {

        }

        @BeforeClass
        @JvmStatic
        fun setupClass() {
            holder.windowManager = windowManager
            holder.cacheDir = File("/tmp")
        }
    }

    @Before
    fun setup() {
        holder.targetContext = targetContext
        holder.uiDevice = uiDevice
        holder.uiAutomation = uiAutomation
        objectStore.clear()
    }

    private inline fun <reified T> TestApplicationCall.jsonResponse(content: String? = response.content) =
        Gson().fromJson<T>(content, T::class.java)

    @Test
    fun testRoot() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(
                    "CulebraTester2: Go to http://localhost:80/help for usage details.\n",
                    response.content
                )
            }
        }
    }

    @Test
    fun testQuit() {
        withTestApplication({ module(testing = true) }) {
            exit.expectSystemExit()
            handleRequest(HttpMethod.Get, "/quit").apply {
                assertEquals(HttpStatusCode.Gone, response.status())
                // added sleep to try to alleviate an exception for "shutdown in progress"
                Thread.sleep(2000)
            }
        }
    }

    @Test
    fun `test non-existent url`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/DOES_NOT_EXIST").apply {
                assertEquals(HttpStatusCode.NotFound, response.status())
            }
        }
    }

    @Test
    fun `test culebra info`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/culebra/info").apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun testJsonGson() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/json/gson").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val json = Gson().fromJson(response.content, Map::class.java)
                assertEquals(json["hello"], "world")
            }
        }
    }

    @Ignore("TODO")
    @Test
    fun `test static image`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/static/ktor_logo.svg").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(response.contentType(), ContentType.parse("image/svg+xml"))
            }
        }
    }

    @Test
    fun `test culebra generic help`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/help").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val help = Gson().fromJson<Help>(response.content, Help::class.java)
                assertTrue { help.text.matches(Regex(".*generic.*")) }
            }
        }
    }

    @Test
    fun `test culebra help for some api`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/culebra/help/%2Fsomeapi").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val help = Gson().fromJson<Help>(response.content, Help::class.java)
                assertTrue { help.text.matches(Regex(".*someapi.*")) }
            }
        }
    }

    @Test
    fun `test culebra help for uiDevice`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/culebra/help/%2FuiDevice%2Fsomethingelse").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val help = Gson().fromJson<Help>(response.content, Help::class.java)
                assertTrue { help.text.matches(Regex(".*UiDevice.*somethingelse.*")) }
            }
        }
    }

    @Test
    fun `test culebra help for some api not starting with slash 0x2F`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/culebra/help/someapi").apply {
                assertEquals(HttpStatusCode.InternalServerError, response.status())
            }
        }
    }

    @Test
    fun `test culebra help for some api with query`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/culebra/help").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val help = Gson().fromJson<Help>(response.content, Help::class.java)
                println(help)
                assertTrue { help.text.matches(Regex(".*sample help.*")) }
            }
        }
    }

    @Test
    fun `test device display real size`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/device/displayRealSize").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val displayRealSize = jsonResponse<DisplayRealSize>()
                assertEquals("robolectric", displayRealSize.device)
                assertEquals(realSize["x"], displayRealSize.x)
                assertEquals(realSize["y"], displayRealSize.y)
            }
        }
    }

    @Test
    fun `test object store list`() {
        objectStore.put("Some object")
        objectStore.put("Another object")
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/objectStore/list").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                println(response.content)
                assertEquals(
                    "[{\"oid\":1,\"obj\":\"Some object\"},{\"oid\":2,\"obj\":\"Another object\"}]",
                    response.content
                )
            }
        }
    }

    @Test
    fun `test object store list with uiobject2`() {
        val uio21 = mock<UiObject2> {}
        val uio22 = mock<UiObject2> {}
        objectStore.put(uio21)
        objectStore.put(uio22)
        assertEquals(2, objectStore.size())
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/objectStore/list").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                println(response.content)
                val list = jsonResponse<Array<OidObj>>()
                assertEquals(2, list.size)
                list.forEach { assertTrue(it.oid > 0); assertTrue(it.obj.toString().isNotEmpty()) }
            }
        }
    }

    @Test
    fun `test object store clear`() {
        val uio21 = mock<UiObject2> {}
        val uio22 = mock<UiObject2> {}
        objectStore.put(uio21)
        objectStore.put(uio22)
        assertEquals(2, objectStore.size())
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/objectStore/clear").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                println(response.content)
                assertEquals(0, objectStore.size())
            }
        }
    }

    @Test
    fun `test object store remove`() {
        val uio21 = mock<UiObject2> {}
        val oid = objectStore.put(uio21)
        val uio22 = mock<UiObject2> {}
        objectStore.put(uio22)
        assertEquals(2, objectStore.size())
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/objectStore/remove?oid=${oid}").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                println(response.content)
                assertEquals(1, objectStore.size())
            }
        }
    }

    @Test
    fun `test clear last traversed text`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiDevice/clearLastTraversedText").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val statusResponse = jsonResponse<StatusResponse>()
                assertEquals("OK", statusResponse.status.value)
            }
        }
    }

    @Test
    fun `test find object no selectors`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiDevice/findObject").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val statusResponse = jsonResponse<StatusResponse>()
                assertEquals("ERROR", statusResponse.status.value, statusResponse.errorMessage)
            }
        }
    }

    @Test
    fun `test find object resourceId selector`() {
        withTestApplication({ module(testing = true) }) {
            assertEquals(0, objectStore.size())
            handleRequest(
                HttpMethod.Get,
                "/v2/uiDevice/findObject?resourceId=$DOES_NOT_MATCH"
            ).apply {
                assertEquals(HttpStatusCode.NotFound, response.status())
                assertEquals(
                    StatusCode.OBJECT_NOT_FOUND.message() + "\n",
                    response.content
                )
            }
            assertEquals(0, objectStore.size())
        }
    }

    @Test
    fun `test find object uiSelector selector`() {
        withTestApplication({ module(testing = true) }) {
            assertEquals(0, objectStore.size())
            handleRequest(
                HttpMethod.Get,
                "/v2/uiDevice/findObject?uiSelector=res@${MATCHES}"
            ).apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }
            assertEquals(1, objectStore.size())
        }
    }

    @Test
    fun `test find object bySelector selector`() {
        withTestApplication({ module(testing = true) }) {
            assertEquals(0, objectStore.size())
            handleRequest(
                HttpMethod.Get,
                "/v2/uiDevice/findObject?bySelector=clazz@$MATCHES"
            ).apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }
            assertEquals(1, objectStore.size())
        }
    }

    @Test
    fun `test freeze rotation`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(
                HttpMethod.Get,
                "/v2/uiDevice/freezeRotation"
            ).apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun `test unfreeze rotation`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(
                HttpMethod.Get,
                "/v2/uiDevice/unfreezeRotation"
            ).apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun `test is natural orientation`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(
                HttpMethod.Get,
                "/v2/uiDevice/isNaturalOrientation"
            ).apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val booleanResponse = jsonResponse<BooleanResponse>()
                assertEquals(booleanResponse.name, "isNaturalOrientation")
                assertFalse(booleanResponse.value)
            }
        }
    }

    @Test
    fun `test is screen on`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(
                HttpMethod.Get,
                "/v2/uiDevice/isScreenOn"
            ).apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val booleanResponse = jsonResponse<BooleanResponse>()
                assertEquals(booleanResponse.name, "isScreenOn")
                assertFalse(booleanResponse.value)
            }
        }
    }

    @Test
    fun `test has object`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(
                HttpMethod.Get,
                "/v2/uiDevice/hasObject?bySelector=clazz@${MATCHES}"
            ).apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun `test has object not found`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(
                HttpMethod.Get,
                "/v2/uiDevice/hasObject?bySelector=clazz@${DOES_NOT_MATCH}"
            ).apply {
                assertEquals(HttpStatusCode.NotFound, response.status())
                assertEquals(
                    StatusCode.OBJECT_NOT_FOUND.message() + "\n",
                    response.content
                )
            }
        }
    }

    @Test
    fun `test find object should not store object when not found`() {
        withTestApplication({ module(testing = true) }) {
            assertEquals(0, objectStore.size())
            handleRequest(
                HttpMethod.Get,
                "/v2/uiDevice/findObject?resourceId=$DOES_NOT_MATCH"
            ).apply {
                assertEquals(HttpStatusCode.NotFound, response.status())
                assertEquals(
                    StatusCode.OBJECT_NOT_FOUND.message() + "\n",
                    response.content
                )
                assertEquals(0, objectStore.size())
            }
        }
    }

    @Test
    fun `test find object should store object when found`() {
        withTestApplication({ module(testing = true) }) {
            assertEquals(0, objectStore.size())
            handleRequest(HttpMethod.Get, "/v2/uiDevice/findObject?resourceId=$MATCHES").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val objectRef = jsonResponse<ObjectRef>()
                assertTrue(objectRef.oid > 0)
                assertEquals(MOCK_CLASS_NAME, objectRef.className)
                assertEquals(1, objectStore.size())
            }
        }
    }

    @Test
    fun `test find object post selector`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Post, "/v2/uiDevice/findObject") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(
                    Gson().toJson(
                        Selector(
                            clazz = "android.widget.Button",
                            depth = 1,
                            desc = "Equal"
                        )
                    )
                )
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val objectRef = jsonResponse<ObjectRef>()
                assertTrue(objectRef.oid > 0)
                assertEquals(MOCK_CLASS_NAME, objectRef.className)
            }
        }
    }

    @Test
    fun `test find object post selector not matching should respond not found`() {
        withTestApplication({ module(testing = true) }) {
            assertEquals(0, objectStore.size())
            handleRequest(HttpMethod.Post, "/v2/uiDevice/findObject") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(
                    Gson().toJson(
                        Selector(
                            res = DOES_NOT_MATCH
                        )
                    )
                )
            }.apply {
                assertEquals(HttpStatusCode.NotFound, response.status())
                assertEquals(
                    StatusCode.OBJECT_NOT_FOUND.message() + "\n",
                    response.content
                )
                assertEquals(0, objectStore.size())
            }
        }
    }

    @Test
    fun `test uiobject2 get text`() {
        assertEquals(0, objectStore.size())
        val oid = objectStore.put(uiObject2)
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiObject2/$oid/getText").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val text = jsonResponse<Text>()
                assertEquals("Hello Culebra!", text.text)
            }
        }
    }

    @Test
    fun `test uiobject2 set text get`() {
        assertEquals(0, objectStore.size())
        val oid = objectStore.put(uiObject2)
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiObject2/$oid/setText?text=hello+world").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                verify(uiObject2, times(1)).text = any()
            }
        }
    }

    @Test
    fun `test uiobject2 set text get missing text`() {
        assertEquals(0, objectStore.size())
        val oid = objectStore.put(uiObject2)
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiObject2/$oid/setText").apply {
                assertEquals(HttpStatusCode.NotFound, response.status())
            }
        }
    }

    @Test
    fun `test uiobject2 set text get with invalid oid`() {
        assertEquals(0, objectStore.size())
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiObject2/1/setText?text=hello+world").apply {
                assertEquals(HttpStatusCode.NotFound, response.status())
            }
        }
    }

    @Test
    fun `test uiobject2 set text post`() {
        assertEquals(0, objectStore.size())
        val oid = objectStore.put(uiObject2)
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Post, "/v2/uiObject2/$oid/setText") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(
                    Gson().toJson(
                        Text(text = "Some text 👈🏻")
                    )
                )
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                verify(uiObject2, times(1)).text = any()
            }
        }
    }

    @Test
    fun `test uiobject2 set text post with invalid oid`() {
        assertEquals(0, objectStore.size())
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Post, "/v2/uiObject2/1/setText") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(
                    Gson().toJson(
                        Text(text = "Some text")
                    )
                )
            }.apply {
                assertEquals(HttpStatusCode.NotFound, response.status())
            }
        }
    }

    @Test
    fun `test obtain screenshot`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiDevice/screenshot").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(response.headers["Content-Type"], "image/png")
            }
        }
    }

    @Test
    fun `test drag get`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(
                HttpMethod.Get,
                "/v2/uiDevice/drag?startX=0&startY=0&endX=100&endY=100&steps=10"
            ).apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun `test swipe get`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(
                HttpMethod.Get,
                "/v2/uiDevice/swipe?startX=0&startY=0&endX=100&endY=100&steps=10"
            ).apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun `test swipe post`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Post, "/v2/uiDevice/swipe") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(
                    Gson().toJson(
                        SwipeBody(
                            arrayOf(
                                io.swagger.server.models.Point(0, 0),
                                io.swagger.server.models.Point(5, 5),
                                io.swagger.server.models.Point(10, 10)
                            ), 10
                        )
                    )
                )
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun `test obtain screenshot with params`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiDevice/screenshot?scale=0.5&quality=50").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(response.contentType(), ContentType.Image.PNG)
            }
        }
    }

    @Test
    fun `test cannot obtain screenshot`() {
        holder.uiDevice = uiDeviceNoScreenshot
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiDevice/screenshot?scale=0.5&quality=50").apply {
                assertEquals(HttpStatusCode.InternalServerError, response.status())
            }
        }
    }

    @Test
    fun `test click with params`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiDevice/click?x=100&y=50").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val statusResponse = jsonResponse<StatusResponse>()
                assertEquals(StatusResponse.Status.OK, statusResponse.status)
            }
        }
    }

    @Test
    fun `test get current package name`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiDevice/currentPackageName").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val currentPackageName = jsonResponse<CurrentPackageName>()
                assertEquals(null, currentPackageName.currentPackageName)
            }
        }
    }

    @Test
    fun `test display height`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiDevice/displayHeight").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val displayHeight = jsonResponse<DisplayHeight>()
                assertEquals(realSize["y"], displayHeight.displayHeight)
            }
        }
    }

    @Test
    fun `test display width`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiDevice/displayWidth").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val displayWidth = jsonResponse<DisplayWidth>()
                assertEquals(realSize["x"], displayWidth.displayWidth)
            }
        }
    }

    @Test
    fun `test get display rotation`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiDevice/displayRotation").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val displayRotation = jsonResponse<DisplayRotationEnum>()
                assertEquals(0, displayRotation.value)
            }
        }
    }

    @Test
    fun `test get display size dp`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiDevice/displaySizeDp").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val displaySizeDp = jsonResponse<DisplaySizeDp>()
                assertEquals(realSize["x"], displaySizeDp.displaySizeDpX)
                assertEquals(realSize["y"], displaySizeDp.displaySizeDpY)
            }
        }
    }

    @Test
    fun `test dump window hierarchy with default format`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiDevice/dumpWindowHierarchy").apply {
                println(response.content)
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun `test dump window hierarchy json`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiDevice/dumpWindowHierarchy?format=JSON").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val json = Gson().fromJson(response.content, Map::class.java)
                assertEquals("hierarchy", json["id"])
                assertEquals("Window Hierarchy", json["text"])
                assertEquals(1, (json["children"] as ArrayList<*>).size)
            }
        }
    }

    @Test
    fun `test dump window hierarchy xml`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiDevice/dumpWindowHierarchy?format=XML").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(
                    WINDOW_HIERARCHY.trimIndent(), response.content
                )
            }
        }
    }

    @Test
    fun `test dump window hierarchy invalid`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiDevice/dumpWindowHierarchy?format=INVALID").apply {
                assertEquals(HttpStatusCode.UnprocessableEntity, response.status())
            }
        }
    }

    @Test
    fun `test get last traversed text`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiDevice/lastTraversedText").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val lastTraversedText = jsonResponse<LastTraversedText>()
                assertEquals(null, lastTraversedText.lastTraversedText)
            }
        }
    }

    @Test
    fun `test press back`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiDevice/pressBack").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val statusResponse = jsonResponse<StatusResponse>()
                assertEquals("OK", statusResponse.status.value)
            }
        }
    }

    @Test
    fun `test press delete`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiDevice/pressDelete").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val statusResponse = jsonResponse<StatusResponse>()
                assertEquals("OK", statusResponse.status.value)
            }
        }
    }

    @Test
    fun `test press enter`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiDevice/pressEnter").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val statusResponse = jsonResponse<StatusResponse>()
                assertEquals("OK", statusResponse.status.value)
            }
        }
    }

    @Test
    fun `test press home`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiDevice/pressHome").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val statusResponse = jsonResponse<StatusResponse>()
                assertEquals("OK", statusResponse.status.value)
            }
        }
    }

    @Test
    fun `test press key code`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiDevice/pressKeyCode?keyCode=10").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val statusResponse = jsonResponse<StatusResponse>()
                assertEquals("OK", statusResponse.status.value)
            }
        }
    }

    @Test
    fun `test press recent app`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiDevice/pressRecentApps").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val statusResponse = jsonResponse<StatusResponse>()
                assertEquals("OK", statusResponse.status.value)
            }
        }
    }

    @Test
    fun `test get product name`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiDevice/productName").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val productName = jsonResponse<ProductName>()
                assertEquals(null, productName.productName)
            }
        }
    }

    @Test
    fun `test wait for idle`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiDevice/waitForIdle").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val statusResponse = jsonResponse<StatusResponse>()
                assertEquals("OK", statusResponse.status.value)
            }
        }
    }

    @Test
    fun `test wait for window update`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiDevice/waitForWindowUpdate?timeout=10").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val statusResponse = jsonResponse<StatusResponse>()
                // No window update will happen, so we expect ERROR
                assertEquals("ERROR", statusResponse.status.value, statusResponse.errorMessage)
            }
        }
    }

    @Test
    fun `test wait for window update with package name`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(
                HttpMethod.Get,
                "/v2/uiDevice/waitForWindowUpdate?timeout=10&packageName=com.android.systemui"
            ).apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val statusResponse = jsonResponse<StatusResponse>()
                // No window update will happen, so we expect ERROR
                assertEquals("ERROR", statusResponse.status.value, statusResponse.errorMessage)
            }
        }
    }

    @Test
    fun `test click missing param`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiDevice/click?x=100").apply {
                assertEquals(HttpStatusCode.NotFound, response.status())
                println(response.status())
                println(response.content)
            }
        }
    }

    @Test
    fun `test uiobject2 clear`() {
        val oid = objectStore.put(uiObject2)
        assertEquals(1, objectStore.size())
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiObject2/${oid}/clear").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                verify(uiObject2, times(1)).clear()
                println(response.status())
                println(response.content)
            }
        }
    }

    @Test
    fun `test uiobject2 clear with invalid oid`() {
        assertEquals(0, objectStore.size())
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiObject2/1/clear").apply {
                assertEquals(HttpStatusCode.NotFound, response.status())
            }
        }
    }

    @Test
    fun `test uiobject2 click`() {
        val oid = objectStore.put(uiObject2)
        assertEquals(1, objectStore.size())
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiObject2/${oid}/click").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                verify(uiObject2, times(1)).click()
                println(response.content)
                println(response.status())
                println(response.content)
            }
        }
    }

    @Test
    fun `test uiobject2 dump`() {
        val oid = objectStore.put(uiObject2)
        assertEquals(1, objectStore.size())
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/v2/uiObject2/${oid}/dump").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val selector = jsonResponse<Selector>()
                assertEquals(false, selector.clickable)
                assertEquals(MOCK_CLASS_NAME, selector.clazz)
                assertEquals("Hello Culebra!", selector.text)
                println(response.content)
                println(response.status())
                println(response.content)
            }
        }
    }
}
