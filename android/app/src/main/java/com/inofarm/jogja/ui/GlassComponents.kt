package com.inofarm.jogja.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.inofarm.jogja.R
import dev.chrisbanes.haze.*

internal val LocalBottomSpace = compositionLocalOf { 0.dp }
internal val LocalGlassEffectsEnabled = compositionLocalOf { true }

@Composable internal fun glassEffectsEnabled(): Boolean {
    val context=LocalContext.current
    val capable=remember(context) {Build.VERSION.SDK_INT>=31 && !(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice}
    return capable && LocalGlassEffectsEnabled.current
}

@Composable internal fun DeviceGlassPolicy(content: @Composable ()->Unit) {
    val context=LocalContext.current
    val power=remember(context) {context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager}
    var saving by remember {mutableStateOf(power.isPowerSaveMode)}
    DisposableEffect(context,power) {
        val receiver=object: android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: android.content.Intent?) {saving=power.isPowerSaveMode}
        }
        val filter=android.content.IntentFilter(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        if(Build.VERSION.SDK_INT>=33) context.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED)
        else { @Suppress("DEPRECATION") context.registerReceiver(receiver,filter) }
        onDispose {context.unregisterReceiver(receiver)}
    }
    CompositionLocalProvider(LocalGlassEffectsEnabled provides !saving,content=content)
}
internal data class ChromeActions(val refresh: () -> Unit = {}, val logout: () -> Unit = {})
internal val LocalChromeActions = compositionLocalOf { ChromeActions() }

internal enum class FarmGlyph { Home, History, Settings, Person, People, Calendar, Clock, Leaf, Sprout, Drop, Thermometer, Room, Humidity, Flask, Chip, Database, WifiOff, Send, Bell, Info, Back, Arrow, Chevron, Export, Plus, Trash }

