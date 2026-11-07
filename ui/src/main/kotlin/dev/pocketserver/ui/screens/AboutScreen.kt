package dev.pocketserver.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.pocketserver.ui.components.LabeledValue
import dev.pocketserver.ui.components.MonoBlock
import dev.pocketserver.ui.components.PocketCard
import dev.pocketserver.ui.components.SectionHeader
import dev.pocketserver.ui.theme.CodeFont
import dev.pocketserver.ui.theme.TerminalGreen
import dev.pocketserver.ui.theme.TerminalMuted

@Composable
fun AboutScreen(
    versionName: String,
    versionCode: Int,
    deviceAbis: List<String>,
    targetSdk: Int,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text(
                    text = "PocketServer",
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = CodeFont,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "A standalone local web-development stack for Android.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Version $versionName · build $versionCode · Phase 1",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            PocketCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Runtime architecture", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "PocketServer does not reuse desktop glibc binaries. Service executables must be " +
                            "AArch64 Bionic-compatible ELF files packaged in a checksum-verified runtime bundle. " +
                            "The app supervises processes directly from app-private storage and performs real " +
                            "TCP/HTTP health checks before reporting a service as running.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MonoBlock(
                        "device ABIs: ${deviceAbis.joinToString(", ").ifBlank { "unknown" }}\n" +
                            "supported: arm64-v8a\n" +
                            "targetSdk: $targetSdk (legacy exec-from-data requirement)\n" +
                            "minSdk: 26",
                    )
                    Text(
                        text = "The targetSdk 28 choice keeps unprivileged execution from app-private storage working " +
                            "on modern Android. It is a deliberate sideload-oriented engineering decision; this build " +
                            "is not a Google Play submission.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            SectionHeader(
                title = "Roadmap",
                subtitle = "Phases are shipped only when they actually work.",
            )
        }
        item {
            PocketCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PhaseRow(
                        number = "01",
                        title = "Foundation",
                        body = "Runtime installer, process supervisor, service manager, native probe, CI, Compose shell.",
                        done = true,
                    )
                    PhaseRow(
                        number = "02",
                        title = "Apache + PHP + PHP-FPM",
                        body = "Runtime bundle integration, PHP configuration, extension verification, web root wiring.",
                        done = false,
                    )
                    PhaseRow(
                        number = "03",
                        title = "MariaDB + MySQL",
                        body = "Bootstrap, users, SQL import/export, backup and restore, graceful shutdown.",
                        done = false,
                    )
                    PhaseRow(
                        number = "04",
                        title = "phpMyAdmin + Database Manager",
                        body = "Authenticated browser access and full database management UI.",
                        done = false,
                    )
                    PhaseRow(
                        number = "05",
                        title = "Nginx + web server manager",
                        body = "Server blocks, reverse proxy and config generation.",
                        done = false,
                    )
                    PhaseRow(
                        number = "06",
                        title = "Projects + files",
                        body = "SAF import/export, project templates, richer file operations.",
                        done = false,
                    )
                    PhaseRow(
                        number = "07-08",
                        title = "Composer, Laravel, Node, Python",
                        body = "Additional runtime packages and project workflows.",
                        done = false,
                    )
                    PhaseRow(
                        number = "09-10",
                        title = "Polish and hardening",
                        body = "Configuration UX, security review, regression matrix and release builds.",
                        done = false,
                    )
                }
            }
        }

        item {
            PocketCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Security posture", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "• Loopback binding by default\n" +
                            "• LAN access requires explicit confirmation\n" +
                            "• Runtime checksum verification when enforced\n" +
                            "• Zip-slip and symlink rejection\n" +
                            "• Keystore-backed random database secrets\n" +
                            "• No unrestricted shell execution\n" +
                            "• App-private runtime, data, logs and backups",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                LabeledValue("Package", "dev.pocketserver", mono = true)
                LabeledValue("License", "Source provided as-is")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Runtime binaries are not bundled in this APK. Their licenses and redistribution terms are the " +
                    "integrator's responsibility. Oracle MySQL redistribution requires particular care; MariaDB is " +
                    "the simpler default for bundling.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun PhaseRow(number: String, title: String, body: String, done: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = if (done) Icons.Outlined.CheckCircle else Icons.Outlined.Schedule,
            contentDescription = null,
            tint = if (done) TerminalGreen else TerminalMuted,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$number · $title",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            color = if (done) TerminalGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.extraSmall,
        ) {
            Text(
                text = if (done) "shipped" else "planned",
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (done) TerminalGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
