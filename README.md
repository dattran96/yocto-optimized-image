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

### Other things learned along the way

- `Ctrl-Z` pauses a command instead of cancelling it. A paused `devtool build` kept
  holding the BitBake server, so every following `bitbake` / `devtool` command
  waited forever at `Reconnecting to bitbake server...`.
- Run BitBake commands from the build directory, and use absolute paths (or check the
  current directory) for arguments like the layer path in `devtool finish`.

## Next steps

- [ ] Build an SDK (`bitbake my-image -c populate_sdk`) and compile against it outside Yocto
- [ ] Write a recipe for a CMake project and a systemd/SysVinit service
- [ ] Kernel: configuration fragments and a kernel module recipe
- [ ] Create my own distro config instead of using `poky`
- [ ] Build for real hardware (e.g. Raspberry Pi with `meta-raspberrypi`)
