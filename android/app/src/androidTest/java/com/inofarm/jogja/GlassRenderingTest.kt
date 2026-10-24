package com.inofarm.jogja

import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.data.*
import com.inofarm.jogja.ui.*
import dev.chrisbanes.haze.*
import org.junit.*
import org.junit.Assert.*

class GlassRenderingTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun shot(name:String) {
        rule.waitForIdle();Thread.sleep(300)
        instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/liquid-$name.png").let {android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use {f->f.readBytes()}}
    }
    @After fun cleanup() {SessionStore(instrumentation.targetContext).clear()}
    @Test fun trueBackdropAndExplicitOpaqueFallback() {
        fun render(enabled:Boolean) {
            rule.activity.setContent { InofarmTheme {
                CompositionLocalProvider(LocalGlassEffectsEnabled provides enabled) {
                    val haze=rememberHazeState()
                    Box(Modifier.fillMaxSize().safeDrawingPadding(),contentAlignment=Alignment.Center) {
                        Canvas(Modifier.width(320.dp).height(200.dp).hazeSource(haze)) {
                            for(i in 0..31) drawRect(if(i%2==0) Color(0xFF245B43) else Color.White,Offset(i*size.width/32,0f),Size(size.width/32,size.height))
                        }
                        GlassSurface(Modifier.width(300.dp).height(72.dp).testTag("glassProbe"),haze) {Text("Liquid Glass · Inofarm",Modifier.align(Alignment.Center))}
                    }
                }
            } }
        }
        render(true);shot("glass-supported")
        val blurred=rule.onNodeWithTag("glassProbe").captureToImage().toPixelMap()
        render(false);shot("glass-fallback")
        val opaque=rule.onNodeWithTag("glassProbe").captureToImage().toPixelMap()
        assertEquals(blurred.width,opaque.width)
        // Runtime proof that API37 blur rendering differs from the opaque performance fallback.
        var differences=0
        for(x in 20 until blurred.width-20 step 5) if(blurred[x,10]!=opaque[x,10]) differences++
        assertTrue("Backdrop rendering differs from opaque fallback",differences>10)
    }
    @Test fun allConfigurationDetailsAtNarrowWidthAndTwoHundredPercent() {
        val session=SessionStore(instrumentation.targetContext)
        val vm=OwnerViewModel(OwnerRepository(ApiClient({session.baseUrl},{null},true),session))
        val state=OwnerState(config=RemoteConfig(null,ConfigDraft().json(),null,null),resources=mapOf("config" to ResourceState(fetchedAt="2026-10-12T01:40:00Z")))
        fun render(page:String) {
            rule.activity.setContent {
                val density=LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                    InofarmTheme {Box(Modifier.width(280.dp).fillMaxHeight().safeDrawingPadding()) {SettingsScreen(state,vm,{},page=page)}}
                }
            }
        }
        listOf("schedule","ppm","sensors","device").forEach {page ->
            render(page)
            val last=when(page) {"schedule"->rule.onNodeWithTag("Toleransi akhir (menit)");"ppm"->rule.onNodeWithTag("Maksimum (ppm)");"sensors"->rule.onAllNodesWithTag("Maksimum").onLast();else->rule.onNodeWithTag("Durasi buzzer pengingat")}
            last.performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("Simpan konfigurasi").assertIsDisplayed()
            val field=last.fetchSemanticsNode().boundsInRoot
            val footer=rule.onNodeWithTag("formFooter").fetchSemanticsNode().boundsInRoot
            assertTrue("Last field above footer: $page",field.bottom<=footer.top+1)
            shot("font200-$page")
        }
    }
}
