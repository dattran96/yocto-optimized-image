FILESEXTRAPATHS:prepend := "${THISDIR}/${PN}:"

SRC_URI:append:class-target = " file://0001-file-mark-version-output-as-patched.patch"
