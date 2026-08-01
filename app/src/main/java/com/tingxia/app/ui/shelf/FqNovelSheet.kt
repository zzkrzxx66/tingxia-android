package com.tingxia.app.ui.shelf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tingxia.app.data.remote.FqAudioTone
import com.tingxia.app.data.remote.FqSearchBook

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FqNovelSheet(
    searchResults: List<FqSearchBook>,
    selectedBook: FqSearchBook?,
    tones: List<FqAudioTone>,
    loading: Boolean,
    importing: Boolean,
    onSearch: (String) -> Unit,
    onSelectBook: (FqSearchBook) -> Unit,
    onBack: () -> Unit,
    onImport: (FqSearchBook, FqAudioTone) -> Unit,
    onDismiss: () -> Unit,
) {
    var keyword by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            if (selectedBook == null) {
                Text("番茄真人有声", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("搜索小说") },
                    trailingIcon = { IconButton(onClick = { onSearch(keyword) }) { Icon(Icons.Default.Search, null) } },
                )
                Spacer(Modifier.height(8.dp))
                if (loading) CircularProgressIndicator(Modifier.padding(20.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(searchResults, key = { it.bookId }) { book ->
                        Surface(Modifier.fillMaxWidth().clickable { onSelectBook(book) }) {
                            Column(Modifier.padding(12.dp)) {
                                Text(book.title, style = MaterialTheme.typography.titleMedium)
                                Text(book.author ?: "未知作者", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            } else {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                    Text(selectedBook.title, style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(8.dp))
                Text("选择真人演播版本", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (loading) CircularProgressIndicator(Modifier.padding(20.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tones, key = { it.audioBookId }) { tone ->
                        Surface(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(tone.title, Modifier.weight(1f), maxLines = 2)
                                Button(onClick = { onImport(selectedBook, tone) }, enabled = !importing) {
                                    if (importing) CircularProgressIndicator(Modifier.size(18.dp)) else Text("加入书架")
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
