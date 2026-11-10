package com.zenorx.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

// ───────────────────────── Root ─────────────────────────

@Composable
fun ZenorXApp(vm: FilesViewModel) {
    val cs = MaterialTheme.colorScheme
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val act = vm.panel(vm.active)

    LaunchedEffect(Unit) {
        for (m in vm.messages) {
            snack.currentSnackbarData?.dismiss()
            snack.showSnackbar(m)
        }
    }

    BackHandler(
        enabled = drawer.isOpen || act.selected.isNotEmpty() || vm.searchOpen || vm.canGoUp(act)
    ) {
        when {
            drawer.isOpen -> scope.launch { drawer.close() }
            act.selected.isNotEmpty() -> act.selected = emptySet()
            vm.searchOpen -> vm.closeSearch()
            else -> vm.up(act)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = !vm.dnd.active,
        drawerContent = { Sidebar(vm) { scope.launch { drawer.close() } } }
    ) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = cs.background,
                snackbarHost = { SnackbarHost(snack) },
                topBar = { TopBar(vm) { scope.launch { drawer.open() } } },
                bottomBar = { ActionBar(vm) }
            ) { pad ->
                Column(Modifier.padding(pad).fillMaxSize()) {
                    if (vm.searchOpen) SearchBar(vm)
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        FilePanel(vm, vm.panelA, Modifier.weight(1f).fillMaxHeight())
                        if (vm.dual) {
                            Box(Modifier.width(3.dp).fillMaxHeight().background(Aubergine))
                            FilePanel(vm, vm.panelB, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
            DragOverlay(vm)
        }
    }
    DialogHost(vm)
}

// ───────────────────────── Top bar ─────────────────────────

@Composable
fun TopBar(vm: FilesViewModel, onMenu: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(color = Aubergine, contentColor = Color.White, shadowElevation = 4.dp) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, "Menu") }
            Text("zenorX", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.toggleSearch() }) { Icon(Icons.Filled.Search, "Cari") }
            IconButton(onClick = { vm.changeDual(!vm.dual) }) {
                Icon(
                    Icons.Filled.ViewColumn, "Dual panel",
                    tint = if (vm.dual) Orange else Color.White
                )
            }
            IconButton(onClick = { vm.refreshAll() }) { Icon(Icons.Filled.Refresh, "Segarkan") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Lainnya") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (vm.showHidden) "Sembunyikan file tersembunyi" else "Tampilkan file tersembunyi") },
                        leadingIcon = {
                            Icon(if (vm.showHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                        },
                        onClick = { menu = false; vm.changeHidden(!vm.showHidden) }
                    )
                    DropdownMenuItem(
                        text = { Text("Pengaturan") },
                        leadingIcon = { Icon(Icons.Filled.Settings, null) },
                        onClick = { menu = false; vm.dlg = Dlg.Prefs }
                    )
                    DropdownMenuItem(
                        text = { Text("Tentang zenorX") },
                        leadingIcon = { Icon(Icons.Filled.Info, null) },
                        onClick = { menu = false; vm.dlg = Dlg.About }
                    )
                }
            }
        }
    }
}

@Composable
fun SearchBar(vm: FilesViewModel) {
    val cs = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val who = if (vm.dual) " panel ${if (vm.active == 0) "A" else "B"}" else ""
    Row(
        Modifier.fillMaxWidth().background(cs.surface).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = vm.searchText,
            onValueChange = { vm.searchText = it },
            modifier = Modifier.weight(1f).focusRequester(focus),
            singleLine = true,
            placeholder = { Text("Cari di$who (termasuk subfolder)", fontSize = 13.sp) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search(vm.panel(vm.active), vm.searchText) })
        )
        IconButton(onClick = { vm.closeSearch() }) { Icon(Icons.Filled.Close, "Tutup pencarian") }
    }
}

// ───────────────────────── Sidebar (gaya Nautilus) ─────────────────────────

class Place(val label: String, val icon: ImageVector, val dir: File)