/** Local vector glyphs, drawn independently of the reference bitmaps. */
@Composable internal fun FarmIcon(glyph: FarmGlyph, modifier: Modifier = Modifier, tint: Color = FarmColors.Ink) {
    Canvas(modifier.size(24.dp).clearAndSetSemantics {}) {
        val stroke = Stroke(width = 1.75f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        scale(size.width / 24f, size.height / 24f, Offset.Zero) {
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, Offset(x1,y1), Offset(x2,y2), 1.75f, StrokeCap.Round)
            fun path(block: Path.() -> Unit) = drawPath(Path().apply(block),tint,style=stroke)
            fun circle(x: Float,y: Float,r: Float) = drawCircle(tint,r,Offset(x,y),style=stroke)
            fun rect(x: Float,y: Float,w: Float,h: Float,r: Float=2f) = drawRoundRect(tint,Offset(x,y),androidx.compose.ui.geometry.Size(w,h),androidx.compose.ui.geometry.CornerRadius(r),style=stroke)
            when(glyph) {
                FarmGlyph.Home, FarmGlyph.Room -> { path { moveTo(3f,11f);lineTo(12f,3f);lineTo(21f,11f);moveTo(5f,10f);lineTo(5f,21f);lineTo(10f,21f);lineTo(10f,14f);lineTo(14f,14f);lineTo(14f,21f);lineTo(19f,21f);lineTo(19f,10f) } }
                FarmGlyph.History -> { rect(5f,3f,14f,18f);line(9f,7f,15f,7f);line(9f,12f,15f,12f);line(9f,17f,15f,17f) }
                FarmGlyph.Settings -> { circle(12f,12f,3f);path { moveTo(9f,3f);lineTo(15f,3f);lineTo(16f,6f);lineTo(19f,7f);lineTo(22f,10f);lineTo(20f,13f);lineTo(20f,17f);lineTo(17f,19f);lineTo(14f,19f);lineTo(12f,22f);lineTo(9f,20f);lineTo(6f,20f);lineTo(3f,17f);lineTo(4f,13f);lineTo(2f,10f);lineTo(5f,7f);lineTo(8f,6f);close() } }
                FarmGlyph.Person -> { circle(12f,7f,3.3f);path { moveTo(5f,21f);cubicTo(5f,12f,19f,12f,19f,21f) } }
                FarmGlyph.People -> { circle(8f,7f,3f);circle(17f,10f,2.5f);path { moveTo(2f,21f);cubicTo(2f,12f,14f,12f,14f,21f);moveTo(14f,17f);cubicTo(17f,14f,22f,16f,22f,21f) } }
                FarmGlyph.Calendar -> { rect(3f,5f,18f,16f);line(3f,10f,21f,10f);line(7f,2f,7f,7f);line(17f,2f,17f,7f) }
                FarmGlyph.Clock -> { circle(12f,12f,9f);line(12f,6f,12f,12f);line(12f,12f,16f,14f) }
                FarmGlyph.Drop, FarmGlyph.Humidity -> { path { moveTo(12f,2f);cubicTo(10f,6f,4f,12f,4f,16f);cubicTo(4f,24f,20f,24f,20f,16f);cubicTo(20f,12f,14f,6f,12f,2f);close() } }
                FarmGlyph.Thermometer -> { path { moveTo(9f,14f);lineTo(9f,5f);cubicTo(9f,1f,15f,1f,15f,5f);lineTo(15f,14f);cubicTo(21f,22f,3f,24f,9f,14f) };line(12f,6f,12f,17f);circle(12f,18f,1.5f) }
                FarmGlyph.Leaf, FarmGlyph.Sprout -> { path { moveTo(5f,18f);cubicTo(0f,6f,13f,6f,21f,2f);cubicTo(22f,15f,17f,23f,5f,18f);moveTo(4f,22f);cubicTo(7f,15f,13f,12f,17f,7f) };if(glyph==FarmGlyph.Sprout) path { moveTo(7f,15f);cubicTo(1f,15f,2f,8f,2f,6f);cubicTo(8f,7f,10f,9f,10f,12f) } }
                FarmGlyph.Flask -> { path { moveTo(8f,2f);lineTo(16f,2f);moveTo(9f,2f);lineTo(9f,10f);lineTo(3f,21f);lineTo(21f,21f);lineTo(15f,10f);lineTo(15f,2f) } }
                FarmGlyph.Chip -> { rect(6f,6f,12f,12f);for(i in listOf(8f,12f,16f)) {line(i,2f,i,6f);line(i,18f,i,22f);line(2f,i,6f,i);line(18f,i,22f,i)} }
                FarmGlyph.Database -> { drawOval(tint,Offset(4f,3f),androidx.compose.ui.geometry.Size(16f,6f),style=stroke);path { moveTo(4f,6f);lineTo(4f,18f);cubicTo(4f,23f,20f,23f,20f,18f);lineTo(20f,6f);moveTo(4f,12f);cubicTo(4f,17f,20f,17f,20f,12f) } }
                FarmGlyph.WifiOff -> { path { moveTo(2f,8f);cubicTo(7f,3f,17f,3f,22f,8f);moveTo(6f,12f);cubicTo(9f,9f,15f,9f,18f,12f) };circle(12f,18f,1f);line(3f,3f,21f,21f) }
                FarmGlyph.Send -> { path { moveTo(2f,10f);lineTo(22f,2f);lineTo(14f,22f);lineTo(11f,13f);close();moveTo(11f,13f);lineTo(22f,2f) } }
                FarmGlyph.Bell -> { path { moveTo(3f,19f);cubicTo(9f,13f,5f,5f,12f,4f);cubicTo(19f,5f,15f,13f,21f,19f);close();moveTo(9f,22f);cubicTo(10f,24f,14f,24f,15f,22f) } }
                FarmGlyph.Info -> { circle(12f,12f,9f);line(12f,11f,12f,17f);circle(12f,7f,.5f) }
                FarmGlyph.Back -> {line(5f,12f,20f,12f);line(5f,12f,11f,6f);line(5f,12f,11f,18f)}
                FarmGlyph.Arrow -> {line(4f,12f,20f,12f);line(20f,12f,14f,6f);line(20f,12f,14f,18f)}
                FarmGlyph.Chevron -> {line(9f,5f,16f,12f);line(16f,12f,9f,19f)}
                FarmGlyph.Export -> {line(12f,2f,12f,16f);line(12f,16f,7f,11f);line(12f,16f,17f,11f);path {moveTo(3f,16f);lineTo(3f,22f);lineTo(21f,22f);lineTo(21f,16f)}}
                FarmGlyph.Plus -> {line(12f,4f,12f,20f);line(4f,12f,20f,12f)}
                FarmGlyph.Trash -> {rect(6f,6f,12f,16f,1f);line(3f,6f,21f,6f);rect(9f,2f,6f,4f,1f)}
            }
        }
    }
}

