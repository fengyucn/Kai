package com.inspiredandroid.kai.linux

import android.os.Build
import java.io.File
import java.io.IOException
import java.net.URL

// Cap at 3.22: Alpine 3.23+ ships apk-tools 3, which uses execveat() in a way
// proot does not support, so `apk update` fails under the sandbox runtime.
// See termux/proot-distro#532 / #595.
private const val ALPINE_VERSION = "3.22.5"
private const val ALPINE_BRANCH = "v3.22"

private val ALPINE_MIRRORS = listOf(
    // 国内镜像优先 (abuild): 经实测均可直接下载 minirootfs
    "https://mirrors.tuna.tsinghua.edu.cn/alpine",
    "https://mirrors.nju.edu.cn/alpine",
    "https://mirrors.bfsu.edu.cn/alpine",
    "https://mirrors.aliyun.com/alpine",
    "https://mirrors.sjtug.sjtu.edu.cn/alpine",
    // 官方/海外兜底
    "https://dl-cdn.alpinelinux.org/alpine",
    "https://mirrors.edge.kernel.org/alpine",
    "https://ftp.halifax.rwth-aachen.de/alpine",
    "https://alpine.ethz.ch/alpine",
    "https://mirror.csclub.uwaterloo.ca/alpine",
)

// LXC 镜像索引仍从官方读取：国内镜像的 meta/1.0/index-user 存在全零/陈旧/HTML 的情况，
// 不能作为路径来源；但同一路径的 rootfs 本体国内镜像通常可用（华为云最快/最新）。
private const val LXC_INDEX = "https://images.linuxcontainers.org/meta/1.0/index-user"
private val LXC_BASE_MIRRORS = listOf(
    // 国内镜像优先 (abuild)。华为云虽返回 200 但内容是 HTML 门户，已剔除。
    "https://mirrors.tuna.tsinghua.edu.cn/lxc-images",
    "https://mirrors.bfsu.edu.cn/lxc-images",
    "https://mirrors.cernet.edu.cn/lxc-images",
    "https://mirrors.nju.edu.cn/lxc-images",
    // 官方兜底
    "https://images.linuxcontainers.org",
)
private const val DEBIAN_RELEASE = "bookworm"

/**
 * 国内 LXC 镜像通常滞后约一天，而官方索引只给最新时间戳。
 * 生成“当天 + 前 1/2 天同一时刻”的候选路径；不存在的路径会 404 并被下载器跳过。
 */
