package moe.shizuku.manager.console

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.monitor.XStatusCollector
import moe.shizuku.manager.ui.compose.MainTab
import moe.shizuku.manager.ui.compose.MainTabBar
import moe.shizuku.manager.ui.compose.MonospaceLog
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.openMainTab
import rikka.shizuku.Shizuku

private data class ConsoleEntry(
    val command: String,
    val output: String,
    val exitCode: Int,
    val elevated: Boolean
)

class ConsoleActivity : AppActivity() {

    private var shellTick by mutableStateOf(0)

    override fun onResume() {
        super.onResume()
        shellTick++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val entries = remember { mutableStateListOf<ConsoleEntry>() }
            var input by remember { mutableStateOf("") }
            var running by remember { mutableStateOf(false) }
            var elevated by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val listState = rememberLazyListState()

            LaunchedEffect(shellTick) {
                elevated = withContext(Dispatchers.IO) {
                    try {
                        Shizuku.pingBinder()
                    } catch (_: Throwable) {
                        false
                    }
                }
            }

            LaunchedEffect(entries.size) {
                if (entries.isNotEmpty()) {
                    listState.animateScrollToItem(entries.size - 1)
                }
            }

            fun runCommand() {
                val cmd = input.trim()
                if (cmd.isBlank() || running) return
                input = ""
                running = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { XStatusCollector.exec(cmd) }
                    val combined = buildString {
                        append(result.stdout.trim())
                        if (result.stderr.isNotBlank()) {
                            if (isNotEmpty()) append('\n')
                            append(result.stderr.trim())
                        }
                        if (isBlank()) append("(exit ${result.exitCode})")
                    }.take(30_000)
                    entries.add(ConsoleEntry(cmd, combined.toString(), result.exitCode, result.elevated))
                    elevated = result.elevated
                    running = false
                }
            }

            ShizukuExpressiveTheme {
                Scaffold(
                    modifier = Modifier,
                    contentWindowInsets = WindowInsets(0.dp),
                    topBar = {
                        TopAppBar(
                            title = { Text(text = stringResource(R.string.tab_console)) },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            actions = {
                                TextButton(onClick = { entries.clear() }) {
                                    Text(text = stringResource(R.string.console_clear))
                                }
                            }
                        )
                    },
                    bottomBar = {
                        MainTabBar(selected = MainTab.CONSOLE, onSelect = ::openMainTab)
                    }
                ) { innerPadding ->
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                text = stringResource(
                                    if (elevated) {
                                        R.string.console_shell_elevated
                                    } else {
                                        R.string.console_shell_local
                                    }
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(entries) { entry ->
                            MonospaceLog(
                                text = buildString {
                                    append("$ ${entry.command}")
                                    if (!entry.elevated) append("  [local]")
                                    append('\n')
                                    append(entry.output)
                                }.toString()
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = input,
                                    onValueChange = { input = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text(text = stringResource(R.string.console_hint)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = { runCommand() })
                                )
                                Button(
                                    enabled = input.isNotBlank() && !running,
                                    onClick = { runCommand() }
                                ) {
                                    Text(text = stringResource(R.string.console_run))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
