package com.arsal.ragmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RAGMateApp() }
    }
}

@Composable
fun RAGMateApp(vm: ChatViewModel = viewModel()) {
    val state by vm.state
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.isLoading) {
        val target = (state.messages.size - 1).coerceAtLeast(0)
        listState.animateScrollToItem(target)
    }

    MaterialTheme(colorScheme = ragColorScheme()) {
        Scaffold(
            topBar = { AppHeader() },
            bottomBar = {
                Composer(
                    value = input,
                    enabled = !state.isLoading,
                    onValueChange = { input = it },
                    onSend = {
                        if (input.isNotBlank()) {
                            vm.send(input)
                            input = ""
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
            ) {
                if (state.messages.isEmpty()) Welcome() else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(state.messages) { message -> MessageBubble(message) }
                        if (state.isLoading) {
                            item { ThinkingBubble() }
                        }
                    }
                }

                state.error?.let { error ->
                    Card(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(error, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = vm::clearError) { Text("Dismiss") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ragColorScheme() = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFF9B8CFF),
    secondary = Color(0xFF71D7C3),
    background = Color(0xFF0B0B10),
    surface = Color(0xFF15151D),
    surfaceVariant = Color(0xFF20202A),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppHeader() {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF9B8CFF), Color(0xFF71D7C3)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✦", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("RAGMate", fontWeight = FontWeight.Bold)
                    Text("Grounded knowledge assistant", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}

@Composable
private fun Welcome() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("✦", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Ask your knowledge base", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "RAGMate retrieves the most relevant document chunks, then asks Gemini to answer from that evidence.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text("Semantic search") })
            AssistChip(onClick = {}, label = { Text("Grounded answers") })
        }
    }
}

@Composable
private fun MessageBubble(message: ChatUiMessage) {
    when (message) {
        is ChatUiMessage.User -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    shape = RoundedCornerShape(20.dp, 20.dp, 5.dp, 20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(0.84f),
                ) {
                    Text(message.text, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        is ChatUiMessage.Assistant -> {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✦", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text("RAGMate", fontWeight = FontWeight.SemiBold)
                    if (message.grounded) {
                        Spacer(Modifier.width(8.dp))
                        Text("GROUNDED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp),
                ) {
                    Column(Modifier.padding(15.dp)) {
                        Text(message.text, style = MaterialTheme.typography.bodyLarge)
                        if (message.sources.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(10.dp))
                            Text("Retrieved sources", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            message.sources.forEach { source -> SourceCard(source) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceCard(source: com.arsal.ragmobile.data.SourceItem) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background,
        onClick = { expanded = !expanded },
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(source.source, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text("${source.score * 100.0f}%", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
            }
            Text("chunk ${source.chunkId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AnimatedVisibility(expanded) {
                Text(
                    source.text,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ThinkingBubble() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("✦", color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(8.dp))
        Text("Searching documents and thinking…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Composer(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                placeholder = { Text("Ask your documents…") },
                maxLines = 4,
                shape = RoundedCornerShape(22.dp),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onSend,
                enabled = enabled && value.isNotBlank(),
                modifier = Modifier.height(56.dp),
                shape = CircleShape,
            ) {
                Text("➤")
            }
        }
    }
}
