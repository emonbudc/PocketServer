package dev.pocketserver.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.pocketserver.core.config.AppSettings
import dev.pocketserver.ui.PortStatus
import dev.pocketserver.ui.components.MonoBlock
import dev.pocketserver.ui.components.PocketCard
import dev.pocketserver.ui.components.SectionHeader
import dev.pocketserver.ui.components.StatusDot
import dev.pocketserver.ui.theme.CodeFont
import dev.pocketserver.ui.theme.TerminalGreen
import dev.pocketserver.ui.theme.TerminalRed
import dev.pocketserver.core.model.ServiceState

@Composable
fun PortsScreen(
    settings: AppSettings,
    status: PortStatus,
    message: String?,
    onRefresh: () -> Unit,
    onApply: (Int, Int, Int, Int) -> Unit,
) {
    var http by remember { mutableStateOf(settings.httpPort.toString()) }
    var php by remember { mutableStateOf(settings.phpFpmPort.toString()) }
    var mariadb by remember { mutableStateOf(settings.mariadbPort.toString()) }
    var mysql by remember { mutableStateOf(settings.mysqlPort.toString()) }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(settings) {
        http = settings.httpPort.toString()
        php = settings.phpFpmPort.toString()
        mariadb = settings.mariadbPort.toString()
        mysql = settings.mysqlPort.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(
                title = "Port manager",
                subtitle = "Ports below 1024 are rejected. Check availability before applying.",
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRefresh) { Text("Recheck") }
        }

        PocketCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PortField("HTTP (Apache / Nginx)", http, { http = it }, status)
                PortField("PHP-FPM", php, { php = it }, status)
                PortField("MariaDB", mariadb, { mariadb = it }, status)
                PortField("Oracle MySQL", mysql, { mysql = it }, status)
                if (localError != null) {
                    Text(localError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (message != null) {
                    MonoBlock(message)
                }
                if (status.conflicts.isNotEmpty()) {
                    MonoBlock(status.conflicts.joinToString("\n") { "• $it" }, error = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val httpValue = http.toIntOrNull()
                            val phpValue = php.toIntOrNull()
                            val mariaValue = mariadb.toIntOrNull()
                            val mysqlValue = mysql.toIntOrNull()
                            if (httpValue == null || phpValue == null || mariaValue == null || mysqlValue == null) {
                                localError = "All ports must be numeric"
                            } else if (listOf(httpValue, phpValue, mariaValue, mysqlValue).any { it !in 1024..65535 }) {
                                localError = "Ports must be between 1024 and 65535"
                            } else if (setOf(httpValue, phpValue, mariaValue, mysqlValue).size != 4) {
                                localError = "Each service needs a distinct port"
                            } else {
                                localError = null
                                onApply(httpValue, phpValue, mariaValue, mysqlValue)
                            }
                        },
                    ) { Text("Apply ports") }
                    OutlinedButton(onClick = onRefresh) { Text("Check availability") }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = "Runtime configuration owns the actual listen directive.",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Changing a port here updates PocketServer's health checks and service definitions. " +
                        "The imported Apache/Nginx/PHP-FPM/database configuration must listen on the same port. " +
                        "Restart affected services after applying changes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PortField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    status: PortStatus,
) {
    val parsed = value.toIntOrNull()
    val check = parsed?.let { status.checks[it] }
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(5)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = CodeFont),
        modifier = Modifier.fillMaxWidth(),
        supportingText = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (check != null) {
                    StatusDot(
                        if (check.ok) ServiceState.RUNNING else ServiceState.FAILED,
                        size = 7,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = check.detail,
                        color = if (check.ok) TerminalGreen else TerminalRed,
                    )
                } else {
                    Text("Not checked", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        trailingIcon = {
            if (check?.ok == true) {
                Text("free", style = MaterialTheme.typography.labelSmall, color = TerminalGreen)
            }
        },
    )
}
