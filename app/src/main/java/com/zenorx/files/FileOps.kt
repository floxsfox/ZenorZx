package com.zenorx.files

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object FileOps {

    fun isLink(f: File): Boolean = try {
        Files.isSymbolicLink(f.toPath())
    } catch (e: Exception) {
        false
    }

    /** Nama unik: "nama (1).ext" jika sudah ada. */
    fun uniqueTarget(dir: File, name: String): File {
        val first = File(dir, name)
        if (!first.exists()) return first
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        var t = File(dir, "$base ($i)$ext")
        while (t.exists()) {
            i++
            t = File(dir, "$base ($i)$ext")
        }
        return t
    }

    fun countFiles(f: File): Int {
        if (f.isDirectory && !isLink(f)) {
            var n = 0
            f.listFiles()?.forEach { n += countFiles(it) }
            return n
        }
        return 1
    }

    private suspend fun copyStream(src: File, dst: File) {
        try {
            src.inputStream().use { input ->
                dst.outputStream().use { output ->
                    val buf = ByteArray(128 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        currentCoroutineContext().ensureActive()
                    }
                }
            }
        } catch (e: Throwable) {
            dst.delete()
            throw e
        }
    }

    suspend fun copy(src: File, dstDir: File, onFile: (String) -> Unit): File {
        currentCoroutineContext().ensureActive()
        val target = uniqueTarget(dstDir, src.name)
        if (src.isDirectory && !isLink(src)) {
            if (!target.mkdirs() && !target.isDirectory) throw IOException("Gagal membuat folder ${target.name}")
            src.listFiles()?.forEach { copy(it, target, onFile) }
        } else {
            copyStream(src, target)
            target.setLastModified(src.lastModified())
            onFile(src.name)
        }
        return target
    }

    suspend fun move(src: File, dstDir: File, onFile: (String) -> Unit) {
        currentCoroutineContext().ensureActive()
        val target = uniqueTarget(dstDir, src.name)
        if (src.renameTo(target)) {
            onFile(src.name)
            return
        }
        copy(src, dstDir, onFile)
        delete(src) { }
    }

    suspend fun delete(f: File, onFile: (String) -> Unit) {
        currentCoroutineContext().ensureActive()
        val isRealDir = f.isDirectory && !isLink(f)
        if (isRealDir) f.listFiles()?.forEach { delete(it, onFile) }
        if (!f.delete() && f.exists()) throw IOException("Gagal menghapus ${f.name}")
        if (!isRealDir) onFile(f.name)
    }

    suspend fun zip(files: List<File>, out: File, onFile: (String) -> Unit) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { zos ->
            for (f in files) addToZip(zos, f, f.name, out, onFile)
        }
    }

    private suspend fun addToZip(zos: ZipOutputStream, f: File, entry: String, skip: File, onFile: (String) -> Unit) {
        currentCoroutineContext().ensureActive()
        if (f.absolutePath == skip.absolutePath) return
        if (f.isDirectory) {
            if (isLink(f)) return
            zos.putNextEntry(ZipEntry("$entry/"))
            zos.closeEntry()
            f.listFiles()?.forEach { addToZip(zos, it, "$entry/${it.name}", skip, onFile) }
        } else {
            val e = ZipEntry(entry)
            e.time = f.lastModified()
            zos.putNextEntry(e)
            f.inputStream().use { input ->
                val buf = ByteArray(128 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    zos.write(buf, 0, n)
                    currentCoroutineContext().ensureActive()
                }
            }
            zos.closeEntry()
            onFile(f.name)
        }
    }

    fun zipCount(zip: File): Int = ZipFile(zip).use { it.size() }

    suspend fun unzip(zip: File, destDir: File, onFile: (String) -> Unit): File {
        val target = uniqueTarget(destDir, zip.nameWithoutExtension.ifEmpty { "ekstrak" })
        target.mkdirs()
        val canonTarget = target.canonicalPath
        ZipFile(zip).use { zf ->
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                currentCoroutineContext().ensureActive()
                val e = entries.nextElement()
                val out = File(target, e.name)
                if (!out.canonicalPath.startsWith(canonTarget + File.separator)) {
                    throw IOException("Entri zip tidak aman: ${e.name}")
                }
                if (e.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    zf.getInputStream(e).use { input ->
                        out.outputStream().use { output ->
                            val buf = ByteArray(128 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                currentCoroutineContext().ensureActive()
                            }
                        }
                    }
                    if (e.time > 0) out.setLastModified(e.time)
                    onFile(out.name)
                }
            }
        }
        return target
    }

    /** Hitung total ukuran (byte) dan jumlah file. */
    suspend fun measure(root: File): Pair<Long, Int> {
        var bytes = 0L
        var count = 0
        val stack = ArrayList<File>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val f = stack.removeAt(stack.size - 1)
            if (f.isDirectory && !isLink(f)) {
                f.listFiles()?.let { stack.addAll(it) }
            } else {
                bytes += f.length()
                count++
            }
        }
        return bytes to count
    }
}
