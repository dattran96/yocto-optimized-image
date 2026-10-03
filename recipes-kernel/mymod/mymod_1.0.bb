SUMMARY = "Example out-of-tree Linux kernel module with a parameter"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=12f884d2ae1ff87c09e5b7ccc2c4ca7e"

inherit module

SRC_URI = " \
    file://Makefile \
    file://mymod.c \
    file://COPYING \
"

S = "${WORKDIR}"

RPROVIDES:${PN} += "kernel-module-mymod"

# Load the module automatically at boot
KERNEL_MODULE_AUTOLOAD += "mymod"

# Default parameters when the module is loaded
KERNEL_MODULE_PROBECONF += "mymod"
module_conf_mymod = "options mymod whom=Dat"
