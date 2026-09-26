package moe.shizuku.manager.extras

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.module.ModulesActivity
import moe.shizuku.manager.settings.LabFeaturesActivity
import moe.shizuku.manager.shell.ShellTutorialActivity
import moe.shizuku.manager.shizutest.ShizuTestActivity
import moe.shizuku.manager.ui.compose.ExpressiveCard
import moe.shizuku.manager.ui.compose.MainTab
import moe.shizuku.manager.ui.compose.MainTabBar
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.openMainTab

class ExtrasActivity : AppActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ShizukuExpressiveTheme {
                Scaffold(
                    modifier = Modifier,
                    contentWindowInsets = WindowInsets(0.dp),
                    topBar = {
                        TopAppBar(
                            title = { Text(text = stringResource(R.string.extras_title)) },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        )
                    },
                    bottomBar = {
                        MainTabBar(selected = MainTab.EXTRAS, onSelect = ::openMainTab)
                    }
                ) { innerPadding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            ExpressiveCard(
                                icon = R.drawable.ic_adb_24dp,
                                title = stringResource(R.string.modules_title),
                                body = stringResource(R.string.modules_settings_summary),
                                onClick = {
                                    startActivity(Intent(this@ExtrasActivity, ModulesActivity::class.java))
                                }
                            )
                        }
                        item {
                            ExpressiveCard(
                                icon = R.drawable.ic_baseline_link_24,
                                title = stringResource(R.string.lab_features_title),
                                body = stringResource(R.string.lab_features_summary),
                                onClick = {
                                    startActivity(Intent(this@ExtrasActivity, LabFeaturesActivity::class.java))
                                }
                            )
                        }
                        item {
                            ExpressiveCard(
                                icon = R.drawable.ic_terminal_24,
                                title = stringResource(R.string.home_terminal_title),
                                body = stringResource(R.string.home_terminal_description),
                                onClick = {
                                    startActivity(Intent(this@ExtrasActivity, ShellTutorialActivity::class.java))
                                }
                            )
                        }
                        item {
                            ExpressiveCard(
                                icon = R.drawable.ic_server_ok_24dp,
                                title = stringResource(R.string.extras_shizutest_title),
                                body = stringResource(R.string.extras_shizutest_summary),
                                onClick = {
                                    startActivity(Intent(this@ExtrasActivity, ShizuTestActivity::class.java))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