private fun lxcCandidatePaths(path: String): List<String> {
    val trimmed = path.trimEnd('/')
    val segments = trimmed.split('/')
    val stamp = segments.lastOrNull().orEmpty()
    val match = Regex("""(\d{8})_(\d{2}:\d{2})""").matchEntire(stamp) ?: return listOf(trimmed)
    val date = runCatching {
        java.time.LocalDate.parse(match.groupValues[1], java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
    }.getOrNull() ?: return listOf(trimmed)
    val time = match.groupValues[2]
    return (0..2).map { days ->
        val day = date.minusDays(days.toLong())
            .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
        (segments.dropLast(1) + "${day}_$time").joinToString("/")
    }
}

/**
 * The per-distribution facts the shared installer and proot launcher need:
 * where the rootfs comes from, what has to be fixed up after extraction, and
 * which proot flags and environment the distro's own tooling depends on.
 */
sealed interface DistroSpec {

    val distro: LinuxDistro

    /** This device's ABI in the distro's own architecture vocabulary. */
    fun arch(): String

    /** File name for the downloaded archive — the extension picks the decompressor. */
    val archiveName: String

    /**
     * Candidate download URLs, best first. May hit the network to resolve an
     * index, so call it off the main thread.
     */
    fun rootfsUrls(): List<String>

    /** Post-extraction fixes that must land before the first proot run. */
    fun configure(rootfsDir: File)

    /** proot flags this distro cannot work without. */
    val prootArgs: List<String> get() = emptyList()

    /** Environment every command in this distro should see. */
    val env: Map<String, String> get() = emptyMap()

    companion object {
        fun of(distro: LinuxDistro): DistroSpec = when (distro) {
            LinuxDistro.ALPINE -> AlpineSpec
            LinuxDistro.DEBIAN -> DebianSpec
        }
    }
}

object AlpineSpec : DistroSpec {

    override val distro = LinuxDistro.ALPINE

    override val archiveName = "rootfs.tar.gz"

    override fun arch(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return when {
            abi.startsWith("arm64") -> "aarch64"
            abi.startsWith("armeabi") -> "armhf"
            abi.startsWith("x86_64") -> "x86_64"
            abi.startsWith("x86") -> "x86"
            else -> "aarch64"
        }
    }

    override fun rootfsUrls(): List<String> {
        val arch = arch()
        return ALPINE_MIRRORS.map { base ->
            "$base/$ALPINE_BRANCH/releases/$arch/alpine-minirootfs-$ALPINE_VERSION-$arch.tar.gz"
        }
    }

    override fun configure(rootfsDir: File) {
        TarExtractor.makeWritable(rootfsDir)
        TarExtractor.writeResolvConf(rootfsDir)
        writeRepositories(rootfsDir, ALPINE_MIRRORS.first())
    }

    /**
     * `apk update` is the first thing that has to work, and a mirror can be
     * unreachable even when the one that served the rootfs was fine. The
     * installer walks these, rewriting `repositories` each time.
     */
    val mirrors: List<String> = ALPINE_MIRRORS

    fun writeRepositories(rootfsDir: File, mirrorBase: String) {
        val apkDir = File(rootfsDir, "etc/apk")
        apkDir.mkdirs()
        File(apkDir, "repositories").writeText(
            "$mirrorBase/$ALPINE_BRANCH/main\n$mirrorBase/$ALPINE_BRANCH/community\n",
        )
    }
}

object DebianSpec : DistroSpec {

    override val distro = LinuxDistro.DEBIAN

    override val archiveName = "rootfs.tar.xz"

    override fun arch(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return when {
            abi.startsWith("arm64") -> "arm64"
            abi.startsWith("armeabi") -> "armhf"
            abi.startsWith("x86_64") -> "amd64"
            abi.startsWith("x86") -> "i386"
            else -> "arm64"
        }
    }

    /**
     * Newest default image for this device's architecture, e.g.
     * `debian;bookworm;arm64;default;20260731_05:24;/images/debian/bookworm/arm64/default/20260731_05:24/`.
     */
    override fun rootfsUrls(): List<String> {
        val arch = arch()
        val index = URL(LXC_INDEX).openStream().bufferedReader().use { it.readText() }
        val line = index.lineSequence()
            .filter { it.startsWith("debian;$DEBIAN_RELEASE;$arch;default;") }
            .maxOrNull()
            ?: throw IOException("No Debian $DEBIAN_RELEASE image for $arch in LXC index")
        val path = line.split(';').getOrNull(5)?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IOException("Malformed LXC index line: $line")
        // abuild: 国内镜像优先；先试当天路径，再试前 1-2 天同一时刻（镜像滞后），官方最后。
        // 404/HTML 响应会由下载器的文件头校验剔除并继续下一个候选。
        val officialBase = "https://images.linuxcontainers.org"
        val domestic = LXC_BASE_MIRRORS.filter { it != officialBase }
        val urls = mutableListOf<String>()
        for (candidate in lxcCandidatePaths(path)) {
            domestic.forEach { base -> urls += base + candidate.removeSuffix("/") + "/rootfs.tar.xz" }
        }
        urls += officialBase + path.removeSuffix("/") + "/rootfs.tar.xz"
        return urls
    }

    override fun configure(rootfsDir: File) {
        TarExtractor.makeWritable(rootfsDir)
        TarExtractor.writeResolvConf(rootfsDir)
        // apt and dpkg assume these exist; an LXC image ships some of them empty
        // and the tar extractor skips empty directories it never saw an entry for.
        listOf(
            "var/lib/apt/lists/partial",
            "var/cache/apt/archives/partial",
            "var/lib/dpkg/updates",
            "var/lib/dpkg/info",
            "var/lib/dpkg/alternatives",
            "var/log",
            "run/lock",
            "tmp",
        ).forEach { File(rootfsDir, it).mkdirs() }
        // dpkg fsyncs every unpacked file by default, which on a phone turns a
        // base install into a multi-minute affair.
        File(rootfsDir, "etc/dpkg/dpkg.cfg.d").mkdirs()
        File(rootfsDir, "etc/dpkg/dpkg.cfg.d/force-unsafe-io").writeText("force-unsafe-io\n")
        // apt drops to `_apt` via setresuid for downloads. Work profiles and
        // system clones block that syscall (termux/proot#369), so `apt-get update`
        // fails and every package then looks missing. proot's root is fake anyway,
        // so staying root costs nothing; the file also covers shell-run apt-get.
        File(rootfsDir, "etc/apt/apt.conf.d").mkdirs()
        File(rootfsDir, "etc/apt/apt.conf.d/99kai-nosandbox").writeText("APT::Sandbox::User \"root\";\n")
        // abuild: 包管理源切到清华镜像；rootfs 本体可能来自官方或其他镜像
        File(rootfsDir, "etc/apt").mkdirs()
        File(rootfsDir, "etc/apt/sources.list").writeText(
            "deb https://mirrors.tuna.tsinghua.edu.cn/debian bookworm main contrib non-free non-free-firmware\n" +
                "deb https://mirrors.tuna.tsinghua.edu.cn/debian bookworm-updates main contrib non-free non-free-firmware\n" +
                "deb https://mirrors.tuna.tsinghua.edu.cn/debian-security bookworm-security main contrib non-free non-free-firmware\n",
        )
        // 新版镜像可能带 deb822 的 debian.sources，保留会与上面的源重复
        File(rootfsDir, "etc/apt/sources.list.d/debian.sources").delete()
    }

    /**
     * dpkg unpacks packages via hardlinks, which Android's `protected_hardlinks`
     * policy refuses inside the app sandbox. Without the emulation the base
     * install fails with a dpkg subprocess error even though `apt-get update`
     * succeeded. `-L` is the companion lstat fix.
     */
    override val prootArgs = listOf("--link2symlink", "-L")

    override val env = mapOf("DEBIAN_FRONTEND" to "noninteractive")
}
