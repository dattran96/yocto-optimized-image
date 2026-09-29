SUMMARY = "Multithreaded, small, efficient SOCKS5 server"
HOMEPAGE = "https://github.com/rofl0r/microsocks"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://COPYING;md5=cb7fdf0917b18defb45c38cd9f9409fc"

SRC_URI = "git://github.com/rofl0r/microsocks;protocol=https;branch=master \
           file://0001-microsocks-print-a-message-at-startup.patch \
           "

PV = "1.0+git"
SRCREV = "307950cb1296b521fe86384fd695062ade5304b5"

S = "${WORKDIR}/git"

# Plain Makefile project: nothing to configure. The empty task also stops
# base.bbclass from running "make clean" when the configuration changes.
do_configure () {
	:
}

do_compile () {
	oe_runmake
}

# The Makefile defaults to prefix=/usr/local; install to /usr instead.
do_install () {
	oe_runmake install 'DESTDIR=${D}' 'prefix=${prefix}'
}
