cmake_minimum_required(VERSION 3.10)
project(sysmon VERSION 1.0 LANGUAGES C)

include(GNUInstallDirs)

add_executable(sysmon main.c)
target_compile_options(sysmon PRIVATE -Wall -Wextra)

install(TARGETS sysmon RUNTIME DESTINATION ${CMAKE_INSTALL_SBINDIR})
