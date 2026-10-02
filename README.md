# meta-mylayer

A Yocto Project layer I built while learning embedded Linux with Yocto.
It targets **Yocto 5.0 "Scarthgap" (LTS)** and the **`qemuarm64`** machine, and
contains:

| Path | What it shows |
|---|---|
| `recipes-hello/hello/` | A recipe written by hand for a small C program (cross-compiled with `${CC}`) |
| `recipes-devtools/file/` | A `.bbappend` that patches an existing poky recipe (`file`), created with `devtool modify` / `devtool finish` |
| `recipes-microsocks/microsocks/` | A recipe for a third-party project from GitHub, created with `devtool add`, plus a local patch |
| `recipes-core/images/my-image.bb` | A custom image recipe (`inherit core-image`) with an SSH server |
| `recipes-sysmon/sysmon/` | A CMake project (`inherit cmake`) packaged as a daemon that starts at boot, with both a SysVinit script and a systemd unit |
| `recipes-kernel/linux/` | A `linux-yocto` `.bbappend` with a kernel config fragment (exFAT as a module) |
| `sdk-example/` | A program cross-compiled outside BitBake with the SDK generated from `my-image` |

The [learning log](#learning-log) below records what I did at each step, the
problems I ran into and how I solved them.

## Building

Requirements: a Linux host set up for Yocto builds
(see the [Yocto system requirements](https://docs.yoctoproject.org/5.0/ref-manual/system-requirements.html)).

```bash
git clone -b scarthgap https://git.yoctoproject.org/poky
git clone https://github.com/dattran96/yocto-optimized-image.git meta-mylayer

cd poky
source oe-init-build-env build
bitbake-layers add-layer ../../meta-mylayer

# conf/local.conf: MACHINE ?= "qemuarm64"
bitbake my-image
runqemu my-image nographic        # log in as root
```

On the target:

```
hello                  # Hello from my own Yocto layer!
file -v                # file-5.45 (patched by Dat)
microsocks -p 1080 &   # prints "Learn yocto", SOCKS5 proxy on port 1080
```

From the host (with `runqemu` tap networking, target at `192.168.7.2`):

```bash
ssh root@192.168.7.2
curl --socks5 192.168.7.2:1080 http://192.168.7.1:8000/   # through the proxy on the target
```

## Learning log

### Step 1: First build with poky

- Built `core-image-minimal` for `qemuarm64` and booted it with `runqemu`.
- Learned where BitBake puts its output: `build/tmp/deploy/images/<machine>/`
  (kernel `Image`, root filesystem `.ext4`, `.manifest`, `.qemuboot.conf`).

### Step 2: Adding packages to an image

- Added `strace` and `file` with `IMAGE_INSTALL:append = " strace file"` in `local.conf`.
- Learned why the leading space in `:append` matters, and that the rebuild was quick
  because BitBake reused the shared-state (sstate) cache and only built what changed.
- Checked the result in the image `.manifest` and on the running target.

### Step 3: My own layer and a hello-world recipe

- Created the layer with `bitbake-layers create-layer` / `add-layer`.
- Wrote `hello_1.0.bb`: `LICENSE` / `LIC_FILES_CHKSUM`, `SRC_URI = "file://..."`,
  `do_compile` and `do_install` into `${D}${bindir}`.
- **Cross-compiling:** `${CC}` expands to `aarch64-poky-linux-gcc -mcpu=cortex-a57+crc ... --sysroot=...`.
  The compiler runs on the x86-64 host but produces an ARM64 binary (checked with `file`).
  Yocto builds this cross toolchain itself from `gcc-cross_13.4.bb`.
- Traced where the target architecture comes from:
  `MACHINE` → `conf/machine/qemuarm64.conf` → `tune-cortexa57.inc`
  (`TARGET_ARCH = "aarch64"`) → `TARGET_SYS` / `HOST_PREFIX` in `bitbake.conf` → `CC`.
  Used `bitbake-getvar -r <recipe> <VAR>` to inspect resolved values.
- Explored the recipe work directory (`tmp/work/cortexa57-poky-linux/hello/1.0/`):
  `temp/log.do_*`, `temp/run.do_*`, `image/`, `packages-split/`.

### Step 4: Patching an existing recipe with devtool

- `devtool modify file` → edited the source in `workspace/sources/file` → committed →
  `devtool finish file meta-mylayer`, which generated a patch and a `file_%.bbappend`.
- **Problem 1:** `QA Issue: Missing Upstream-Status in patch [patch-status]`.
  Patches to core-layer recipes must state their upstream status. Fixed by adding
  `Upstream-Status: Inappropriate [local customization for learning]` to the patch header.
- **Problem 2:** the `.bbappend` also patched `file-native` (the build-host tool),
  which caused hundreds of dependent tasks to rebuild. Fixed by restricting the patch
  to the target with `SRC_URI:append:class-target = " file://..."`.

### Step 5: A custom image recipe

- Wrote `my-image.bb` with `inherit core-image`, an explicit `IMAGE_INSTALL`
  (`packagegroup-core-boot` + my packages) and `IMAGE_FEATURES += "ssh-server-dropbear"`.
- Moved package selection out of `local.conf` into the layer so the image is reproducible.
- **Problem:** `runqemu qemuarm64 my-image` failed with
  `IMAGE_LINK_NAME wasn't set to find corresponding .qemuboot.conf file`.
  Reading `scripts/runqemu` showed that when a machine name is given, it runs
  `bitbake -e` without the image target, so `IMAGE_LINK_NAME` is missing.
  Solved by running `runqemu my-image nographic` (the machine comes from `local.conf`).
- Logged in to the target over SSH from the host (`ssh root@192.168.7.2`).

### Step 6: Adding a third-party project with devtool add

- `devtool add microsocks https://github.com/rofl0r/microsocks` generated a recipe
  with `SRC_URI`, a pinned `SRCREV` and `LIC_FILES_CHKSUM`.
- Reviewed the draft: the license was not recognised (`LICENSE = "Unknown"`); upstream
  `COPYING` is MIT, so I set `LICENSE = "MIT"`. Checked that `do_install` passes
  `prefix=${prefix}`, because the Makefile defaults to `/usr/local`.
- Fast edit/test loop without rebuilding the image:
  `devtool build microsocks && devtool deploy-target microsocks root@192.168.7.2`.
- **Problem:** my added `printf` only appeared when the program exited. Cause: stdout
  buffering, since the string had no `\n` and the server never exits. Fixed by adding `\n`.
- **Problem:** `devtool finish` refused with `Source tree is not clean: ?? microsocks`.
  The Makefile builds in the source directory, so the compiled binary was untracked.
  Removed the build artifact rather than using `--force`.
- Put `Upstream-Status:` in the commit message so the generated patch passed QA
  without editing.
- Added `microsocks` to `my-image` and verified it in the booted image.

### Step 7: Building and using an SDK

- Built a standard SDK for the image with `bitbake my-image -c populate_sdk`. The result is
  a self-extracting installer (`tmp/deploy/sdk/poky-glibc-x86_64-my-image-cortexa57-qemuarm64-toolchain-5.0.20.sh`, 183 MB).
- The SDK has two parts, each with its own manifest: host tools that run on any x86-64 PC
  (the `nativesdk` cross-compiler, `pkg-config`, ...) and a target sysroot with the headers and
  libraries of `my-image` (e.g. `libz-dev 1.3.1`, matching `libz1 1.3.1` in the image).
- Installed it without root (`-d ~/yocto-sdk/my-image`) and used it in a clean shell with no
  BitBake environment: `source environment-setup-cortexa57-poky-linux` sets `CC`, `CFLAGS`,
  `LDFLAGS` and `PKG_CONFIG_*`, just like BitBake does for recipes.
- Cross-compiled [`sdk-example/zversion.c`](sdk-example/zversion.c) against zlib from the SDK sysroot:
  ```bash
  $CC $CFLAGS $LDFLAGS zversion.c -o zversion -lz
  scp zversion root@192.168.7.2:/tmp/ && ssh root@192.168.7.2 /tmp/zversion
  ```
  For comparison, the same file built with the host `gcc` is an x86-64 binary linked
  against the host's zlib, and it does not run on the target.
- **Problem:** the SDK build failed with `No space left on device`. `tmp/work` had grown to
  62 GB, because BitBake keeps every recipe's work directory, and the SDK added a second
  toolchain (`gcc-cross-canadian`, `nativesdk-qemu`, ...) of about 20 GB.
  The disk space monitor (`BB_DISKMON_DIRS`) first stopped starting new tasks (`STOPTASKS`) and
  then halted the build (`HALT`).
  **Fix:** `INHERIT += "rm_work"` (with `RM_WORK_EXCLUDE` for the recipes I develop) and a
  clean `tmp/`. Finished recipes are restored from the sstate cache, and `tmp/` dropped from
  72 GB to 16 GB.

### Step 8: A CMake project as a boot service

- Wrote `sysmon`, a small C daemon that logs the load average and available memory to
  syslog at a fixed interval and exits cleanly on `SIGTERM`, with a `CMakeLists.txt` that uses
  `GNUInstallDirs` instead of hard-coded paths.
- The recipe has no `do_configure` / `do_compile`: `inherit cmake` runs CMake with Yocto's
  toolchain file and installs into `${D}`. The build happens in a separate build directory (`B` ≠ `S`).
- `SRC_URI = "file://sysmon"` fetches a whole directory; `S = "${WORKDIR}/sysmon"`.
- Supports both init systems, like the recipes in poky:
  - SysVinit: `inherit update-rc.d`, `INITSCRIPT_NAME` / `INITSCRIPT_PARAMS = "defaults 90"`, and an
    init script using `start-stop-daemon` (background, pid file, stop by `SIGTERM`).
  - systemd: `inherit systemd`, `SYSTEMD_SERVICE:${PN}` and a `.service` unit.
  - `do_install:append` uses `bb.utils.contains('DISTRO_FEATURES', ...)` so only the files for
    the active init system are installed (this build uses `INIT_MANAGER = "sysvinit"`).
- Verified on the target: the service starts at boot via `/etc/rc5.d/S90sysmon`, logs to
  `/var/log/messages` every 10 s, and `/etc/init.d/sysmon stop|start|status` work
  (the log shows `stopped` from the signal handler, then a new PID).
- **Problem:** `ERROR: Nothing PROVIDES 'sysmon'`. The recipe was in `recipes-sysmon/` instead of
  `recipes-sysmon/sysmon/`. `BBFILES` in `conf/layer.conf` only matches `recipes-*/*/*.bb`,
  so the file was never parsed. Moved the recipe and `files/` one level down.

### Step 8b: Switching the image from SysVinit to systemd

- Set `INIT_MANAGER = "systemd"` in `local.conf`. In poky this pulls in
  `conf/distro/include/init-manager-systemd.inc`, which adds `systemd usrmerge` to
  `DISTRO_FEATURES` and points the `VIRTUAL-RUNTIME_*` variables (init manager, device manager,
  init scripts) to systemd packages. Checked the result with `bitbake-getvar`.
- Because `DISTRO_FEATURES` is part of almost every task signature, nearly the whole image was
  rebuilt. The SysVinit results stay in the sstate cache, so switching back is quick.
- The `sysmon` recipe was **not changed**: with systemd in `DISTRO_FEATURES` it installs
  `sysmon.service` instead of `/etc/init.d/sysmon`, and the `systemd` class enables the unit.
- In the image manifest, `sysvinit`, `initscripts` and `eudev` are replaced by `systemd`,
  `systemd-udev-rules`, `systemd-serialgetty` and `systemd-compat-units` (96 packages in total).
- **Finding:** `busybox-syslog` is still installed next to the systemd journal, because
  `packagegroup-core-boot` always pulls in `${VIRTUAL-RUNTIME_base-utils-syslog}` and poky
  defaults it to `busybox-syslog` (`default-providers.inc`). Two loggers is a candidate for
  later image optimization.
- Trade-off: systemd costs more flash and RAM than SysVinit + BusyBox, but adds restart on
  failure, a structured journal, dependency-based parallel startup and `systemd-analyze`.
  A real decision needs measurements (e.g. with `buildhistory` / `buildhistory-diff`).

### Step 9a: Kernel configuration fragments

- `linux-yocto` builds its `.config` from a base configuration plus small fragments. Added my own
  fragment from the layer: `recipes-kernel/linux/files/exfat.cfg` (`CONFIG_EXFAT_FS=m`) and
  `linux-yocto_%.bbappend` with `SRC_URI += "file://exfat.cfg"`. The kernel classes merge `.cfg`
  files from `SRC_URI` automatically, and `do_kernel_configcheck` warns if a requested option
  does not end up in the final `.config`.
- Verified `CONFIG_EXFAT_FS=m` in `tmp/work-shared/qemuarm64/kernel-build-artifacts/.config`.
  Kconfig added the dependent default `CONFIG_EXFAT_DEFAULT_IOCHARSET="utf8"` by itself.
- `=m` builds a loadable module that is packaged separately as `kernel-module-exfat`, so it
  had to be added to `IMAGE_INSTALL` in `my-image.bb`. The running kernel exposes its
  configuration in `/proc/config.gz` (`CONFIG_IKCONFIG_PROC=y`).
- Practised the interactive workflow:
  1. `bitbake linux-yocto -c menuconfig`: find an option with `/`, set it with `y` / `m` / `n`, save.
  2. `bitbake linux-yocto -c diffconfig`: writes only the changes to `${WORKDIR}/fragment.cfg`
     (tried with `NTFS3_FS`, which produced `CONFIG_NTFS3_FS=m` plus its sub-options).
  3. Copy the fragment into the layer under a meaningful name and add it to `SRC_URI`.
  Changes made only in `menuconfig` are temporary: the next configuration run regenerates
  `.config` from the recipe and the layer's fragments.
- **Observation:** running `diffconfig` after `menuconfig` on an option that was already set by
  my fragment produced no file at all. The configuration had not changed, so there was nothing to write.
- Decided **not** to keep the NTFS3 fragment: every enabled option adds kernel size, attack surface
  and maintenance, so a fragment should only exist for a feature the product needs.

### Other things learned along the way

- `Ctrl-Z` pauses a command instead of cancelling it. A paused `devtool build` kept
  holding the BitBake server, so every following `bitbake` / `devtool` command
  waited forever at `Reconnecting to bitbake server...`.
- Run BitBake commands from the build directory, and use absolute paths (or check the
  current directory) for arguments like the layer path in `devtool finish`.

## Next steps

- [x] Build an SDK (`bitbake my-image -c populate_sdk`) and compile against it outside Yocto
- [x] Write a recipe for a CMake project and a systemd/SysVinit service
- [x] Kernel: configuration fragments
- [ ] Kernel: an out-of-tree kernel module recipe, loaded at boot
- [ ] Create my own distro config instead of using `poky`
- [ ] Build for real hardware (e.g. Raspberry Pi with `meta-raspberrypi`)
