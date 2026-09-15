package de.fabihbbbt.assetbrowser.client.gui.export

import net.minecraft.resources.Identifier
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.TimeUnit

object AssetClipboardExporter {
    private val EXPORT_DIR = Path.of(System.getProperty("java.io.tmpdir", "."), "assetbrowser-copied-assets")

    private val OS_NAME = System.getProperty("os.name", "").lowercase(Locale.ROOT)

    fun writeExportFile(
        id: Identifier,
        bytes: ByteArray,
    ): Path? {
        val target = EXPORT_DIR.resolve(id.namespace).resolve(id.path)
        return runCatching {
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
            target
        }.getOrNull()
    }

    fun copyFilesReferenceToClipboard(files: List<Path>): Boolean = copyViaAwt(files) || copyViaNativeCommand(files)

    private fun copyViaAwt(files: List<Path>): Boolean {
        val previousHeadless = System.getProperty("java.awt.headless")
        val copied =
            runCatching {
                System.setProperty("java.awt.headless", "false")
                Toolkit.getDefaultToolkit().systemClipboard.setContents(FileTransferable(files), null)
            }.isSuccess
        if (previousHeadless != null) {
            System.setProperty("java.awt.headless", previousHeadless)
        } else {
            System.clearProperty("java.awt.headless")
        }
        return copied
    }

    private fun copyViaNativeCommand(files: List<Path>): Boolean =
        runCatching {
            when {
                OS_NAME.contains("win") -> {
                    runWithStdin(
                        listOf(
                            "powershell",
                            "-NoProfile",
                            "-NonInteractive",
                            "-Command",
                            "Set-Clipboard -LiteralPath " + toPowerShellArray(files),
                        ),
                        null,
                    )
                }

                OS_NAME.contains("mac") -> {
                    runWithStdin(listOf("osascript", "-e", "set the clipboard to " + toAppleScriptFileList(files)), null)
                }

                isOnPath("wl-copy") -> {
                    runWithStdin(listOf("wl-copy", "--type", "text/uri-list"), toUriList(files))
                }

                isOnPath("xclip") -> {
                    runWithStdin(listOf("xclip", "-selection", "clipboard", "-t", "text/uri-list"), toUriList(files))
                }

                else -> {
                    false
                }
            }
        }.getOrDefault(false)

    private fun toUriList(files: List<Path>): String = files.joinToString("") { "${it.toUri()}\r\n" }

    private fun toAppleScriptFileList(files: List<Path>): String =
        files.joinToString(", ", "{", "}") { "POSIX file \"${it.toAbsolutePath()}\"" }

    private fun toPowerShellArray(files: List<Path>): String =
        files.joinToString(", ", "@(", ")") { "'" + it.toAbsolutePath().toString().replace("'", "''") + "'" }

    fun revealInFileManager(file: Path): Boolean =
        runCatching {
            when {
                OS_NAME.contains("win") -> runFireAndForget(listOf("explorer.exe", "/select,\"" + file.toAbsolutePath() + "\""))
                OS_NAME.contains("mac") -> runFireAndForget(listOf("open", "-R", file.toAbsolutePath().toString()))
                isOnPath("dolphin") -> runFireAndForget(listOf("dolphin", "--select", file.toAbsolutePath().toString()))
                isOnPath("nautilus") -> runFireAndForget(listOf("nautilus", "--select", file.toAbsolutePath().toString()))
                isOnPath("xdg-open") -> runFireAndForget(listOf("xdg-open", file.parent.toString()))
                else -> false
            }
        }.getOrDefault(false)

    private fun runFireAndForget(command: List<String>): Boolean {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        process.outputStream.close()
        return true
    }

    private fun isOnPath(command: String): Boolean {
        val path = System.getenv("PATH") ?: return false
        for (dir in path.split(File.pathSeparator)) {
            if (File(dir, command).canExecute()) {
                return true
            }
        }
        return false
    }

    private fun runWithStdin(
        command: List<String>,
        stdin: String?,
    ): Boolean {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        if (stdin != null) {
            process.outputStream.use { out -> out.write(stdin.toByteArray(StandardCharsets.UTF_8)) }
        } else {
            process.outputStream.close()
        }
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return false
        }
        return process.exitValue() == 0
    }

    private class FileTransferable(
        files: List<Path>,
    ) : Transferable {
        private val files: List<File> = files.map { it.toFile() }

        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = DataFlavor.javaFileListFlavor == flavor

        override fun getTransferData(flavor: DataFlavor): Any {
            if (!isDataFlavorSupported(flavor)) {
                throw UnsupportedFlavorException(flavor)
            }
            return files
        }
    }
}