private fun placesOf(vm: FilesViewModel): List<Place> {
    val r = vm.root
    return listOf(
        Place("Beranda", Icons.Filled.Home, r),
        Place("Dokumen", Icons.Filled.Description, File(r, "Documents")),
        Place("Unduhan", Icons.Filled.Download, File(r, "Download")),
        Place("Musik", Icons.Filled.MusicNote, File(r, "Music")),
        Place("Gambar", Icons.Filled.Image, File(r, "Pictures")),
        Place("Video", Icons.Filled.Movie, File(r, "Movies")),
        Place("Kamera", Icons.Filled.CameraAlt, File(r, "DCIM"))
    ).filter { it.dir.isDirectory }
}

@Composable
fun Sidebar(vm: FilesViewModel, close: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val p = vm.panel(vm.active)
    val places = remember { placesOf(vm) }
    ModalDrawerSheet(drawerContainerColor = cs.surface) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Aubergine, AubergineSoft)))
                    .padding(20.dp)
            ) {
                Column {
                    Box(
                        Modifier.size(44.dp).background(Orange, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Folder, null, tint = Color.White) }
                    Spacer(Modifier.height(10.dp))
                    Text("zenorX", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Manajer file dual panel", color = Color(0xFFD9B8CF), fontSize = 12.sp)
                }
            }
            SectionLabel("Tempat")
            places.forEach { pl ->
                DrawerItem(pl.label, pl.icon, p.path == pl.dir) { vm.navigate(p, pl.dir); close() }
            }
            SectionLabel("Perangkat")
            DrawerItem("Penyimpanan internal", Icons.Filled.Storage, p.path == vm.root) {
                vm.navigate(p, vm.root); close()
            }
            vm.sdCards.forEach { sd ->
                DrawerItem("Kartu SD (${sd.name})", Icons.Filled.SdCard, p.path == sd) {
                    vm.navigate(p, sd); close()
                }
            }
            DrawerItem("Root sistem (/)", Icons.Filled.Folder, p.path.absolutePath == "/") {
                vm.navigate(p, File("/")); close()
            }
            SectionLabel("Aplikasi")
            DrawerItem("Pengaturan", Icons.Filled.Settings, false) { vm.dlg = Dlg.Prefs; close() }
            DrawerItem("Tentang", Icons.Filled.Info, false) { vm.dlg = Dlg.About; close() }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionLabel(t: String) {
    Text(
        t.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun DrawerItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label, fontSize = 14.sp) },
        icon = { Icon(icon, null) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = Orange.copy(alpha = 0.25f),
            selectedIconColor = Orange,
            selectedTextColor = Orange
        )
    )
}

// ───────────────────────── Panel ─────────────────────────

@Composable
fun FilePanel(vm: FilesViewModel, p: PanelState, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val active = vm.active == p.id
    val listState = rememberLazyListState()
    LaunchedEffect(p.path) { listState.scrollToItem(0) }
    DisposableEffect(p.id) { onDispose { vm.panelBounds.remove(p.id) } }

    Column(
        modifier
            .onGloballyPositioned { vm.panelBounds[p.id] = it.boundsInRoot() }
            .pointerInput(p.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    vm.activate(p)
                }
            }
            .background(cs.background)
    ) {
        PanelHeader(vm, p, active)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (p.items.isEmpty() && !p.loading) {
                Text(
                    p.error ?: if (p.resultsMode) "Tidak ada hasil" else "Folder kosong",
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    color = cs.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center
                )
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(p.items, key = { it.path }) { item ->
                    val hovered = vm.dnd.active && vm.dnd.hoverPanel == p.id && vm.dnd.hoverFolder == item.path
                    FileRow(vm, p, item, item.path in p.selected, hovered)
                }
            }
            if (p.loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    color = Orange
                )
            }
            val dropHere = vm.dnd.active && vm.dnd.hoverPanel == p.id &&
                vm.dnd.hoverFolder == null && vm.dnd.from != p.id
            if (dropHere) Box(Modifier.fillMaxSize().border(3.dp, Orange))
        }
        PanelFooter(p)
    }
}

