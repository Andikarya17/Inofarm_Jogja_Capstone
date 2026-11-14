package com.inofarm.jogja.feature.auth

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.inofarm.jogja.core.design.FarmAttention
import com.inofarm.jogja.core.design.FarmControlIcon
import com.inofarm.jogja.core.design.FarmIcon
import com.inofarm.jogja.core.design.FarmIconTile
import com.inofarm.jogja.core.design.FarmLeafMotif
import com.inofarm.jogja.core.design.FarmMotion
import com.inofarm.jogja.core.design.FarmPageTitle
import com.inofarm.jogja.core.design.FarmShape
import com.inofarm.jogja.core.design.FarmSpace
import com.inofarm.jogja.core.design.FarmTone
import com.inofarm.jogja.core.design.FarmType
import com.inofarm.jogja.core.design.ForestScheme
import com.inofarm.jogja.core.design.farmPressable
import com.inofarm.jogja.core.design.farmStaggerEntrance
import com.inofarm.jogja.core.network.LocalNetworkAccess
import com.inofarm.jogja.feature.Notice

@Composable
internal fun FinanceLogin(
    state: AuthUiState,
    endpoint: String?,
    onLogin: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    var rememberedEmail by remember { mutableStateOf(LastLoginEmail.read(context)) }
    var email by rememberSaveable { mutableStateOf(rememberedEmail) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var awaitingPermission by remember { mutableStateOf(false) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val autofill = LocalAutofillManager.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (awaitingPermission && granted && endpoint != null) onLogin(endpoint, email, password)
        if (!granted) permissionMessage = "Izinkan akses jaringan lokal untuk tersambung ke Inofarm."
        awaitingPermission = false
    }
    val enabled = endpoint != null && !state.loading && !awaitingPermission &&
        email.isNotBlank() && password.isNotBlank()
    val submit: () -> Unit = {
        if (enabled) {
            focus.clearFocus()
            passwordVisible = false
            permissionMessage = null
            // Lets a password manager offer to save or update what was typed.
            autofill?.commit()
            if (LocalNetworkAccess.requiredFor(endpoint, Build.VERSION.SDK_INT) &&
                ContextCompat.checkSelfPermission(context, LocalNetworkAccess.PERMISSION) != PackageManager.PERMISSION_GRANTED
            ) {
                awaitingPermission = true
                permission.launch(LocalNetworkAccess.PERMISSION)
            } else onLogin(endpoint, email, password)
        }
    }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(state.error) {
        if (state.error != null) {
            repeat(3) { shake.animateTo(6f, tween(50)); shake.animateTo(-6f, tween(50)) }
            shake.animateTo(0f, tween(50))
        }
    }
    val colors = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val view = LocalView.current
    DisposableEffect(view) {
        var host = view.context
        while (host is android.content.ContextWrapper && host !is android.app.Activity) host = host.baseContext
        val controller = (host as? android.app.Activity)?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (controller != null && previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    val buttonShape = RoundedCornerShape(16.dp)

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        val viewport = maxHeight
        val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val tall = viewport >= 600.dp && !imeOpen
        val target = when {
            tall -> (viewport * 0.47f).coerceIn(300.dp, 420.dp)
            else -> 72.dp + statusTop
        }
        val forestHeight by animateDpAsState(target, tween(FarmMotion.Navigation), label = "Tinggi kanopi")
        val overlap = 36.dp
        val sideInsets = WindowInsets.displayCutout.union(WindowInsets.navigationBars).only(WindowInsetsSides.Horizontal)
        Box(Modifier.fillMaxWidth().height(forestHeight).background(ForestScheme.background)) {
            FarmLeafMotif(Modifier.align(Alignment.TopEnd).size(200.dp))
            Column(
                Modifier.fillMaxSize().windowInsetsPadding(sideInsets).statusBarsPadding().padding(bottom = overlap)
                    .padding(horizontal = FarmSpace.Section),
                verticalArrangement = Arrangement.Center
            ) {
                if (tall) Box(Modifier.farmStaggerEntrance(0, "login-0", FarmMotion.LoginStagger)) {
                    FarmIconTile(FarmIcon.Greenhouse, tone = FarmTone.Forest, size = 52.dp)
                }
                Column(Modifier.farmStaggerEntrance(1, "login-1", FarmMotion.LoginStagger).padding(top = if (tall) 14.dp else 0.dp)) {
                    Text(
                        "Inofarm", Modifier.semantics { heading() },
                        style = if (tall) FarmType.Wordmark else FarmType.Wordmark.copy(fontSize = 28.sp, lineHeight = 32.sp),
                        color = ForestScheme.onBackground
                    )
                    if (tall) Text("Catatan operasional greenhouse melon", style = MaterialTheme.typography.bodyMedium,
                        color = ForestScheme.onSurfaceVariant)
                }
            }
        }
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(forestHeight - overlap))
            Surface(
                Modifier.fillMaxWidth().heightIn(min = viewport - (forestHeight - overlap))
                    .farmStaggerEntrance(2, "login-2", FarmMotion.LoginStagger),
                shape = FarmShape.Sheet, color = colors.surface,
                border = if (dark) BorderStroke(1.dp, colors.outlineVariant) else null
            ) {
                Box(Modifier.fillMaxWidth().windowInsetsPadding(sideInsets), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 420.dp).fillMaxWidth()
                        .navigationBarsPadding().graphicsLayer { translationX = shake.value.dp.toPx() }
                        .padding(horizontal = FarmSpace.Section).padding(top = FarmSpace.Section, bottom = FarmSpace.Large),
                    verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)
                ) {
                    FarmPageTitle("Masuk", "Pakai akun yang dibuat pengelola.")
                    val fieldsEnabled = !state.loading && !awaitingPermission
                    // The action shares the label's row, so a remembered email adds no gap between the two fields.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { LoginFieldLabel("Email") }
                        if (rememberedEmail.isNotEmpty() && email == rememberedEmail) TextButton(
                            onClick = { LastLoginEmail.forget(context); rememberedEmail = ""; email = "" },
                            enabled = fieldsEnabled,
                            modifier = Modifier.testTag("finance-forget-email")
                                .semantics { onClick("Kosongkan email yang diingat perangkat ini", null) }
                        ) { Text("Bukan saya") }
                    }
                    OutlinedTextField(
                        email, { email = it }, placeholder = { Text("email@contoh.com") },
                        leadingIcon = { FarmControlIcon(FarmIcon.Mail, null) },
                        shape = FarmShape.Tile, enabled = fieldsEnabled, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier.fillMaxWidth().testTag("finance-email").semantics {
                            contentDescription = "Email"
                            contentType = ContentType.EmailAddress + ContentType.Username
                        }
                    )
                    LoginFieldLabel("Password")
                    OutlinedTextField(
                        password, { password = it }, leadingIcon = { FarmControlIcon(FarmIcon.Lock, null) },
                        shape = FarmShape.Tile, enabled = fieldsEnabled, singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                enabled = fieldsEnabled,
                                modifier = Modifier.testTag("finance-password-toggle")
                            ) {
                                FarmControlIcon(
                                    if (passwordVisible) FarmIcon.EyeOff else FarmIcon.Eye,
                                    if (passwordVisible) "Sembunyikan password" else "Tampilkan password"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth().testTag("finance-password").semantics {
                            contentDescription = "Password"
                            contentType = ContentType.Password
                        }
                    )
                    Column(Modifier.fillMaxWidth().animateContentSize(tween(FarmMotion.State)),
                        verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                        if (endpoint == null) Notice("Alamat aplikasi belum diatur. Hubungi pengelola.", true)
                        state.error?.let { Notice(it, true) }
                        when (state.logoutStatus) {
                            LogoutStatus.Confirmed -> Notice("Sudah keluar. Draf dan catatan yang belum terkirim tetap tersimpan.")
                            LogoutStatus.RevocationUnconfirmed ->
                                FarmAttention("Sudah keluar dari perangkat ini. Draf tetap tersimpan. Akses akun belum dipastikan tercabut.")
                            null -> Unit
                        }
                        permissionMessage?.let { Notice(it, true) }
                    }
                    val press = remember { MutableInteractionSource() }
                    Button(
                        onClick = submit, enabled = enabled, shape = buttonShape, interactionSource = press,
                        colors = if (state.loading || awaitingPermission) ButtonDefaults.buttonColors(
                            disabledContainerColor = colors.primary, disabledContentColor = colors.onPrimary) else ButtonDefaults.buttonColors(),
                        modifier = Modifier.fillMaxWidth().height(56.dp).testTag("finance-login")
                            .farmPressable(press, FarmMotion.PressScaleButton, buttonShape)
                    ) {
                        AnimatedContent(
                            state.loading || awaitingPermission,
                            transitionSpec = { fadeIn(tween(FarmMotion.LoginFeedback)) togetherWith fadeOut(tween(FarmMotion.LoginFeedback)) },
                            label = "Status masuk"
                        ) { busy ->
                            Row(
                                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)
                            ) {
                                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                                Text(if (busy) "Memproses…" else "Masuk", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    Text("Lupa password? Minta pengelola mengatur ulang.", Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                }
            }
        }
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(ForestScheme.background))
    }
}

@Composable
private fun LoginFieldLabel(label: String) {
    Text(label, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
}
