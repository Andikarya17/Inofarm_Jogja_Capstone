package com.inofarm.jogja.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.inofarm.jogja.R
import com.inofarm.jogja.BuildConfig
import com.inofarm.jogja.data.ApiEndpoint
import kotlinx.coroutines.delay

@Composable fun OwnerApp(vm: OwnerViewModel, export: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = key(state.sessionId) { rememberNavController() }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "beranda"
    if (state.restoring) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(),contentAlignment=Alignment.Center) {
            Column(Modifier.widthIn(max=480.dp).fillMaxWidth().padding(FarmSpace.Page),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                LoadingBar();Text("Memulihkan sesi dan data tersimpan…")
            }
        }
        return
    }
    if (!state.signedIn) { Login(vm, state); return }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, state.sessionId) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.watchExpiry() }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.resume()
            else if (event == Lifecycle.Event.ON_PAUSE) vm.pause()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(lifecycle, route) {
        if (route == "beranda") lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.resume()
            while (true) { delay(30_000); vm.refreshDashboard() }
        }
    }
    var settingsPage by rememberSaveable { mutableStateOf("hub") }
    val haze = dev.chrisbanes.haze.rememberHazeState()
    val main = route != "pekerja" && (route != "pengaturan" || settingsPage == "hub")
    val density = LocalDensity.current
    val showNavigation = main && WindowInsets.ime.getBottom(density) == 0
    var navigationHeightPx by remember { mutableIntStateOf(0) }
    val bottomSpace = if (showNavigation) with(density) { navigationHeightPx.toDp() } else 0.dp
    fun navigateMain(path: String) {
        if (path == nav.currentDestination?.route) return
        nav.navigate(path) {
            popUpTo(nav.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            // Saved stacks can also be indexed by the start destination. Restoring that
            // key while selecting Dashboard can restore Pengaturan instead of beranda.
            restoreState = path != "beranda"
        }
    }
    CompositionLocalProvider(LocalChromeActions provides ChromeActions(vm::refresh, vm::logout),
        LocalBottomSpace provides 0.dp) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = FarmSpace.MaxWidth).fillMaxSize().then(if(glassEffectsEnabled()) Modifier.hazeSource(haze) else Modifier)) {
            ChromeBackdrop(Modifier.matchParentSize())
            Column(Modifier.fillMaxSize()) {
                state.error?.let { Banner(it, true) }
                state.notice?.let { Banner(it) }
                NavHost(navController = nav, startDestination = "beranda", modifier = Modifier.weight(1f).padding(bottom=bottomSpace).testTag("ownerContent"),
                    enterTransition = { fadeIn(tween(FarmMotion.Navigation)) },
                    exitTransition = { fadeOut(tween(FarmMotion.Press)) },
                    popEnterTransition = { fadeIn(tween(FarmMotion.Navigation)) },
                    popExitTransition = { fadeOut(tween(FarmMotion.Press)) }) {
                    composable("beranda") { DashboardScreen(state) { settingsPage="hub";navigateMain("pengaturan") } }
                    composable("riwayat") { HistoryScreen(state, vm, export) }
                    composable("pengaturan") { SettingsScreen(state, vm, { nav.navigate("pekerja") }, settingsPage, { settingsPage=it }) }
                    composable("pekerja") { WorkersScreen(state, vm) { nav.popBackStack() } }
                }
            }
            }
            if(showNavigation) Box(Modifier.align(Alignment.BottomCenter).onSizeChanged { navigationHeightPx = it.height }) {
                OwnerNavigation(route, haze, ::navigateMain)
            }
        }
    }
}

