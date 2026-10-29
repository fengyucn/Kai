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
- Coding agents are fully catalog-driven (`BuildAgent.binDirs`); PATH, install dirs and binary
  detection are derived from the catalog instead of hard-coded vendor paths.
  - **CodeBuddy** (`codebuddy`, `https://www.codebuddy.cn/cli/install.sh`)
  - **Qoder CLI** (`qodercli`, `https://qoder.com/install`)
  - **Kimi Code** (`kimi`, `https://code.kimi.com/kimi-code/install.sh`)
- Login capture for narrow terminals:
  - The rootfs gets `xdg-open` / `sensible-browser` / `x-www-browser` wrappers that record the
    URL into the in-app link bar (Node CLIs often call xdg-open instead of honoring `$BROWSER`).
  - Catalog agents may declare `deviceCodeUrlTemplate`; when only a short device code is visible,
    the canonical login URL is rebuilt from the code. Kimi Code uses this as a fallback.
- Login URLs are captured generically from PTY text, OSC 8/52 hyperlinks, `$BROWSER`, and the
  xdg-open wrappers; vendor-specific x.ai device-code hints were removed.

## Notes

- No signing keystore, credentials, API keys, or environment-specific configuration is included
  in this repository or branch.
- Personal APKs are built locally and signed separately; they are not published here.
- Upstream sync: rebase this branch onto a new upstream tag and re-apply the corresponding patches
  kept in the local build workspace.

## License

Modifications are provided under the same Apache-2.0 license as upstream.
