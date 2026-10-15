package com.goalmaker.app.ui.pcsignin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.theme.AppTheme
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Settings, Sign in on a PC (ADR 0020): scan the QR code on the PC's sign-in screen, see which PC
 * asked, and sign it in or not. Google Play services shows the camera, so the app asks for no camera
 * permission; a phone without it is told to use the emailed code on the PC.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PcSignInScreen(viewModel: PcSignInViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val scan = {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { code -> viewModel.scanned(code.rawValue.orEmpty()) }
            .addOnFailureListener { viewModel.scannerUnavailable() }
        Unit
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { ScreenTitle(stringResource(R.string.pc_sign_in_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.density.pagePadding.dp, vertical = 8.dp),
        ) {
            val device = state.device.orEmpty()
            when (state.step) {
                PcSignInStep.READY -> {
                    Text(stringResource(R.string.pc_sign_in_intro), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = scan) { Text(stringResource(R.string.pc_sign_in_scan)) }
                }
                PcSignInStep.CHECKING -> LinearProgressIndicator(Modifier.fillMaxWidth())
                PcSignInStep.ASK -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.pc_sign_in_ask, device),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        Text(
                            stringResource(R.string.pc_sign_in_ask_warning),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textMuted,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = viewModel::approve) { Text(stringResource(R.string.pc_sign_in_approve)) }
                            OutlinedButton(onClick = viewModel::decline) { Text(stringResource(R.string.pc_sign_in_decline)) }
                        }
                    }
                }
                else -> {
                    val (said, button) = when (state.step) {
                        PcSignInStep.APPROVED -> stringResource(R.string.pc_sign_in_approved, device) to null
                        PcSignInStep.DECLINED -> stringResource(R.string.pc_sign_in_declined, device) to R.string.pc_sign_in_scan_another
                        PcSignInStep.NOT_A_CODE -> stringResource(R.string.pc_sign_in_not_a_code) to R.string.pc_sign_in_scan_again
                        PcSignInStep.GONE -> stringResource(R.string.pc_sign_in_gone) to R.string.pc_sign_in_scan_again
                        PcSignInStep.OFFLINE -> stringResource(R.string.pc_sign_in_offline) to R.string.pc_sign_in_scan_again
                        PcSignInStep.FAILED -> stringResource(R.string.pc_sign_in_failed) to R.string.pc_sign_in_scan_again
                        else -> stringResource(R.string.pc_sign_in_no_scanner) to null
                    }
                    Text(
                        said,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    when {
                        button != null -> Button(onClick = scan) { Text(stringResource(button)) }
                        state.step == PcSignInStep.APPROVED -> Button(onClick = onBack) { Text(stringResource(R.string.pc_sign_in_done)) }
                    }
                }
            }
            Text(
                stringResource(R.string.pc_sign_in_safety),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textMuted,
            )
        }
    }
}