@Composable
fun PanelHeader(vm: FilesViewModel, p: PanelState, active: Boolean) {
    val cs = MaterialTheme.colorScheme
    val scroll = rememberScrollState()
    LaunchedEffect(p.path) { snapshotFlow { scroll.maxValue }.collect { scroll.scrollTo(it) } }
    val crumbs = remember(p.path) { vm.crumbs(p.path) }
    Column {
        Row(
            Modifier.fillMaxWidth()
                .background(if (active) cs.secondaryContainer else cs.surfaceVariant)
                .height(38.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (vm.dual) {
                Box(
                    Modifier.padding(start = 6.dp).size(20.dp)
                        .background(if (active) Orange else cs.outline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (p.id == 0) "A" else "B", color = Color.White,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
            IconButton(onClick = { vm.up(p) }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.ArrowUpward, "Naik", Modifier.size(18.dp), tint = cs.onSurface)
            }
            Row(
                Modifier.weight(1f).horizontalScroll(scroll),
                verticalAlignment = Alignment.CenterVertically
            ) {
                crumbs.forEachIndexed { i, c ->
                    val last = i == crumbs.lastIndex
                    Text(
                        c.first, fontSize = 12.sp, maxLines = 1,
                        color = if (last) cs.onSurface else cs.onSurfaceVariant,
                        fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp))
                            .clickable { vm.activate(p); vm.navigate(p, c.second) }
                            .padding(horizontal = 3.dp, vertical = 4.dp)
                    )
                    if (!last) Text("›", fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (active) Orange else Color.Transparent))
    }
}

@Composable
fun PanelFooter(p: PanelState) {
    val cs = MaterialTheme.colorScheme
    val res = if (p.resultsMode) "Hasil cari • " else ""
    val sel = if (p.selected.isNotEmpty()) " • ${p.selected.size} dipilih" else ""
    Row(Modifier.fillMaxWidth().background(cs.surfaceVariant).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(
            "$res${p.items.size} item$sel", fontSize = 10.5.sp, color = cs.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

private fun iconFor(k: FileKind): ImageVector = when (k) {
    FileKind.FOLDER -> Icons.Filled.Folder
    FileKind.IMAGE -> Icons.Filled.Image
    FileKind.AUDIO -> Icons.Filled.Audiotrack
    FileKind.VIDEO -> Icons.Filled.Movie
    FileKind.ARCHIVE -> Icons.Filled.Archive
    FileKind.APK -> Icons.Filled.Android
    FileKind.CODE -> Icons.Filled.Code
    FileKind.PDF -> Icons.Filled.PictureAsPdf
    FileKind.TEXT -> Icons.Filled.Description
    FileKind.OTHER -> Icons.Filled.Description
}

private fun colorFor(k: FileKind): Color = when (k) {
    FileKind.FOLDER -> Color(0xFFEE8A3C)
    FileKind.IMAGE -> Color(0xFF5BB974)
    FileKind.AUDIO -> Color(0xFFB070D0)
    FileKind.VIDEO -> Color(0xFFE5534B)
    FileKind.ARCHIVE -> Color(0xFFE0B030)
    FileKind.APK -> Color(0xFF7CB342)
    FileKind.CODE -> Color(0xFF5AA9E6)
    FileKind.PDF -> Color(0xFFE5534B)
    FileKind.TEXT -> Color(0xFF9AA5B1)
    FileKind.OTHER -> Color(0xFF8A8A8A)
}

@Composable
fun FileRow(vm: FilesViewModel, p: PanelState, item: FileItem, selected: Boolean, hovered: Boolean) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val origin = remember { mutableStateOf(Offset.Zero) }
    val key = "${p.id}|${item.path}"
    if (item.isDir) {
        DisposableEffect(key) { onDispose { vm.rowBounds.remove(key) } }
    }
    val bg = when {
        hovered -> Orange.copy(alpha = 0.35f)
        selected -> cs.primary.copy(alpha = 0.22f)
        else -> Color.Transparent
    }
    val meta = when {
        p.resultsMode -> item.file.parent ?: ""
        item.isDir -> "${item.childCount} item • ${formatShort(item.modified)}"
        else -> "${humanSize(item.size)} • ${formatShort(item.modified)}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(bg)
            .onGloballyPositioned { c ->
                origin.value = c.positionInRoot()
                if (item.isDir) vm.rowBounds[key] = c.boundsInRoot()
            }
            .pointerInput(item.path) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var outcome = 0 // 0 = tahan lama, 1 = ketuk, 2 = batal/scroll
                    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        val up = waitForUpOrCancellation()
                        outcome = if (up != null) 1 else 2
                        up?.consume()
                    }
                    if (outcome == 1) {
                        vm.onRowTap(p, item.path, ctx)
                    } else if (outcome == 0) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.dragStart(p, item.path, origin.value + down.position)
                        val ok = drag(down.id) { change ->
                            change.consume()
                            vm.dragMove(origin.value + change.position)
                        }
                        if (ok) vm.dragEnd() else vm.dragCancel()
                    }
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Box(Modifier.size(30.dp).background(cs.primary, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            } else {
                Icon(iconFor(item.kind), null, tint = colorFor(item.kind), modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = cs.onSurface)
            Text(meta, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.width(6.dp))
    }
}

// ───────────────────────── Bar aksi bawah ─────────────────────────

@Composable
fun ActionBar(vm: FilesViewModel) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val p = vm.panel(vm.active)
    val sel = vm.selectedFiles(p)
    val n = sel.size
    val other = vm.panel(1 - vm.active)
    val otherName = if (vm.active == 0) "B" else "A"
    val clip = vm.clip
    Surface(color = cs.surface, shadowElevation = 10.dp) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 3.dp)
        ) {
            if (vm.dual) {
                ActionBtn(Icons.Filled.ContentCopy, "Salin→$otherName", n > 0, highlight = true) {
                    vm.transfer(sel, other.path, false); p.selected = emptySet()
                }
                ActionBtn(Icons.Filled.SwapHoriz, "Pindah→$otherName", n > 0, highlight = true) {
                    vm.transfer(sel, other.path, true); p.selected = emptySet()
                }
            }
            ActionBtn(Icons.Filled.ContentCopy, "Salin", n > 0) { vm.copyToClip(p, false) }
            ActionBtn(Icons.Filled.ContentCut, "Potong", n > 0) { vm.copyToClip(p, true) }
            ActionBtn(
                Icons.Filled.ContentPaste,
                if (clip != null) "Tempel (${clip.files.size})" else "Tempel",
                clip != null
            ) { vm.paste(p) }
            ActionBtn(Icons.Filled.Delete, "Hapus", n > 0) { vm.dlg = Dlg.Delete(sel) }
            ActionBtn(Icons.Filled.Edit, "Ubah nama", n == 1) { vm.dlg = Dlg.Rename(sel[0]) }
            ActionBtn(Icons.Filled.CreateNewFolder, "Folder", true) { vm.dlg = Dlg.NewFolder(p.path) }
            ActionBtn(Icons.Filled.NoteAdd, "File", true) { vm.dlg = Dlg.NewFile(p.path) }
            ActionBtn(Icons.Filled.Archive, "Zip", n > 0) { vm.compress(sel, p.path); p.selected = emptySet() }
            ActionBtn(
                Icons.Filled.Unarchive, "Ekstrak",
                n == 1 && sel[0].isFile && sel[0].extension.lowercase() in extractableExt
            ) { vm.extract(sel[0], p.path); p.selected = emptySet() }
            ActionBtn(Icons.Filled.Share, "Bagikan", n > 0) { vm.share(ctx, sel) }
            ActionBtn(Icons.Filled.OpenInNew, "Buka dgn", n == 1 && sel[0].isFile) { vm.openWith(ctx, sel[0]) }
            ActionBtn(Icons.Filled.Info, "Info", true) { vm.showProps(if (n >= 1) sel[0] else p.path) }
            val allSelected = n > 0 && n == p.items.size
            ActionBtn(
                Icons.Filled.SelectAll,
                if (allSelected) "Batal pilih" else "Pilih semua",
                p.items.isNotEmpty()
            ) { if (allSelected) p.selected = emptySet() else vm.selectAll(p) }
        }
    }
}

@Composable
private fun ActionBtn(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    highlight: Boolean = false,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.width(66.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp)
            .alpha(if (enabled) 1f else 0.35f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = if (highlight) Orange else cs.onSurface, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 10.sp, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ───────────────────────── Overlay drag ─────────────────────────

@Composable
fun DragOverlay(vm: FilesViewModel) {
    val d = vm.dnd
    if (!d.active) return
    val label = if (d.files.size == 1) d.files[0].name else "${d.files.size} item"
    Surface(
        modifier = Modifier.offset { IntOffset((d.pos.x - 40f).roundToInt(), (d.pos.y - 100f).roundToInt()) },
        color = Orange,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 10.dp
    ) {
        Row(
            Modifier.widthIn(max = 220.dp).padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (d.files.size == 1 && d.files[0].isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
                null, tint = Color.White, modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(label, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
