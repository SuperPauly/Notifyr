package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ActionItem
import com.example.data.model.NotificationEntity

@Composable
fun TextReplyDialog(
    notification: NotificationEntity,
    action: ActionItem,
    onDismiss: () -> Unit,
    onSubmitReply: (replyText: String) -> Unit
) {
    var replyText by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = action.label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column {
                Text(
                    text = "Responding to '${notification.title}' from ${notification.fromApp}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = replyText,
                    onValueChange = {
                        replyText = it
                        if (it.isNotBlank()) isError = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reply_input_field"),
                    label = { Text("Your response") },
                    placeholder = { Text("Type reply notes, status, or token...") },
                    isError = isError,
                    supportingText = if (isError) {
                        { Text("Please enter a response before sending") }
                    } else null,
                    minLines = 3,
                    maxLines = 6
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (replyText.isBlank()) {
                        isError = true
                    } else {
                        onSubmitReply(replyText.trim())
                    }
                },
                modifier = Modifier.testTag("reply_submit_button")
            ) {
                Text("Send Response")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