@Composable internal fun IconMedallion(glyph: FarmGlyph, modifier: Modifier = Modifier, size: Dp = 42.dp, dark: Boolean = false) {
    Box(modifier.size(size).background(if(dark) Color(0xFF568355).copy(alpha=.55f) else Color(0xFFCDE8D4),CircleShape),contentAlignment=Alignment.Center) {
        FarmIcon(glyph,Modifier.size(size*.52f),if(dark) Color.White else FarmColors.Ink)
    }
}

@Composable internal fun BotanicalWatermark(modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val color=Color(0xFFB6D4AC).copy(alpha=.15f)
        val leaves=Path().apply {
            moveTo(size.width,size.height);cubicTo(size.width*.6f,size.height*.48f,size.width*.2f,size.height*.6f,0f,size.height*.1f)
            cubicTo(size.width*.65f,0f,size.width*.85f,size.height*.3f,size.width,size.height)
            moveTo(size.width,size.height*.85f);cubicTo(size.width*.7f,size.height*.25f,size.width*.9f,size.height*.12f,size.width,0f)
            moveTo(size.width,size.height*.85f);cubicTo(size.width*.45f,size.height*.55f,size.width*.25f,size.height*.9f,size.width*.2f,size.height*.95f)
            cubicTo(size.width*.7f,size.height,size.width*.85f,size.height*.95f,size.width,size.height*.85f)
        }
        drawPath(leaves,color)
        drawLine(color,Offset(size.width*.1f,size.height*.15f),Offset(size.width,size.height),1.dp.toPx())
    }
}

/** Quiet botanical backdrop under chrome; it is sampled and blurred, while fields remain opaque. */
@Composable internal fun ChromeBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        drawRect(background)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFC9E3D1),background),center=Offset(size.width*.2f,size.height*.96f),radius=size.width*.45f),size.width*.45f,Offset(size.width*.2f,size.height*.96f))
        drawCircle(Brush.radialGradient(listOf(Color(0xFFE6EBDD),background),center=Offset(size.width*.85f,size.height*.95f),radius=size.width*.36f),size.width*.36f,Offset(size.width*.85f,size.height*.95f))
    }
}

@Composable internal fun BrandHeader(back: (() -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    val actions=LocalChromeActions.current
    Column {
        if(back!=null) TextButton(back,contentPadding=PaddingValues(0.dp),modifier=Modifier.heightIn(min=48.dp).semantics { contentDescription="Kembali ke Pengaturan" }) {
            FarmIcon(FarmGlyph.Back);Spacer(Modifier.width(12.dp));Text("Pengaturan",color=FarmColors.Muted,style=MaterialTheme.typography.bodyLarge)
        }
        Row(Modifier.fillMaxWidth().padding(vertical=if(back!=null) 4.dp else 8.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_inofarm),null,Modifier.size(28.dp),tint=Color.Unspecified)
            Spacer(Modifier.width(8.dp))
            Text("Inofarm Jogja",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            if(back==null) Box {
                IconButton({menu=true},Modifier.background(FarmColors.Soft,CircleShape).size(40.dp).semantics {contentDescription="Menu akun"}) { FarmIcon(FarmGlyph.Person) }
                DropdownMenu(menu,{menu=false}) {
                    DropdownMenuItem({Text("Refresh")},{menu=false;actions.refresh()})
                    DropdownMenuItem({Text("Keluar akun")},{menu=false;actions.logout()})
                }
            }
        }
    }
}

@Composable internal fun PageHeader(title: String, subtitle: String? = null, back: (() -> Unit)? = null) {
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize()) {
            BotanicalWatermark(Modifier.align(Alignment.TopEnd).size(142.dp,160.dp).offset(x=16.dp,y=14.dp))
        }
        Column(verticalArrangement=Arrangement.spacedBy(if(back!=null) 8.dp else 12.dp)) {
            BrandHeader(back)
            PageHeading(title,subtitle)
        }
    }
}

