package moe.shizuku.manager.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.R

@Composable
fun ThemeColorDialog(
    current: ThemeColorOption,
    onDismiss: () -> Unit,
    onSelect: (ThemeColorOption) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_theme_color)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (option in ThemeColorOption.entries) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (option.seed == Color.Unspecified) {
                            Surface(
                                modifier = Modifier.size(24.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                androidx.compose.foundation.layout.Box(
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Palette,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        } else {
                            Surface(
                                modifier = Modifier.size(24.dp),
                                shape = CircleShape,
                                color = option.seed,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {}
                        }
                        Text(
                            text = stringResource(option.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        RadioButton(
                            selected = option == current,
                            onClick = { onSelect(option) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
fun ThemeColorDot(
    option: ThemeColorOption,
    modifier: Modifier = Modifier
) {
    if (option.seed == Color.Unspecified) {
        Surface(
            modifier = modifier.size(24.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {}
    } else {
        Surface(
            modifier = modifier.size(24.dp),
            shape = CircleShape,
            color = option.seed,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {}
    }
}
