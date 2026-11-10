package com.zenorx.files

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Status satu panel (A atau B). */
class PanelState(val id: Int, start: File) {
    var path by mutableStateOf(start)
    var items by mutableStateOf<List<FileItem>>(emptyList())
    var selected by mutableStateOf<Set<String>>(emptySet())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var resultsMode by mutableStateOf(false)
    var job: Job? = null
}

class DragState {
    var active by mutableStateOf(false)
    var from by mutableIntStateOf(0)
    var files by mutableStateOf<List<File>>(emptyList())
    var pos by mutableStateOf(Offset.Zero)
    var start: Offset = Offset.Zero
    var hoverPanel by mutableIntStateOf(-1)
    var hoverFolder by mutableStateOf<String?>(null)
}

class Clip(val files: List<File>, val move: Boolean)
class Busy(val title: String, val detail: String, val done: Int, val total: Int)
class DropReq(val files: List<File>, val dest: File, val from: Int)
class PropsInfo(val file: File, val bytes: Long? = null, val files: Int? = null)

sealed class Dlg {
    class Rename(val file: File) : Dlg()
    class NewFolder(val dir: File) : Dlg()
    class NewFile(val dir: File) : Dlg()
    class Delete(val files: List<File>) : Dlg()
    object Prefs : Dlg()
    object About : Dlg()
}

private fun listDir(dir: File, hidden: Boolean, by: SortBy, asc: Boolean): Pair<List<FileItem>, String?> {
    val arr = dir.listFiles() ?: return emptyList<FileItem>() to "Folder tidak dapat dibaca"
    val items = ArrayList<FileItem>(arr.size)
    for (f in arr) {
        if (!hidden && f.name.startsWith(".")) continue
        items.add(makeItem(f))
    }
    return sortItems(items, by, asc) to null
}

fun sortItems(list: List<FileItem>, by: SortBy, asc: Boolean): List<FileItem> {
    val base = Comparator<FileItem> { a, b ->
        val c = when (by) {
            SortBy.NAME -> a.name.compareTo(b.name, true)
            SortBy.SIZE -> a.size.compareTo(b.size)
            SortBy.DATE -> a.modified.compareTo(b.modified)
            SortBy.TYPE -> a.file.extension.compareTo(b.file.extension, true)
        }
        if (c != 0) c else a.name.compareTo(b.name, true)
    }
    val cmp = if (asc) base else base.reversed()
    val dirs = list.filter { it.isDir }.sortedWith(cmp)
    val files = list.filter { !it.isDir }.sortedWith(cmp)
    return dirs + files
}

