package cl.nheox.quests.platform

import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.concurrent.thread

/**
 * Makes sure only one Quests runs, and gives a second launch a way to talk to
 * the first.
 *
 * This is what makes global quick capture possible at all (§8.3). Quests does
 * not grab a global hotkey — on Wayland it cannot — so the desktop environment
 * binds a key to `quests --capture`, which starts a *second* process. That
 * process must hand the request to the running one and exit, rather than
 * opening a second copy of the app on top of the first.
 *
 * Two mechanisms, because either alone is insufficient:
 *
 *  - A **lock file**, which is what actually decides who is primary. It is
 *    released by the OS when the process dies, so a crash cannot leave the app
 *    permanently unstartable.
 *  - A **Unix domain socket**, which carries the message. A socket file left
 *    behind by a crash is harmless here, because the lock — not the socket's
 *    existence — is what determines whether anyone is listening.
 */
public class SingleInstance(
    private val runtimeDir: Path = AppPaths.runtimeDir(),
) {
    public enum class Command(public val wire: String) {
        CAPTURE("capture"),
        SHOW("show"),
        ;

        public companion object {
            public fun parse(wire: String): Command? = entries.firstOrNull { it.wire == wire.trim() }
        }
    }

    private var lockChannel: FileChannel? = null
    private var lock: FileLock? = null
    private var server: ServerSocketChannel? = null

    private val lockFile: Path get() = runtimeDir.resolve(LOCK_NAME)

    /**
     * Where the socket lives.
     *
     * A Unix domain socket path is capped by `sun_path`, about 108 bytes, and
     * the limit applies to the whole path rather than the file name. The normal
     * `/run/user/1000/quests/capture.sock` is nowhere near it, but a fallback
     * under a deep `java.io.tmpdir` can exceed it — and binding then throws,
     * which used to take the whole application down. So the path is chosen, not
     * assumed, and both ends choose it the same way.
     */
    private val socketFile: Path get() = socketPath(runtimeDir)

    /**
     * Claims primary status. Returns false when another instance holds it.
     */
    public fun claim(): Boolean {
        Files.createDirectories(runtimeDir)
        val channel = FileChannel.open(
            lockFile,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
        )
        val acquired = runCatching { channel.tryLock() }.getOrNull()
        if (acquired == null) {
            runCatching { channel.close() }
            return false
        }
        lockChannel = channel
        lock = acquired
        runCatching {
            channel.truncate(0)
            channel.write(ByteBuffer.wrap(ProcessHandle.current().pid().toString().toByteArray()))
            channel.force(false)
        }
        return true
    }

    /**
     * Starts listening for commands from later launches.
     *
     * Returns false instead of throwing when the socket cannot be created. That
     * matters: inter-process capture is a convenience, and losing it must cost
     * the user a feature, not the application. Everything still works; only
     * `quests --capture` from a keybinding stops reaching this instance.
     *
     * The socket file is deleted first, because a previous crash leaves one
     * behind and binding over an existing path fails. Safe to do, since reaching
     * this point means the lock was acquired and nobody else is listening.
     */
    public fun listen(onCommand: (Command) -> Unit): Boolean {
        val channel = runCatching {
            val target = socketFile
            target.parent?.let { Files.createDirectories(it) }
            Files.deleteIfExists(target)
            ServerSocketChannel.open(StandardProtocolFamily.UNIX).also {
                it.bind(UnixDomainSocketAddress.of(target))
            }
        }.getOrElse { error ->
            System.err.println("Quests: quick capture over IPC is unavailable (${error.message})")
            return false
        }
        server = channel

        thread(name = "quests-single-instance", isDaemon = true) {
            while (channel.isOpen) {
                try {
                    channel.accept().use { client ->
                        val message = readMessage(client)
                        Command.parse(message)?.let(onCommand)
                    }
                } catch (_: IOException) {
                    // The channel was closed while we were blocked in accept(),
                    // which is how shutdown looks from in here.
                    return@thread
                }
            }
        }
        return true
    }

    /** Sends a command to the running instance. Returns false if nobody answered. */
    public fun send(command: Command): Boolean = runCatching {
        if (!Files.exists(socketFile)) return false
        SocketChannel.open(UnixDomainSocketAddress.of(socketFile)).use { channel ->
            channel.write(ByteBuffer.wrap((command.wire + "\n").toByteArray(StandardCharsets.UTF_8)))
        }
        true
    }.getOrDefault(false)

    public fun release() {
        runCatching { server?.close() }
        runCatching { Files.deleteIfExists(socketFile) }
        runCatching { lock?.release() }
        runCatching { lockChannel?.close() }
        runCatching { Files.deleteIfExists(lockFile) }
        server = null
        lock = null
        lockChannel = null
    }

    public companion object {
        internal const val LOCK_NAME: String = "instance.lock"
        internal const val SOCKET_NAME: String = "capture.sock"

        /**
         * Conservative cap on the socket path. The kernel limit is 108 bytes
         * including the terminator; leaving headroom costs nothing and avoids
         * depending on the exact figure being the same on macOS and Linux.
         */
        internal const val MAX_SOCKET_PATH: Int = 96

        /**
         * The first candidate short enough to bind: the runtime directory, then
         * the temporary directory, then `/tmp`. Pure and deterministic, so the
         * sending process picks the same file as the listening one.
         */
        internal fun socketPath(runtimeDir: Path): Path {
            val tag = Integer.toHexString(
                (System.getProperty("user.name") ?: "user").hashCode(),
            ).takeLast(8)
            val candidates = listOf(
                runtimeDir.resolve(SOCKET_NAME),
                Path.of(System.getProperty("java.io.tmpdir") ?: "/tmp").resolve("quests-$tag.sock"),
                Path.of("/tmp", "quests-$tag.sock"),
            )
            return candidates.firstOrNull { fits(it) } ?: candidates.last()
        }

        private fun fits(path: Path): Boolean =
            path.toAbsolutePath().toString().toByteArray(StandardCharsets.UTF_8).size <= MAX_SOCKET_PATH
    }

    private fun readMessage(client: SocketChannel): String {
        val buffer = ByteBuffer.allocate(64)
        val read = client.read(buffer)
        if (read <= 0) return ""
        buffer.flip()
        return StandardCharsets.UTF_8.decode(buffer).toString()
    }
}