@Composable internal fun OwnerNavigation(route: String, haze: dev.chrisbanes.haze.HazeState? = null, navigate: (String) -> Unit) {
    val destinations=listOf(Triple("beranda","Dashboard",FarmGlyph.Home),Triple("riwayat","Riwayat",FarmGlyph.History),Triple("pengaturan","Pengaturan",FarmGlyph.Settings))
    val selected=destinations.indexOfFirst { it.first==route }.coerceAtLeast(0)
    Box(Modifier.fillMaxWidth().testTag("ownerNavigation").padding(horizontal=12.dp,vertical=8.dp),contentAlignment=Alignment.Center) {
        GlassSurface(Modifier.widthIn(max=FarmSpace.MaxWidth).fillMaxWidth(),haze) {
            if(LocalDensity.current.fontScale>1.5f) Column(Modifier.selectableGroup().padding(6.dp)) {
                destinations.forEachIndexed { i,item ->
                    Surface(Modifier.fillMaxWidth().selectable(i==selected,role=Role.Tab,onClick={navigate(item.first)}),color=if(i==selected) green else androidx.compose.ui.graphics.Color.Transparent,shape=FarmShape.Navigation) {
                        Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
                            FarmIcon(item.third,tint=if(i==selected) androidx.compose.ui.graphics.Color.White else FarmColors.Ink)
                            Spacer(Modifier.width(8.dp));Text(item.second,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge.copy(lineHeight=24.sp),textAlign=TextAlign.Center,color=if(i==selected) androidx.compose.ui.graphics.Color.White else FarmColors.Ink)
                        }
                    }
                }
            } else BoxWithConstraints(Modifier.fillMaxWidth().padding(6.dp).selectableGroup()) {
                val width=maxWidth/3
                val offset by androidx.compose.animation.core.animateDpAsState(width*selected,androidx.compose.animation.core.spring(dampingRatio=.85f,stiffness=450f),label="Navigation pill")
                Box(Modifier.offset(x=offset).width(width).height(62.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF2B7154),green)),FarmShape.Navigation).border(1.dp,androidx.compose.ui.graphics.Color.White.copy(alpha=.7f),FarmShape.Navigation))
                Row {
                    destinations.forEachIndexed { i,item ->
                        Column(Modifier.weight(1f).heightIn(min=62.dp).selectable(i==selected,role=Role.Tab,onClick={navigate(item.first)}).padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            FarmIcon(item.third,Modifier.size(24.dp),if(i==selected) androidx.compose.ui.graphics.Color.White else FarmColors.Ink)
                            Text(item.second,style=MaterialTheme.typography.labelMedium,color=if(i==selected) androidx.compose.ui.graphics.Color.White else FarmColors.Ink)
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun Login(vm: OwnerViewModel, state: OwnerState) {
    var base by rememberSaveable { mutableStateOf(ApiEndpoint.forLogin(vm.repository.baseUrl, BuildConfig.DEFAULT_API_BASE_URL)) }
    var showConnection by rememberSaveable { mutableStateOf(base.isBlank()) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    var connectionError by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingLogin by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    val context = LocalContext.current
    val localPermission = "android.permission.ACCESS_LOCAL_NETWORK"
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val login = pendingLogin
        pendingLogin = null
        if (granted) login?.let { vm.login(it.first, it.second, it.third) }
        else connectionError = "Izinkan jaringan lokal untuk terhubung ke server di komputer. Jika izin telah ditolak, aktifkan lewat pengaturan izin aplikasi."
    }
    val baseError = if (attempted && base.isBlank()) "Alamat server belum disiapkan." else null
    val emailError = if (attempted && !email.contains('@')) "Isi alamat email yang benar." else null
    val passwordError = if (attempted && password.isBlank()) "Isi password." else null
    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(FarmSpace.Section),
            verticalArrangement = Arrangement.spacedBy(FarmSpace.Section)) {
            Spacer(Modifier.height(FarmSpace.Small))
            Icon(painterResource(R.drawable.ic_inofarm), null, Modifier.size(48.dp), tint = androidx.compose.ui.graphics.Color.Unspecified)
            PageHeading("Inofarm Jogja", "Pantau tandon dan pencatatan nutrisi melon NFT.")
            Section("Masuk sebagai owner / leader") {
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true,
                    shape = FarmShape.Control, isError = emailError != null,
                    supportingText = emailError?.let { message -> { Text(message) } },
                    modifier = Modifier.fillMaxWidth().testTag("email"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true,
                    shape = FarmShape.Control, isError = passwordError != null,
                    supportingText = passwordError?.let { message -> { Text(message) } },
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().testTag("password"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                state.error?.let { Banner(it, true) }
                connectionError?.let { Banner(it, true) }
                PrimaryAction(if (state.loading) "Memuat…" else "Masuk", onClick = {
                    attempted = true
                    connectionError = null
                    if (base.isBlank()) showConnection = true
                    if (base.isNotBlank() && email.contains('@') && password.isNotBlank()) {
                        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= 37 && android.net.Uri.parse(base).host == "10.0.2.2" && context.checkSelfPermission(localPermission) != PackageManager.PERMISSION_GRANTED) {
                            pendingLogin = Triple(base, email, password)
                            permissionRequest.launch(localPermission)
                        } else vm.login(base, email, password)
                    }
                }, enabled = !state.loading && pendingLogin == null, modifier = Modifier.fillMaxWidth().testTag("login"))
                TextButton(onClick = { showConnection = !showConnection }, enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth().testTag("connectionSettings")) {
                    Text(if (showConnection) "Tutup pengaturan koneksi" else "Pengaturan koneksi")
                }
                if (showConnection) {
                    OutlinedTextField(base, { base = it }, label = { Text("Alamat server") }, placeholder = { Text("https://server/api/v1/") },
                        singleLine = true, shape = FarmShape.Control, isError = baseError != null, enabled = !state.loading,
                        supportingText = { Text(baseError ?: "Ubah hanya jika pengelola menyediakan alamat server lain.") },
                        modifier = Modifier.fillMaxWidth().testTag("apiUrl"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                }
            }
            Text("Akun disediakan pengelola sistem. Pekerja mencatat PPM melalui ESP32.",
                style = MaterialTheme.typography.bodyMedium, color = FarmColors.Muted)
        }
    }
}
