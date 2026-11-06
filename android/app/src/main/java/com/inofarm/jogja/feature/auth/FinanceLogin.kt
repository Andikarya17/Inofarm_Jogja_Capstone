package com.inofarm.jogja.feature.auth

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.inofarm.jogja.R
import com.inofarm.jogja.core.design.BotanicalBackdrop
import com.inofarm.jogja.core.design.FarmControlIcon
import com.inofarm.jogja.core.design.FarmAttention
import com.inofarm.jogja.core.design.FarmIcon
import com.inofarm.jogja.core.design.FarmPageTitle
import com.inofarm.jogja.core.design.FarmShape
import com.inofarm.jogja.core.design.FarmSpace
import com.inofarm.jogja.core.design.ForestScheme
import com.inofarm.jogja.core.network.LocalNetworkAccess
import com.inofarm.jogja.feature.Notice

@Composable
internal fun FinanceLogin(
    state: AuthUiState,
    endpoint: String?,
    onLogin: (String, String, String) -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var awaitingPermission by remember { mutableStateOf(false) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (awaitingPermission && granted && endpoint != null) onLogin(endpoint, email, password)
        if (!granted) permissionMessage = "Izinkan akses jaringan lokal untuk tersambung ke server Inofarm."
        awaitingPermission = false
    }
    val enabled = endpoint != null && !state.loading && !awaitingPermission &&
        email.isNotBlank() && password.isNotBlank()
    val submit: () -> Unit = {
        if (enabled) {
            focus.clearFocus()
            passwordVisible = false
            permissionMessage = null
            if (LocalNetworkAccess.requiredFor(endpoint, Build.VERSION.SDK_INT) &&
                ContextCompat.checkSelfPermission(context, LocalNetworkAccess.PERMISSION) != PackageManager.PERMISSION_GRANTED
            ) {
                awaitingPermission = true
                permission.launch(LocalNetworkAccess.PERMISSION)
            } else onLogin(endpoint, email, password)
        }
    }

    Box(Modifier.fillMaxSize()) {
        BotanicalBackdrop(R.drawable.background_login)
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
            .background(MaterialTheme.colorScheme.background.copy(alpha = .92f)))
        BoxWithConstraints(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = FarmSpace.Section),
            contentAlignment = Alignment.TopCenter
        ) {
            val spacious = maxHeight >= 600.dp
            val compactIntro = !spacious || LocalDensity.current.fontScale > 1.3f
            Column(
                Modifier.widthIn(max = 420.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight).padding(top = if (spacious) 48.dp else 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(FarmSpace.Section, Alignment.CenterVertically)
            ) {
                val colors = MaterialTheme.colorScheme
                val ink = colors.onBackground
                val intro = if (compactIntro) Modifier.clip(FarmShape.Control).background(colors.background)
                    .padding(FarmSpace.Small) else Modifier
                Column(intro.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Inofarm", style = MaterialTheme.typography.headlineLarge, color = ink)
                    Text("Catatan operasional greenhouse melon", textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium, color = ink)
                }
                LoginPanel {
                    FarmPageTitle("Masuk")
                    val labelAbove = LocalDensity.current.fontScale > 1.5f
                    val fieldsEnabled = !state.loading && !awaitingPermission
                    val fieldColors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ForestScheme.surface,
                        unfocusedContainerColor = ForestScheme.surface,
                        disabledContainerColor = ForestScheme.surface,
                        errorContainerColor = ForestScheme.surface
                    )
                    if (labelAbove) LoginFieldLabel("Email")
                    OutlinedTextField(
                        email, { email = it }, label = if (labelAbove) null else ({ Text("Email") }),
                        shape = FarmShape.Control, colors = fieldColors,
                        enabled = fieldsEnabled, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier.fillMaxWidth().testTag("finance-email")
                            .semantics { if (labelAbove) contentDescription = "Email" }
                    )
                    if (labelAbove) LoginFieldLabel("Password")
                    OutlinedTextField(
                        password, { password = it }, label = if (labelAbove) null else ({ Text("Password") }),
                        shape = FarmShape.Control, colors = fieldColors,
                        enabled = fieldsEnabled, singleLine = true,
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
                        modifier = Modifier.fillMaxWidth().testTag("finance-password")
                            .semantics { if (labelAbove) contentDescription = "Password" }
                    )
                    if (endpoint == null) Notice("Alamat server belum dikonfigurasi untuk aplikasi ini. Hubungi pengelola.", true)
                    state.error?.let { Notice(it, true) }
                    when (state.logoutStatus) {
                        LogoutStatus.Confirmed -> Notice("Keluar. Draf dan antrean akun ini tetap tersimpan.")
                        LogoutStatus.RevocationUnconfirmed ->
                            FarmAttention("Keluar dari perangkat. Draf tetap tersimpan. Pencabutan sesi server belum terkonfirmasi.")
                        null -> Unit
                    }
                    permissionMessage?.let { Notice(it, true) }
                    Button(
                        onClick = submit, enabled = enabled, shape = FarmShape.Control,
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = ForestScheme.surfaceVariant,
                            disabledContentColor = ForestScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("finance-login")
                    ) { Text(if (state.loading) "Memproses…" else "Masuk") }
                }
            }
        }
    }
}

@Composable
private fun LoginPanel(content: @Composable ColumnScope.() -> Unit) {
    MaterialTheme(colorScheme = ForestScheme) {
        Surface(
            Modifier.fillMaxWidth(), shape = FarmShape.Hero,
            color = ForestScheme.background, contentColor = ForestScheme.onBackground,
            border = BorderStroke(1.dp, ForestScheme.outlineVariant)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium),
                content = content)
        }
    }
}

@Composable
private fun LoginFieldLabel(label: String) {
    Text(label, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.labelLarge)
}
