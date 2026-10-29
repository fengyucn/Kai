package com.inspiredandroid.kai.build

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The coding agents Kai Build can install on a fresh Debian. Each one ships a
 * self-contained installer, so the base system plus curl is all they need.
 *
 * [binDirs] lists the guest directories a vendor installer may use; PATH and
 * binary detection are derived from this catalog rather than hard-coded.
 */
@Immutable
data class BuildAgent(
    val id: String,
    /** Product name — shown as-is, not translated. */
    val title: String,
    val binary: String,
    val installCommand: String,
    /** Guest dirs that may hold [binary] after install (for PATH and linking). */
    val binDirs: List<String> = listOf("/root/.local/bin"),
)

object BuildAgents {

    val all: ImmutableList<BuildAgent> = persistentListOf(
        BuildAgent(
            id = "claude-code",
            title = "Claude Code",
            binary = "claude",
            installCommand = "curl -fsSL https://claude.ai/install.sh | bash",
            binDirs = listOf("/root/.local/bin"),
        ),
        BuildAgent(
            id = "qoder",
            title = "Qoder CLI",
            binary = "qodercli",
            installCommand = "curl -fsSL https://qoder.com/install | bash",
            binDirs = listOf("/root/.qoder/bin", "/root/.local/bin"),
        ),
        BuildAgent(
            id = "opencode",
            title = "OpenCode",
            binary = "opencode",
            installCommand = "curl -fsSL https://opencode.ai/install | bash",
            binDirs = listOf("/root/.opencode/bin", "/root/.local/bin"),
        ),
    )

    fun get(id: String?): BuildAgent? = all.firstOrNull { it.id == id }
}