class FilesViewModel(private val app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("zenorx", Context.MODE_PRIVATE)
    val root: File = Environment.getExternalStorageDirectory()
    val sdCards: List<File> = findSdCards()

    // ---- pengaturan ----
    var dual by mutableStateOf(prefs.getBoolean("dual", true))
        private set
    var showHidden by mutableStateOf(prefs.getBoolean("hidden", false))
        private set
    var dark by mutableStateOf(prefs.getBoolean("dark", true))
        private set
    var sortBy by mutableStateOf(SortBy.values().getOrElse(prefs.getInt("sort", 0)) { SortBy.NAME })
        private set
    var sortAsc by mutableStateOf(prefs.getBoolean("asc", true))
        private set
    /** 0 = tanya, 1 = langsung salin, 2 = langsung pindah */
    var dropMode by mutableIntStateOf(prefs.getInt("drop", 0))
        private set

    // ---- panel ----
    val panelA = PanelState(0, startDir("pathA"))
    val panelB = PanelState(1, startDir("pathB"))
    var active by mutableIntStateOf(0)

    var clip by mutableStateOf<Clip?>(null)
    var busy by mutableStateOf<Busy?>(null)
        private set
    var dlg by mutableStateOf<Dlg?>(null)
    var props by mutableStateOf<PropsInfo?>(null)
        private set
    var dropReq by mutableStateOf<DropReq?>(null)
    var editing by mutableStateOf<File?>(null)
    var searchOpen by mutableStateOf(false)
    var searchText by mutableStateOf("")

    val dnd = DragState()
    val panelBounds = HashMap<Int, Rect>()
    val rowBounds = HashMap<String, Rect>()

    val messages = Channel<String>(Channel.BUFFERED)
    private var opJob: Job? = null
    private var propsJob: Job? = null

    init {
        load(panelA)
        load(panelB)
    }

    private fun startDir(key: String): File {
        val s = prefs.getString(key, null)
        if (s != null) {
            val f = File(s)
            if (f.isDirectory) return f
        }
        return root
    }

    private fun findSdCards(): List<File> {
        val res = ArrayList<File>()
        try {
            app.getExternalFilesDirs(null).drop(1).forEach { f ->
                if (f == null) return@forEach
                val s = f.absolutePath
                val i = s.indexOf("/Android/data")
                if (i > 0) {
                    val r = File(s.substring(0, i))
                    if (r.exists() && r.canRead()) res.add(r)
                }
            }
        } catch (e: Exception) {
        }
        return res
    }

    fun panel(i: Int): PanelState = if (i == 0) panelA else panelB
    fun activate(p: PanelState) {
        active = p.id
    }

    fun toast(s: String) {
        messages.trySend(s)
    }

    // ---- pengaturan setter ----
    fun changeDual(v: Boolean) {
        dual = v
        prefs.edit().putBoolean("dual", v).apply()
        if (!v) active = 0
    }

    fun changeHidden(v: Boolean) {
        showHidden = v
        prefs.edit().putBoolean("hidden", v).apply()
        refreshAll()
    }

    fun changeDark(v: Boolean) {
        dark = v
        prefs.edit().putBoolean("dark", v).apply()
    }

    fun changeSort(by: SortBy, asc: Boolean) {
        sortBy = by
        sortAsc = asc
        prefs.edit().putInt("sort", by.ordinal).putBoolean("asc", asc).apply()
        refreshAll()
    }

    fun changeDropMode(v: Int) {
        dropMode = v
        prefs.edit().putInt("drop", v).apply()
    }

    // ---- navigasi ----
    fun crumbs(f: File): List<Pair<String, File>> {
        val list = ArrayList<Pair<String, File>>()
        var cur: File? = f
        while (cur != null) {
            if (cur == root) {
                list.add("Internal" to cur)
                break
            }
            list.add((if (cur.parentFile == null) "/" else cur.name) to cur)
            cur = cur.parentFile
        }
        return list.reversed()
    }

    fun canGoUp(p: PanelState): Boolean =
        p.resultsMode || (p.path != root && p.path.parentFile != null)

    fun navigate(p: PanelState, dir: File) {
        if (!dir.isDirectory) {
            toast("Folder tidak ditemukan")
            return
        }
        if (!dir.canRead()) {
            toast("Tidak ada izin membaca ${dir.name.ifEmpty { "/" }}")
            return
        }
        p.path = dir
        p.selected = emptySet()
        load(p)
        prefs.edit().putString(if (p.id == 0) "pathA" else "pathB", dir.absolutePath).apply()
    }

    fun up(p: PanelState) {
        active = p.id
        if (p.resultsMode) {
            load(p)
            return
        }
        val parent = p.path.parentFile ?: return
        if (!parent.canRead()) {
            toast("Folder induk tidak bisa dibaca")
            return
        }
        navigate(p, parent)
    }

    fun load(p: PanelState) {
        p.job?.cancel()
        p.loading = true
        p.resultsMode = false
        val dir = p.path
        val hidden = showHidden
        val by = sortBy
        val asc = sortAsc
        p.job = viewModelScope.launch {
            val res = withContext(Dispatchers.IO) { listDir(dir, hidden, by, asc) }
            if (p.path == dir) {
                p.items = res.first
                p.error = res.second
                val existing = res.first.mapTo(HashSet()) { it.path }
                p.selected = p.selected.filterTo(HashSet()) { it in existing }
            }
            p.loading = false
        }
    }

    fun refreshAll() {
        load(panelA)
        load(panelB)
    }

    // ---- pencarian ----
    fun toggleSearch() {
        if (searchOpen) closeSearch() else searchOpen = true
    }

    fun closeSearch() {
        searchOpen = false
        searchText = ""
        val p = panel(active)
        if (p.resultsMode) load(p)
    }

    fun search(p: PanelState, q: String) {
        if (q.isBlank()) {
            if (p.resultsMode) load(p)
            return
        }
        p.job?.cancel()
        p.loading = true
        p.resultsMode = true
        p.selected = emptySet()
        val root0 = p.path
        val hidden = showHidden
        val by = sortBy
        val asc = sortAsc
        p.job = viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                val out = ArrayList<FileItem>()
                val stack = ArrayList<File>()
                stack.add(root0)
                while (stack.isNotEmpty() && out.size < 500) {
                    ensureActive()
                    val d = stack.removeAt(stack.size - 1)
                    val kids = d.listFiles() ?: continue
                    for (f in kids) {
                        if (!hidden && f.name.startsWith(".")) continue
                        if (f.name.contains(q.trim(), ignoreCase = true)) out.add(makeItem(f))
                        if (f.isDirectory && !FileOps.isLink(f)) stack.add(f)
                    }
                }
                sortItems(out, by, asc)
            }
            p.items = res
            p.error = null
            p.loading = false
        }
    }

    // ---- seleksi ----
    fun selectedFiles(p: PanelState): List<File> =
        if (p.selected.isEmpty()) emptyList() else p.items.filter { it.path in p.selected }.map { it.file }

    fun toggle(p: PanelState, item: FileItem) {
        p.selected = if (item.path in p.selected) p.selected - item.path else p.selected + item.path
    }

    fun selectAll(p: PanelState) {
        p.selected = p.items.mapTo(HashSet()) { it.path }
    }

    fun onRowTap(p: PanelState, path: String, ctx: Context) {
        active = p.id
        val item = p.items.firstOrNull { it.path == path } ?: return
        if (p.selected.isNotEmpty()) {
            toggle(p, item)
            return
        }
        if (item.isDir) navigate(p, item.file) else openFile(ctx, item)
    }

    // ---- buka / bagikan ----
    private fun uriOf(ctx: Context, f: File): Uri =
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", f)

    fun openFile(ctx: Context, item: FileItem) {
        if (isEditable(item.file)) {
            editing = item.file
        } else {
            openWith(ctx, item.file, false)
        }
    }

    fun openWith(ctx: Context, f: File, chooser: Boolean = true) {
        try {
            val i = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uriOf(ctx, f), mimeOf(f))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(if (chooser) Intent.createChooser(i, "Buka dengan") else i)
        } catch (e: Exception) {
            toast("Tidak ada aplikasi untuk membuka file ini")
        }
    }

    fun share(ctx: Context, files: List<File>) {
        val only = files.filter { it.isFile }
        if (only.isEmpty()) {
            toast("Pilih file (folder tidak bisa dibagikan)")
            return
        }
        try {
            val uris = ArrayList<Uri>(only.map { uriOf(ctx, it) })
            val i = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0]).setType(mimeOf(only[0]))
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris).setType("*/*")
            }
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(Intent.createChooser(i, "Bagikan"))
        } catch (e: Exception) {
            toast("Gagal membagikan: ${e.message}")
        }
    }

    // ---- drag & drop ----
    fun dragStart(p: PanelState, path: String, pos: Offset) {
        active = p.id
        if (path !in p.selected) p.selected = p.selected + path
        dnd.from = p.id
        dnd.files = selectedFiles(p)
        dnd.start = pos
        dnd.pos = pos
        dnd.active = true
        updateHover()
    }

    fun dragMove(pos: Offset) {
        if (!dnd.active) return
        dnd.pos = pos
        updateHover()
    }

    fun dragCancel() {
        dnd.active = false
        dnd.hoverFolder = null
        dnd.hoverPanel = -1
    }

    fun dragEnd() {
        if (!dnd.active) return
        val files = dnd.files
        val from = dnd.from
        val moved = (dnd.pos - dnd.start).getDistance() > 36f
        val hp = dnd.hoverPanel
        val hf = dnd.hoverFolder
        val dest: File? = when {
            hp < 0 -> null
            hf != null -> File(hf)
            hp == from -> null
            else -> panel(hp).path
        }
        dragCancel()
        if (!moved || dest == null || files.isEmpty()) return
        when (dropMode) {
            1 -> {
                panel(from).selected = emptySet()
                transfer(files, dest, false)
            }
            2 -> {
                panel(from).selected = emptySet()
                transfer(files, dest, true)
            }
            else -> dropReq = DropReq(files, dest, from)
        }
    }

    private fun updateHover() {
        val pos = dnd.pos
        var pid = -1
        for ((id, r) in panelBounds) {
            if (r.contains(pos) && (id == 0 || dual)) {
                pid = id
                break
            }
        }
        var folder: String? = null
        if (pid >= 0) {
            val dragged = dnd.files.mapTo(HashSet()) { it.absolutePath }
            val prefix = "$pid|"
            for ((k, r) in rowBounds) {
                if (k.startsWith(prefix) && r.contains(pos)) {
                    val path = k.substring(prefix.length)
                    if (path !in dragged) folder = path
                    break
                }
            }
        }
        dnd.hoverPanel = pid
        dnd.hoverFolder = folder
    }

    // ---- operasi file ----
    inner class Progress(private val title: String) {
        @Volatile private var total = 1
        @Volatile private var done = 0
        private var last = 0L

        fun begin(n: Int) {
            total = if (n < 1) 1 else n
            busy = Busy(title, "", done, total)
        }

        fun step(name: String) {
            done++
            val now = SystemClock.uptimeMillis()
            if (now - last > 80 || done >= total) {
                last = now
                busy = Busy(title, name, done, total)
            }
        }
    }

    private fun runOp(title: String, ok: String, block: suspend (Progress) -> Unit) {
        val pr = Progress(title)
        busy = Busy(title, "", 0, 1)
        opJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block(pr) }
                toast(ok)
            } catch (e: CancellationException) {
                toast("Dibatalkan")
            } catch (e: Exception) {
                toast("Gagal: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                busy = null
                refreshAll()
            }
        }
    }

    fun cancelOp() {
        opJob?.cancel()
    }

    private fun isBusy(): Boolean {
        if (busy != null) {
            toast("Masih ada operasi yang berjalan")
            return true
        }
        return false
    }

    fun copyToClip(p: PanelState, move: Boolean) {
        val f = selectedFiles(p)
        if (f.isEmpty()) return
        clip = Clip(f, move)
        p.selected = emptySet()
        toast("${f.size} item ${if (move) "dipotong" else "disalin"} - buka folder tujuan lalu Tempel")
    }

    fun paste(p: PanelState) {
        val c = clip ?: return
        transfer(c.files, p.path, c.move)
        if (c.move) clip = null
    }

    fun transfer(files: List<File>, dest: File, move: Boolean) {
        if (isBusy()) return
        if (!dest.isDirectory || !dest.canWrite()) {
            toast("Folder tujuan tidak bisa ditulis")
            return
        }
        val destPath = try { dest.canonicalPath } catch (e: Exception) { dest.absolutePath }
        val valid = files.filter { f ->
            if (!f.exists()) return@filter false
            val fp = try { f.canonicalPath } catch (e: Exception) { f.absolutePath }
            val inside = f.isDirectory && (destPath == fp || destPath.startsWith(fp + File.separator))
            val same = move && f.parentFile?.absolutePath == dest.absolutePath
            !inside && !same
        }
        if (valid.isEmpty()) {
            toast("Tidak ada item yang bisa diproses (tujuan sama / di dalam dirinya)")
            return
        }
        runOp(if (move) "Memindahkan" else "Menyalin", if (move) "Selesai dipindahkan" else "Selesai disalin") { p ->
            p.begin(valid.sumOf { FileOps.countFiles(it) })
            for (f in valid) {
                if (move) FileOps.move(f, dest) { n -> p.step(n) } else FileOps.copy(f, dest) { n -> p.step(n) }
            }
        }
    }

    fun delete(files: List<File>) {
        if (isBusy()) return
        panelA.selected = emptySet()
        panelB.selected = emptySet()
        runOp("Menghapus", "Terhapus") { p ->
            p.begin(files.sumOf { FileOps.countFiles(it) })
            for (f in files) FileOps.delete(f) { n -> p.step(n) }
        }
    }

    fun compress(files: List<File>, dir: File) {
        if (isBusy() || files.isEmpty()) return
        val baseName = if (files.size == 1) {
            val f = files[0]
            if (f.isDirectory) f.name else f.nameWithoutExtension
        } else {
            dir.name.ifEmpty { "arsip" }
        }
        val out = FileOps.uniqueTarget(dir, "$baseName.zip")
        runOp("Mengompres", "Dibuat: ${out.name}") { p ->
            p.begin(files.sumOf { FileOps.countFiles(it) })
            try {
                FileOps.zip(files, out) { n -> p.step(n) }
            } catch (e: Throwable) {
                out.delete()
                throw e
            }
        }
    }

    fun extract(zip: File, dir: File) {
        if (isBusy()) return
        runOp("Mengekstrak", "Selesai diekstrak") { p ->
            p.begin(FileOps.zipCount(zip))
            FileOps.unzip(zip, dir) { n -> p.step(n) }
        }
    }

    private fun validName(n: String): Boolean =
        n.isNotBlank() && !n.contains('/') && n != "." && n != ".."

    fun rename(f: File, newName: String) {
        val n = newName.trim()
        if (!validName(n)) {
            toast("Nama tidak valid")
            return
        }
        val target = File(f.parentFile, n)
        if (target.exists() && target.absolutePath != f.absolutePath) {
            toast("Sudah ada: $n")
            return
        }
        if (f.renameTo(target)) {
            panelA.selected = emptySet()
            panelB.selected = emptySet()
            toast("Nama diubah")
            refreshAll()
        } else {
            toast("Gagal mengubah nama")
        }
    }

    fun createFolder(dir: File, name: String) {
        val n = name.trim()
        if (!validName(n)) {
            toast("Nama tidak valid")
            return
        }
        val f = File(dir, n)
        if (f.exists()) {
            toast("Sudah ada: $n")
            return
        }
        if (f.mkdirs()) {
            toast("Folder dibuat")
            refreshAll()
        } else {
            toast("Gagal membuat folder")
        }
    }

    fun createFile(dir: File, name: String) {
        val n = name.trim()
        if (!validName(n)) {
            toast("Nama tidak valid")
            return
        }
        val f = File(dir, n)
        if (f.exists()) {
            toast("Sudah ada: $n")
            return
        }
        try {
            if (f.createNewFile()) {
                toast("File dibuat")
                refreshAll()
            } else {
                toast("Gagal membuat file")
            }
        } catch (e: Exception) {
            toast("Gagal membuat file: ${e.message}")
        }
    }

    fun showProps(f: File) {
        propsJob?.cancel()
        props = PropsInfo(f)
        propsJob = viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { FileOps.measure(f) }
            if (props?.file == f) props = PropsInfo(f, r.first, r.second)
        }
    }

    fun closeProps() {
        propsJob?.cancel()
        props = null
    }
}
