package com.phantom.ai.ui.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantom.ai.ui.viewmodel.ChatViewModel
import android.content.Intent
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.platform.LocalContext

enum class MessageType {
    USER, ASSISTANT, THINKING, TOOL, SYSTEM
}

enum class SessionTab(val label: String, val icon: String) {
    LUNA("Agent A", "🌙"),
    CELESTE("Agent B", "👑"),
    LUC("Agent C", "🔧")
}

data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val messageType: MessageType = if (isUser) MessageType.USER else MessageType.ASSISTANT,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun ChatPanelContent(
    onClose: () -> Unit,
    viewModel: ChatViewModel,
    lunaUrl: String,
    celesteUrl: String,
    codexUrl: String,
    onHeightDrag: (Float) -> Unit = {}
) {
    var inputText by remember { mutableStateOf("") }
    var showCommandsDialog by remember { mutableStateOf(false) }
    val messages by viewModel.messages.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val listState = rememberLazyListState()
    val autocompletePaths by viewModel.autocompletePaths.collectAsState()
    val context = LocalContext.current

    // Connect to active session's streamer
    val activeUrl = when (activeTab) {
        SessionTab.LUNA -> lunaUrl
        SessionTab.CELESTE -> celesteUrl
        SessionTab.LUC -> codexUrl
    }

    LaunchedEffect(activeUrl, activeTab) {
        if (activeUrl.isNotBlank()) {
            viewModel.connect(activeUrl)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(Color(0xF0101020))
    ) {
        // Drag Handle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF13132A))
                .padding(vertical = 8.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        onHeightDrag(dragAmount)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF3E3E5C))
            )
        }

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A2E))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(com.phantom.ai.R.drawable.ic_orb),
                    contentDescription = "O.R.B.",
                    modifier = Modifier.size(24.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Column {
                    Text(
                        "O.R.B.",
                        color = Color(0xFFE0D4FF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    val (statusColor, statusText) = when (connectionStatus) {
                        "connected"    -> Color(0xFF4CAF50) to "● live"
                        "connecting"   -> Color(0xFFFF9800) to "● connecting"
                        "reconnecting" -> Color(0xFFFF9800) to "● reconnecting"
                        else           -> Color(0xFF666680) to "● offline"
                    }
                    Text(statusText, color = statusColor, fontSize = 10.sp)
                }
                IconButton(onClick = { showCommandsDialog = true }, modifier = Modifier.size(24.dp).padding(start = 4.dp)) {
                    Icon(Icons.Default.Info, contentDescription = "Phone Commands", tint = Color(0xFF8B7FB8), modifier = Modifier.size(20.dp))
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = { viewModel.clearMessages() }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Chat", tint = Color(0xFF8B7FB8), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8B7FB8))
                }
            }
        }

        // Session tab selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF13132A))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SessionTab.entries.forEach { tab ->
                val isActive = activeTab == tab
                val tabColor = if (isActive) Color(0xFF6C3CE0) else Color(0xFF252540)
                val textColor = if (isActive) Color.White else Color(0xFF8B7FB8)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(tabColor)
                        .clickable { viewModel.switchTab(tab) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${tab.icon} ${tab.label}",
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Messages
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(messages) { message ->
                MessageBubble(message, activeTab)
            }
        }

        // Autocomplete Popup
        if (autocompletePaths.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF252540))
            ) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp)
                ) {
                    items(autocompletePaths) { path ->
                        Text(
                            text = path,
                            color = Color(0xFFE0D4FF),
                            fontSize = 13.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // Replace the typed path with the selected full path
                                    val lastAt = inputText.lastIndexOf("@/")
                                    val lastSlash = inputText.lastIndexOf("/")
                                    
                                    if (lastAt != -1) {
                                        inputText = inputText.substring(0, lastAt) + "@" + path
                                    } else if (lastSlash != -1) {
                                        inputText = inputText.substring(0, lastSlash) + path
                                    } else {
                                        inputText += path
                                    }
                                    viewModel.clearAutocomplete()
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }

        // Input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A2E))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    val intent = Intent(context, com.phantom.ai.ui.screens.FilePickerActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra("active_url", activeUrl)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF252540))
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add File", tint = Color(0xFFE0D4FF), modifier = Modifier.size(24.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF252540))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (inputText.isEmpty()) {
                    val hint = when (activeTab) {
                        SessionTab.LUNA -> "Talk to Agent A...  (~ local Termux, ~~ cmssh)"
                        SessionTab.CELESTE -> "Talk to Agent B...  (~ local Termux, ~~ cmssh)"
                        SessionTab.LUC -> "Talk to Agent C...  (~ local Termux, ~~ cmssh)"
                    }
                    Text(hint, color = Color(0xFF5C5C7A), fontSize = 14.sp)
                }
                BasicTextField(
                    value = inputText,
                    onValueChange = { 
                        inputText = it 
                        // Trigger autocomplete if typing a path
                        val lastWord = it.split(" ").lastOrNull() ?: ""
                        if (lastWord.startsWith("~")) {
                            viewModel.clearAutocomplete()
                        } else if (lastWord.startsWith("@/") || lastWord.startsWith("/")) {
                            viewModel.fetchAutocompletePaths(activeUrl, lastWord)
                        } else {
                            viewModel.clearAutocomplete()
                        }
                    },
                    textStyle = TextStyle(color = Color(0xFFE0D4FF), fontSize = 14.sp),
                    cursorBrush = SolidColor(Color(0xFFBB86FC)),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendPrompt(activeUrl, inputText)
                        inputText = ""
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF6C3CE0))
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }

    if (showCommandsDialog) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable { showCommandsDialog = false },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E1E36))
                    .clickable(enabled = false) {} // block clicks from closing
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Phone Commands", fontWeight = FontWeight.Bold, color = Color(0xFFE0D4FF), fontSize = 18.sp)
                
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CommandRow("phone_notify: {title, message}", "Push message to phone popup")
                    CommandRow("phone_tts: {text, speed}", "Speak text via device TTS")
                    CommandRow("phone_status", "Get battery %, network, screen state")
                    CommandRow("phone_context", "Get active app, screen text, notifications")
                    CommandRow("phone_logcat: {pkg, lines}", "Stream crash logs for app")
                    CommandRow("phone_screenshot", "Live WebP screenshot of screen")
                }
                
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = { showCommandsDialog = false }) {
                        Text("Close", color = Color(0xFFBB86FC), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandRow(cmd: String, desc: String) {
    Column {
        Text(cmd, color = Color(0xFFE0D4FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(desc, color = Color(0xFF9B8EC4), fontSize = 12.sp)
    }
}
@Composable
private fun MessageBubble(message: ChatMessage, activeTab: SessionTab) {
    val aiLabel = when (activeTab) {
        SessionTab.LUNA -> "🌙 Agent A"
        SessionTab.CELESTE -> "👑 Agent B"
        SessionTab.LUC -> "🔧 Agent C"
    }

    when (message.messageType) {
        MessageType.USER -> {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                        .background(Color(0xFF6C3CE0))
                        .padding(12.dp)
                ) {
                    Text(text = message.content, color = Color(0xFFE0D4FF), fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }
        MessageType.THINKING -> {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1A1A30))
                        .alpha(0.7f)
                        .padding(10.dp)
                ) {
                    Column {
                        Text("💭 thinking", color = Color(0xFF7B68A8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = message.content,
                            color = Color(0xFF9B8EC4),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 8
                        )
                    }
                }
            }
        }
        MessageType.TOOL -> {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(0.6f)
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = message.content,
                        color = Color(0xFF5A8F6A),
                        fontSize = 11.sp
                    )
                }
            }
        }
        MessageType.SYSTEM -> {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text = message.content, color = Color(0xFF666680), fontSize = 11.sp)
            }
        }
        else -> {
            // ASSISTANT — normal bubble with name label
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                Column {
                    Text(
                        text = aiLabel,
                        color = Color(0xFF7B68A8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                    )
                    Box(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                            .background(Color(0xFF1E1E36))
                            .padding(12.dp)
                    ) {
                        Text(text = message.content, color = Color(0xFFE0D4FF), fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
            }
        }
    }
}
