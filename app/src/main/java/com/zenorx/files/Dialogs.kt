package com.zenorx.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun DialogHost(vm: FilesViewModel) {
    when (val d = vm.dlg) {
        is Dlg.Rename -> NameDialog(
            "Ubah nama", d.file.name, "Simpan", !d.file.isDirectory,
            onDismiss = { vm.dlg = null }
        ) { vm.dlg = null; vm.rename(d.file, it) }

        is Dlg.NewFolder -> NameDialog(
            "Folder baru", "", "Buat", false,
            onDismiss = { vm.dlg = null }
        ) { vm.dlg = null; vm.createFolder(d.dir, it) }

        is Dlg.NewFile -> NameDialog(
            "File baru", "", "Buat", false,
            onDismiss = { vm.dlg = null }
        ) { vm.dlg = null; vm.createFile(d.dir, it) }

        is Dlg.Delete -> AlertDialog(
            onDismissRequest = { vm.dlg = null },
            title = { Text("Hapus") },
            text = {
                Text(
                    if (d.files.size == 1) "Hapus \"${d.files[0].name}\" secara permanen?"
                    else "Hapus ${d.files.size} item secara permanen?"
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.dlg = null; vm.delete(d.files) }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { vm.dlg = null }) { Text("Batal") } }
        )

        Dlg.Prefs -> PrefsDialog(vm)
        Dlg.About -> AboutDialog { vm.dlg = null }
        null -> {}
    }
    vm.props?.let { PropsDialog(vm, it) }
    vm.dropReq?.let { DropDialog(vm, it) }
    vm.busy?.let { BusyDialog(vm, it) }
    vm.editing?.let { EditorDialog(vm, it) }
}

@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    selectBase: Boolean,
    onDismiss: () -> Unit,
    onOk: (String) -> Unit
) {
    val dot = initial.lastIndexOf('.')
    val end = if (selectBase && dot > 0) dot else initial.length
    var tf by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, end))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = tf,
                onValueChange = { tf = it },
                singleLine = true,
                modifier = Modifier.focusRequester(focus),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (tf.text.isNotBlank()) onOk(tf.text) })
            )
        },
        confirmButton = {
            TextButton(onClick = { onOk(tf.text) }, enabled = tf.text.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = value, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Orange)
        )
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 14.sp)
    }
}

@Composable
fun PrefsDialog(vm: FilesViewModel) {
    AlertDialog(
        onDismissRequest = { vm.dlg = null },
        title = { Text("Pengaturan") },
        confirmButton = { TextButton(onClick = { vm.dlg = null }) { Text("Tutup") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SwitchRow("Mode dual panel (kiri–kanan)", vm.dual) { vm.changeDual(it) }
                SwitchRow("Tampilkan file tersembunyi", vm.showHidden) { vm.changeHidden(it) }
                SwitchRow("Tema gelap", vm.dark) { vm.changeDark(it) }
                SwitchRow("Urutan naik (A→Z, kecil→besar)", vm.sortAsc) { vm.changeSort(vm.sortBy, it) }
                Spacer(Modifier.height(8.dp))
                Text("Urutkan berdasarkan", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                SortBy.values().forEach { s ->
                    RadioRow(s.label, vm.sortBy == s) { vm.changeSort(s, vm.sortAsc) }
                }
                Spacer(Modifier.height(8.dp))
                Text("Saat seret & lepas file", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                RadioRow("Tanya dulu (salin / pindah)", vm.dropMode == 0) { vm.changeDropMode(0) }
                RadioRow("Langsung salin", vm.dropMode == 1) { vm.changeDropMode(1) }
                RadioRow("Langsung pindah", vm.dropMode == 2) { vm.changeDropMode(2) }
            }
        }
    )
}

@Composable
fun AboutDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("zenorX 1.0.0") },
        text = {
            Text(
                "Manajer file dual panel (kiri–kanan) dengan tampilan bergaya Ubuntu.\n\n" +
                    "• Tahan lama lalu seret file ke panel lain atau ke folder\n" +
                    "• Salin, pindah, hapus, ubah nama, zip, ekstrak, bagikan\n" +
                    "• Editor teks bawaan dan pencarian rekursif\n\n" +
                    "Dibuat dengan Kotlin + Jetpack Compose.",
                fontSize = 14.sp
            )
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Tutup") } }
    )
}

