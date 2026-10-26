package com.example.pocketreborn.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pocketreborn.R

@Composable
fun OnboardingScreen(
    onSelectLanguage: (String) -> Unit,
    onEnableNotifications: () -> Unit,
    onFinish: () -> Unit
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var selectedLanguage by rememberSaveable { mutableStateOf(supportedDeviceLanguage()) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        when (step) {
            0 -> {
                Text(
                    stringResource(R.string.welcome_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.welcome_subtitle),
                    style = MaterialTheme.typography.titleMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
                BenefitCard(
                    icon = { Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.benefit_save_title),
                    body = stringResource(R.string.benefit_save_body)
                )
                Spacer(Modifier.height(12.dp))
                BenefitCard(
                    icon = { Icon(Icons.Default.Explore, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.benefit_discover_title),
                    body = stringResource(R.string.benefit_discover_body)
                )
                Spacer(Modifier.height(12.dp))
                BenefitCard(
                    icon = { Icon(Icons.Default.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.benefit_listen_title),
                    body = stringResource(R.string.benefit_listen_body)
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.next))
                }
            }

            1 -> {
                Text(
                    stringResource(R.string.choose_language),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.language_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                supportedAppLanguages.forEach { language ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedLanguage = language.tag
                            onSelectLanguage(language.tag)
                        }.padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = language.tag == selectedLanguage,
                            onClick = {
                                selectedLanguage = language.tag
                                onSelectLanguage(language.tag)
                            }
                        )
                        Text(language.nativeName, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.next))
                }
            }

            else -> {
                Text(
                    stringResource(R.string.stay_in_loop),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.notifications_intro), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onEnableNotifications, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.enable_notifications))
                }
                OutlinedButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.not_now))
                }
            }
        }
    }
}

@Composable
private fun BenefitCard(
    icon: @Composable () -> Unit,
    title: String,
    body: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Column(Modifier.padding(start = 14.dp)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun LanguageSelectorDialog(
    selectedLanguage: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_language)) },
        text = {
            Column {
                supportedAppLanguages.forEach { language ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(language.tag) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedLanguage.equals(language.tag, true),
                            onClick = { onSelect(language.tag) }
                        )
                        Text(language.nativeName)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
