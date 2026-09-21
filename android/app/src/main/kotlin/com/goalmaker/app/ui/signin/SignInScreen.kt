package com.goalmaker.app.ui.signin

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.ui.components.GoalMakerLogo
import com.goalmaker.app.R
import com.goalmaker.app.application.auth.DevSignIn

/** The signed-out screen: email, then the 6-digit code. */
@Composable
fun SignInScreen(viewModel: SignInViewModel, backendLabel: String?) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GoalMakerLogo(size = 40.dp)
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = stringResource(R.string.sign_in_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    AnimatedContent(targetState = state.step, label = "sign-in step") { step ->
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            when (step) {
                                SignInStep.EMAIL -> EmailStep(state, viewModel)
                                SignInStep.CODE -> CodeStep(state, viewModel)
                            }
                            state.error?.let { error ->
                                Text(
                                    text = errorText(error, state.errorDetail),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
                if (backendLabel != null) {
                    Text(
                        text = stringResource(R.string.sign_in_local_backend, backendLabel),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmailStep(state: SignInUiState, viewModel: SignInViewModel) {
    OutlinedTextField(
        value = state.email,
        onValueChange = viewModel::onEmailChange,
        label = { Text(stringResource(R.string.sign_in_email_label)) },
        supportingText = { Text(stringResource(R.string.sign_in_email_hint)) },
        singleLine = true,
        enabled = !state.busy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { viewModel.sendCode() }),
        modifier = Modifier.fillMaxWidth(),
    )
    PrimaryAction(label = stringResource(R.string.sign_in_send_code), busy = state.busy, onClick = viewModel::sendCode)
    // Dev builds on the local stack: the account that needs no email at all.
    if (state.hasDevSignIn) {
        TextButton(onClick = viewModel::signInAsDev, enabled = !state.busy) {
            Text(stringResource(R.string.sign_in_dev_account, DevSignIn.EMAIL))
        }
    }
}

@Composable
private fun CodeStep(state: SignInUiState, viewModel: SignInViewModel) {
    Text(
        text = stringResource(R.string.sign_in_code_sent, state.email.trim()),
        style = MaterialTheme.typography.bodyLarge,
    )
    OutlinedTextField(
        value = state.code,
        onValueChange = viewModel::onCodeChange,
        label = { Text(stringResource(R.string.sign_in_code_label)) },
        singleLine = true,
        enabled = !state.busy,
        textStyle = MaterialTheme.typography.headlineMedium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { viewModel.verifyCode() }),
        modifier = Modifier.fillMaxWidth(),
    )
    PrimaryAction(label = stringResource(R.string.sign_in_verify), busy = state.busy, onClick = viewModel::verifyCode)
    Column {
        TextButton(onClick = viewModel::sendCode, enabled = !state.busy) {
            Text(stringResource(R.string.sign_in_resend))
        }
        TextButton(onClick = viewModel::useAnotherEmail, enabled = !state.busy) {
            Text(stringResource(R.string.sign_in_use_other_email))
        }
        // Dev builds on the local stack: the code is in the stack's own mailbox.
        if (state.hasDevSignIn) {
            TextButton(onClick = viewModel::fillCode, enabled = !state.busy) {
                Text(stringResource(R.string.sign_in_fill_code))
            }
        }
    }
}

@Composable
private fun PrimaryAction(label: String, busy: Boolean, onClick: () -> Unit) {
    val loading = stringResource(R.string.loading)
    Spacer(Modifier.height(4.dp))
    if (busy) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            LoadingIndicator(modifier = Modifier.size(48.dp).semantics { contentDescription = loading })
        }
    } else {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun errorText(error: SignInError, detail: String): String = when (error) {
    SignInError.INVALID_EMAIL -> stringResource(R.string.sign_in_error_email)
    SignInError.INVALID_CODE -> stringResource(R.string.sign_in_error_code)
    SignInError.WRONG_CODE -> stringResource(R.string.sign_in_error_wrong_code)
    SignInError.TOO_MANY_REQUESTS -> stringResource(R.string.sign_in_error_rate)
    SignInError.OFFLINE -> stringResource(R.string.sign_in_error_offline)
    SignInError.OTHER -> stringResource(R.string.sign_in_error_other, detail)
}
