# abuild fork notes

Personal build branch (`abuild/domestic-mirrors`) on top of upstream tag **v3.3.0**.
It is not intended for upstream merge unless maintainers want it.

## Changes vs v3.3.0

- `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/linux/DistroSpec.kt`
  - Alpine minirootfs: domestic mirrors first (Tsinghua, NJU, BFSU, Aliyun, SJTU), official/overseas fallback
  - Debian LXC rootfs: domestic mirrors first (Tsinghua, BFSU, CERNET, NJU), retry previous 1-2 daily
    timestamps for lagging mirrors, official `images.linuxcontainers.org` as the last fallback
  - Debian apt sources rewritten to Tsinghua `bookworm` / `bookworm-updates` / `bookworm-security`
- `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/linux/RootfsDownloader.kt`
  - Validate gzip/xz magic bytes after download; a mirror answering HTTP 200 with an HTML page
    (observed on HuaweiCloud's LXC mirror) is rejected and the next candidate is tried
- Coding agents: **Grok replaced by Qoder CLI** (`qodercli`, installed via `https://qoder.com/install`).
  Agent bin dirs, PATH and detection are now catalog-driven (`BuildAgent.binDirs`), so adding or
  replacing an agent no longer requires hard-coded paths in the environment manager.
- Removed the Grok-only x.ai device-code hint; login URLs are captured generically from PTY text,
  OSC 8/52 hyperlinks, and the `$BROWSER` wrapper.

## Notes

- No signing keystore, credentials, API keys, or environment-specific configuration is included
  in this repository or branch.
- Personal APKs are built locally and signed separately; they are not published here.
- Upstream sync: rebase this branch onto a new upstream tag and re-apply the corresponding patches
  kept in the local build workspace.

## License

Modifications are provided under the same Apache-2.0 license as upstream.
