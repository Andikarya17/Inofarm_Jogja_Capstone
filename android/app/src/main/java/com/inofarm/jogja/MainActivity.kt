package com.inofarm.jogja

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.*
import com.inofarm.jogja.data.*
import com.inofarm.jogja.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun displayTime(value: String?): String = value?.let { runCatching { OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.of("Asia/Jakarta")).format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss 'WIB'")) }.getOrNull() } ?: "Waktu tidak tersedia"

class MainActivity: ComponentActivity() {
    private lateinit var viewModel: OwnerViewModel
    private var pickerPending = false
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val text = viewModel.takeExport(); pickerPending = false
        if(uri!=null && text==null) viewModel.message("Ekspor terputus saat proses aplikasi dibuat ulang. Silakan ekspor kembali.",true)
        if (uri != null && text != null) lifecycleScope.launch {
            try { withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("File tidak dapat dibuka") }; viewModel.message("Ekspor tersimpan.") }
            catch (_: Exception) { viewModel.message("Gagal menyimpan file ekspor.",true) }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        pickerPending=savedInstanceState?.getBoolean("exportPickerPending") ?: false
        val session = SessionStore(applicationContext)
        val repository = OwnerRepository(ApiClient({ session.baseUrl }, { session.readToken() }, BuildConfig.DEBUG), session,ReadCache(applicationContext,session))
        viewModel = ViewModelProvider(this, object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T = OwnerViewModel(repository) as T
        })[OwnerViewModel::class.java]
        if(pickerPending && !viewModel.hasExport()) viewModel.message("Ekspor terputus karena proses aplikasi dibuat ulang; ekspor kembali setelah menutup pemilih dokumen.",true)
        setContent {
            InofarmTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.exportReady) { if(state.exportReady) { viewModel.pickerLaunched();pickerPending=true;exportLauncher.launch("inofarm-history.csv") } }
                OwnerApp(viewModel) { viewModel.export() }
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("exportPickerPending",pickerPending);super.onSaveInstanceState(outState) }
}

