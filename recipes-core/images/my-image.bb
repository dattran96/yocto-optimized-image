SUMMARY = "My first custom image"
LICENSE = "MIT"

inherit core-image

IMAGE_INSTALL = " \
    packagegroup-core-boot \
    ${CORE_IMAGE_EXTRA_INSTALL} \
    strace \
    file \
    hello \
    microsocks \
    sysmon \
"

IMAGE_FEATURES += "ssh-server-dropbear"
