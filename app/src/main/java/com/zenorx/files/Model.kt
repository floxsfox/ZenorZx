package com.zenorx.files

import android.webkit.MimeTypeMap
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SortBy(val label: String) {
    NAME("Nama"), SIZE("Ukuran"), DATE("Tanggal"), TYPE("Tipe")
}

enum class FileKind { FOLDER, IMAGE, AUDIO, VIDEO, ARCHIVE, APK, CODE, PDF, TEXT, OTHER }

class FileItem(
    val file: File,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    val childCount: Int,
    val kind: FileKind
) {
    val name: String = file.name
    val path: String = file.absolutePath
}

private val imageExt = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg", "ico")
private val audioExt = setOf("mp3", "wav", "flac", "ogg", "m4a", "aac", "opus", "wma", "mid", "midi")
private val videoExt = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "wmv", "m4v")
private val archiveExt = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "jar")
private val codeExt = setOf(
    "kt", "kts", "java", "js", "ts", "py", "c", "cpp", "h", "cs", "go", "rs", "php", "rb",
    "sh", "html", "htm", "css", "xml", "json", "yml", "yaml", "gradle", "sql", "lua", "dart", "swift"
)
private val textExt: Set<String> = codeExt + setOf(
    "txt", "log", "md", "ini", "conf", "cfg", "properties", "csv", "toml", "srt", "lrc", "bat", "gitignore"
)

/** Ekstensi arsip yang bisa diekstrak oleh zenorX. */
val extractableExt = setOf("zip", "jar", "apk")

fun kindOf(file: File, isDir: Boolean): FileKind {
    if (isDir) return FileKind.FOLDER
    val e = file.extension.lowercase()
    return when {
        e == "apk" -> FileKind.APK
        e == "pdf" -> FileKind.PDF
        e in imageExt -> FileKind.IMAGE
        e in audioExt -> FileKind.AUDIO
        e in videoExt -> FileKind.VIDEO
        e in archiveExt -> FileKind.ARCHIVE
        e in codeExt -> FileKind.CODE
        e in textExt -> FileKind.TEXT
        else -> FileKind.OTHER
    }
}

fun isEditable(f: File): Boolean =
    f.extension.lowercase() in textExt && f.length() <= 2L * 1024 * 1024

fun makeItem(f: File): FileItem {
    val d = f.isDirectory
    return FileItem(
        file = f,
        isDir = d,
        size = if (d) 0L else f.length(),
        modified = f.lastModified(),
        childCount = if (d) (f.list()?.size ?: 0) else 0,
        kind = kindOf(f, d)
    )
}

fun mimeOf(f: File): String {
    val ext = f.extension.lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
}

fun humanSize(b: Long): String {
    if (b < 1024) return "$b B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = b.toDouble()
    var i = -1
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return String.format(Locale.US, if (v >= 100) "%.0f %s" else "%.1f %s", v, units[i])
}

private val shortFmt = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
private val fullFmt = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())

fun formatShort(ms: Long): String = shortFmt.format(Date(ms))
fun formatFull(ms: Long): String = fullFmt.format(Date(ms))
