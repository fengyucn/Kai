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
            id = "codebuddy",
            title = "CodeBuddy",
            binary = "codebuddy",
            installCommand = "curl -fsSL https://www.codebuddy.cn/cli/install.sh | bash",
            binDirs = listOf("/root/.codebuddy/bin", "/root/.local/bin"),
        ),
        BuildAgent(
            id = "qoder",
            title = "Qoder CLI",
            binary = "qodercli",
            installCommand = "curl -fsSL https://qoder.com/install | bash",
            binDirs = listOf("/root/.qoder/bin", "/root/.local/bin"),
        ),
        BuildAgent(
            id = "kimicode",
            title = "Kimi Code",
            binary = "kimi",
            installCommand = "curl -fsSL https://code.kimi.com/kimi-code/install.sh | bash",
            binDirs = listOf("/root/.kimi-code/bin", "/root/.local/bin"),
        ),
    )

    fun get(id: String?): BuildAgent? = all.firstOrNull { it.id == id }
}
