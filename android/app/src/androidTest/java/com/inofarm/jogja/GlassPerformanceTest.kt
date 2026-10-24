package com.inofarm.jogja

import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.data.*
import com.inofarm.jogja.ui.*
import dev.chrisbanes.haze.*
import org.junit.*

/** Controlled test scene with production components, measured in the non-debug profile variant. */
class GlassPerformanceTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun shell(command:String):String = instrumentation.uiAutomation.executeShellCommand(command).let {android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use {f->f.readBytes().toString(Charsets.UTF_8)}}
    @After fun cleanup() {SessionStore(instrumentation.targetContext).clear()}
    @Test fun releaseChromeMotionSample() {
        val session=SessionStore(instrumentation.targetContext)
        val vm=OwnerViewModel(OwnerRepository(ApiClient({session.baseUrl},{null},false),session))
        val readings=metrics.zip(listOf(25.6,6.2,29.1,72.0)).associate {it.first.key to SensorValue(it.second,SensorQuality.Ok)}
        val telemetry=Telemetry("2026-10-12T01:39:00Z","2026-10-12T01:40:00Z",readings)
        val record=NutrientRecord(41,"2026-10-12T01:30:00Z","2026-10-12T01:31:00Z",PlantPhase.Vegetatif,980.0,ValidationStatus.Incomplete,listOf(Check("incomplete","missing_config","Parameter belum lengkap"),Check("incomplete","missing_schedule","Jadwal nutrisi belum diatur")),null,"unscheduled",null,null,null)
        val state=OwnerState(signedIn=true,config=RemoteConfig(null,ConfigDraft().json(),null,null),
            dashboard=Dashboard(PlantPhase.Vegetatif,1,"recent_contact","2026-10-12T01:40:00Z",null,telemetry,record,serverTime="2026-10-12T01:40:00Z"),history=listOf(record),telemetry=listOf(telemetry),
            resources=mapOf("config" to ResourceState(fetchedAt="2026-10-12T01:40:00Z")))
        var selected by mutableStateOf("beranda")
        rule.activity.setContent {InofarmTheme {
            CompositionLocalProvider(LocalGlassEffectsEnabled provides (InstrumentationRegistry.getArguments().getString("profileFallback")!="true")) {
            val haze=rememberHazeState()
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Box(Modifier.fillMaxSize().hazeSource(haze)) {
                    ChromeBackdrop(Modifier.matchParentSize())
                    Crossfade(selected,Modifier.fillMaxSize().padding(bottom=84.dp),label="Profile page") {page ->
                        when(page) {"beranda"->DashboardScreen(state);"riwayat"->HistoryScreen(state,vm,{});else->SettingsScreen(state,vm,{})}
                    }
                }
                Box(Modifier.align(Alignment.BottomCenter)) {OwnerNavigation(selected,haze) {selected=it}}
            }
        } } }
        rule.waitForIdle()
        // A short recording contains real stepped frames rather than only settled screenshots.
        val recording=if(InstrumentationRegistry.getArguments().getString("profileRecord")=="true") instrumentation.uiAutomation.executeShellCommand("screenrecord --time-limit 18 /sdcard/Download/liquid-motion-profile.mp4") else null
        if(recording!=null) Thread.sleep(500)
        shell("dumpsys gfxinfo ${instrumentation.targetContext.packageName} reset")
        rule.mainClock.autoAdvance=false
        try {
            repeat(6) {
                listOf("Riwayat","Pengaturan","Dashboard").forEach {label ->
                    rule.onNode(hasText(label) and hasClickAction()).performClick()
                    repeat(24) {rule.mainClock.advanceTimeByFrame();Thread.sleep(16)}
                }
            }
        } finally {rule.mainClock.autoAdvance=true}
        val result=shell("dumpsys gfxinfo ${instrumentation.targetContext.packageName} framestats")
        val sample=java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"liquid-frame-sample-profile.txt")
        sample.writeText(result)
        val memory=java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"glass-memory.txt")
        memory.writeText(shell("dumpsys meminfo ${instrumentation.targetContext.packageName}"))
        shell("cp ${sample.absolutePath} /sdcard/Download/liquid-frame-sample-profile.txt")
        recording?.let {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use {stream->stream.readBytes()}
            val localVideo=java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"motion.mp4")
            shell("cp /sdcard/Download/liquid-motion-profile.mp4 ${localVideo.absolutePath}")
            val media=android.media.MediaMetadataRetriever()
            try {
                media.setDataSource(localVideo.absolutePath)
                val metadata=java.io.File(localVideo.parentFile,"motion-metadata.txt")
                metadata.writeText("durationMs=${media.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)}\nwidth=${media.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)}\nheight=${media.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)}")
                shell("cp ${metadata.absolutePath} /sdcard/Download/liquid-motion-metadata.txt")
                listOf(1L,3L,5L,7L,9L).forEach {second ->
                    media.getFrameAtTime(second*1_000_000,android.media.MediaMetadataRetriever.OPTION_CLOSEST)?.let {bitmap ->
                        val frame=java.io.File(localVideo.parentFile,"motion-$second.png")
                        frame.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
                        shell("cp ${frame.absolutePath} /sdcard/Download/liquid-motion-$second.png")
                        bitmap.recycle()
                    }
                }
            } finally {media.release()}
        }
    }

    /** Same 100-row scene in both builds; real injected swipes, no recording. */
    @Test fun historyScrollSample() {
        val context=instrumentation.targetContext
        check(context.packageName.endsWith(".performance"))
        val session=SessionStore(context);session.clear()
        val vm=OwnerViewModel(OwnerRepository(ApiClient({session.baseUrl},{null},false),session))
        val records=(1L..100L).map {id ->
            NutrientRecord(id,"2026-10-12T01:30:00Z","2026-10-12T01:31:00Z",PlantPhase.Vegetatif,980.0,
                ValidationStatus.Incomplete,listOf(Check("incomplete","missing_config","Parameter belum lengkap")),
                null,"unscheduled",null,null,null)
        }
        val state=OwnerState(signedIn=true,history=records)
        rule.activity.setContent {InofarmTheme {HistoryScreen(state,vm,{})}}
        rule.waitForIdle()
        val folder=context.getExternalFilesDir(null)
        java.io.File(folder,"scroll-start-memory.txt").writeText(shell("dumpsys meminfo ${context.packageName}"))
        shell("dumpsys gfxinfo ${context.packageName} reset")
        repeat(3) {
            repeat(6) {rule.onNode(hasScrollAction()).performTouchInput {swipeUp(durationMillis=300)};rule.waitForIdle()}
            repeat(6) {rule.onNode(hasScrollAction()).performTouchInput {swipeDown(durationMillis=300)};rule.waitForIdle()}
        }
        java.io.File(folder,"scroll-frames.txt").writeText(shell("dumpsys gfxinfo ${context.packageName} framestats"))
        java.io.File(folder,"scroll-end-memory.txt").writeText(shell("dumpsys meminfo ${context.packageName}"))
    }
}