@Composable
fun PropsDialog(vm: FilesViewModel, info: PropsInfo) {
    val f = info.file
    val rows = listOf(
        "Nama" to f.name.ifEmpty { "/" },
        "Lokasi" to (f.parent ?: "/"),
        "Tipe" to if (f.isDirectory) "Folder" else mimeOf(f),
        "Ukuran" to (info.bytes?.let { "${humanSize(it)} ($it B)" } ?: "Menghitung…"),
        "Isi" to (info.files?.let { "$it file" } ?: "Menghitung…"),
        "Diubah" to formatFull(f.lastModified()),
        "Izin" to (if (f.canRead()) "r" else "-") + (if (f.canWrite()) "w" else "-") + (if (f.canExecute()) "x" else "-")
    )
    AlertDialog(
        onDismissRequest = { vm.closeProps() },
        title = { Text("Properti") },
        confirmButton = { TextButton(onClick = { vm.closeProps() }) { Text("Tutup") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                rows.forEach { (k, v) ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(k, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, fontSize = 14.sp)
                    }
                }
            }
        }
    )
}

@Composable
fun DropDialog(vm: FilesViewModel, req: DropReq) {
    AlertDialog(
        onDismissRequest = { vm.dropReq = null },
        title = { Text("Seret & lepas") },
        text = {
            Text(
                (if (req.files.size == 1) "\"${req.files[0].name}\"" else "${req.files.size} item") +
                    "\n→ ${req.dest.absolutePath}"
            )
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    vm.dropReq = null
                    vm.panel(req.from).selected = emptySet()
                    vm.transfer(req.files, req.dest, false)
                }) { Text("Salin") }
                TextButton(onClick = {
                    vm.dropReq = null
                    vm.panel(req.from).selected = emptySet()
                    vm.transfer(req.files, req.dest, true)
                }) { Text("Pindahkan") }
            }
        },
        dismissButton = { TextButton(onClick = { vm.dropReq = null }) { Text("Batal") } }
    )
}

@Composable
fun BusyDialog(vm: FilesViewModel, b: Busy) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("${b.title}…") },
        text = {
            Column {
                Text(
                    b.detail.ifEmpty { " " }, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (b.done.toFloat() / b.total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Orange
                )
                Spacer(Modifier.height(6.dp))
                Text("${b.done} / ${b.total}", fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { vm.cancelOp() }) { Text("Batal") } }
    )
}

@Composable
fun EditorDialog(vm: FilesViewModel, file: File) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var original by remember(file) { mutableStateOf<String?>(null) }
    var text by remember(file) { mutableStateOf("") }
    var confirmClose by remember { mutableStateOf(false) }

    LaunchedEffect(file) {
        val s = withContext(Dispatchers.IO) {
            try { file.readText() } catch (e: Exception) { null }
        }
        if (s == null) {
            vm.toast("Gagal membaca file")
            vm.editing = null
        } else {
            original = s
            text = s
        }
    }
    val dirty = original != null && text != original

    Dialog(
        onDismissRequest = { if (dirty) confirmClose = true else vm.editing = null },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = cs.background) {
            Column {
                Row(
                    Modifier.fillMaxWidth().background(Aubergine).height(52.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { if (dirty) confirmClose = true else vm.editing = null }) {
                        Icon(Icons.Filled.Close, "Tutup", tint = Color.White)
                    }
                    Text(
                        file.name + if (dirty) " •" else "",
                        color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        enabled = dirty,
                        onClick = {
                            val t = text
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    try { file.writeText(t); true } catch (e: Exception) { false }
                                }
                                if (ok) {
                                    original = t
                                    vm.toast("Tersimpan")
                                    vm.refreshAll()
                                } else {
                                    vm.toast("Gagal menyimpan")
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Save, "Simpan", tint = if (dirty) Orange else Color.Gray)
                    }
                }
                if (original == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Orange)
                    }
                } else {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = TextStyle(
                            color = cs.onBackground, fontSize = 13.sp, fontFamily = FontFamily.Monospace
                        ),
                        cursorBrush = SolidColor(Orange),
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)
                    )
                }
            }
        }
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Buang perubahan?") },
            text = { Text("Ada perubahan yang belum disimpan.") },
            confirmButton = {
                TextButton(onClick = { confirmClose = false; vm.editing = null }) {
                    Text("Buang", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Lanjut edit") } }
        )
    }
}

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Aubergine, Color(0xFF1B0013)))),
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(72.dp).background(Orange, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Folder, null, tint = Color.White, modifier = Modifier.size(40.dp)) }
            Spacer(Modifier.height(18.dp))
            Text("zenorX", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                "zenorX butuh izin akses semua file agar bisa menampilkan dan mengelola penyimpanan perangkat Anda.",
                color = Color(0xFFE6CFE0), fontSize = 14.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onGrant, colors = ButtonDefaults.buttonColors(containerColor = Orange)) {
                Text("Berikan izin", color = Color.White)
            }
        }
    }
}