@Composable internal fun GlassSurface(modifier: Modifier = Modifier, haze: HazeState? = null, content: @Composable BoxScope.() -> Unit) {
    val enabled=glassEffectsEnabled()
    val own=rememberHazeState()
    val style=remember { HazeStyle(backgroundColor=background,tint=HazeTint(FarmColors.Navigation.copy(alpha=.78f)),blurRadius=18.dp,noiseFactor=.025f,fallbackTint=HazeTint(FarmColors.Navigation)) }
    Box(modifier.shadow(4.dp,FarmShape.Navigation,ambientColor=green.copy(alpha=.12f),spotColor=green.copy(alpha=.16f)).clip(FarmShape.Navigation)) {
        if(enabled && haze==null) Box(Modifier.matchParentSize().hazeSource(own).background(Brush.linearGradient(listOf(Color.White,FarmColors.Hero,FarmColors.Soft,background))))
        if(enabled) Box(Modifier.matchParentSize().hazeEffect(haze ?: own,style) {
            blurEnabled=true
            inputScale=HazeInputScale.Auto
        }) else Box(Modifier.matchParentSize().background(FarmColors.Navigation))
        Box(Modifier.matchParentSize().border(1.dp,Brush.verticalGradient(listOf(Color.White,Color(0xFFA9D5C1),Color.White)),FarmShape.Navigation).padding(2.dp).border(1.dp,Color.White.copy(alpha=.6f),FarmShape.Navigation))
        content()
    }
}

@Composable internal fun GlassSegments(labels: List<String>, selected: Int, icons: List<FarmGlyph>, change: (Int)->Unit) {
    val scale=LocalDensity.current.fontScale
    if(scale>1.5f) {
        Column(Modifier.selectableGroup(),verticalArrangement=Arrangement.spacedBy(4.dp)) { labels.forEachIndexed { i,label ->
            Surface(Modifier.fillMaxWidth().selectable(i==selected,role=Role.Tab,onClick={change(i)}).heightIn(min=48.dp),color=if(i==selected) green else FarmColors.Soft,shape=FarmShape.Navigation) {
                Text(label,Modifier.padding(12.dp),color=if(i==selected) Color.White else FarmColors.Ink)
            }
        } }
    } else GlassSurface(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(5.dp).selectableGroup()) {
            val width=maxWidth/labels.size
            val offset by animateDpAsState(width*selected,spring(dampingRatio=.85f,stiffness=450f),label="Segment pill")
            Box(Modifier.offset(x=offset).width(width).height(46.dp).background(Brush.verticalGradient(listOf(Color(0xFF296C50),green)),FarmShape.Navigation).border(1.dp,Color.White.copy(alpha=.45f),FarmShape.Navigation))
            Row { labels.forEachIndexed { i,label ->
                Row(Modifier.weight(1f).heightIn(min=46.dp).selectable(i==selected,role=Role.Tab,onClick={change(i)}).padding(horizontal=8.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                    FarmIcon(icons[i],Modifier.size(22.dp),if(i==selected) Color.White else FarmColors.Ink)
                    Spacer(Modifier.width(8.dp));Text(label,style=MaterialTheme.typography.titleMedium,color=if(i==selected) Color.White else FarmColors.Ink)
                }
            } }
        }
    }
}

@Composable internal fun InfoNote(text: String, warning: Boolean = true) {
    Row(Modifier.fillMaxWidth().background(if(warning) FarmColors.WarningSurface else FarmColors.Soft,FarmShape.Control).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        FarmIcon(FarmGlyph.Info,tint=if(warning) FarmColors.Warning else green)
        Text(text,style=MaterialTheme.typography.bodyMedium,color=if(warning) FarmColors.Warning else FarmColors.Ink)
    }
}

@Composable internal fun FormFooter(save: ()->Unit, cancel: ()->Unit, text: String = "Simpan konfigurasi", enabled: Boolean = true, haze: HazeState? = null) {
    GlassSurface(Modifier.fillMaxWidth().testTag("formFooter"),haze) {
        val large=LocalDensity.current.fontScale>1.5f
        if(large) Column(Modifier.padding(8.dp)) {
            PrimaryAction(text,save,Modifier.fillMaxWidth(),enabled,shape=FarmShape.Navigation)
            TextButton(cancel,Modifier.fillMaxWidth()) { Text("Batal") }
        } else Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(cancel,Modifier.weight(.8f).heightIn(min=48.dp),shape=FarmShape.Navigation,border=BorderStroke(1.dp,Color(0xFFA9D5C1))) {Text("Batal")}
            PrimaryAction(text,save,Modifier.weight(1.5f),enabled,shape=FarmShape.Navigation)
        }
    }
}

@Composable internal fun BotanicalHero(modifier: Modifier = Modifier, leavesOnly: Boolean = false, content: @Composable BoxScope.()->Unit) {
    Box(modifier.fillMaxWidth().clip(FarmShape.Card).background(green)) {
        LocalHeroImage(if(leavesOnly) R.drawable.leaf_hero else R.drawable.melon_hero,Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color(0xFF174B35),Color(0xFF174B35).copy(alpha=.92f),Color.Transparent))))
        content()
    }
}
