
cmake_minimum_required(VERSION 3.13)

###############################################################################
# vcpkg setup
# This must be done before project() is called.
###############################################################################
if (ANDROID_ABI STREQUAL "arm64-v8a")
    set(VCPKG_TARGET_TRIPLET "arm64-android")
elseif (ANDROID_ABI STREQUAL "armeabi-v7a")
    set(VCPKG_TARGET_TRIPLET "arm-neon-android")
elseif (ANDROID_ABI STREQUAL "x86")
    set(VCPKG_TARGET_TRIPLET "x86-android")
elseif (ANDROID_ABI STREQUAL "x86_64")
    set(VCPKG_TARGET_TRIPLET "x64-android")
else ()
    message(FATAL_ERROR "Unknown ANDROID_ABI: ${ANDROID_ABI}")
endif ()

set(VCPKG_BOOTSTRAP_OPTIONS "-disableMetrics")
set(ENV{ANDROID_NDK_HOME} ${ANDROID_NDK})


###############################################################################
# Project
###############################################################################
project(NativeVNC C CXX)

set(AVNC_EXTERN_DIR ${PROJECT_SOURCE_DIR}/../extern)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build shared Libs" FORCE)

###############################################################################
# Utilities
###############################################################################

# Make sure given submodule is checked out
macro(avnc_check_submodule name)
    if (NOT EXISTS "${AVNC_EXTERN_DIR}/${name}/CMakeLists.txt")
        message(FATAL_ERROR "git submodule for ${name} is not initialized.
                             Please run 'git submodule update --init'.")
    endif ()
endmacro()


# Clear build id in release builds to allow reproducible builds on F-Droid
add_link_options("LINKER:$<$<NOT:$<CONFIG:Debug>>:--build-id=none>")


###############################################################################
# LibVNC
###############################################################################
avnc_check_submodule(libvncserver)

set(AVNC_LIBVNC_SRC_DIR ${AVNC_EXTERN_DIR}/libvncserver)
set(AVNC_LIBVNC_BUILD_DIR ${CMAKE_BINARY_DIR}/libvncserver)
set(WITH_LIBSSH2 OFF CACHE BOOL "Find LibSSH" FORCE)

add_subdirectory(${AVNC_LIBVNC_SRC_DIR} ${AVNC_LIBVNC_BUILD_DIR})  # (source dir, build dir)

include_directories(${AVNC_LIBVNC_SRC_DIR}/include ${AVNC_LIBVNC_BUILD_DIR}/include)


###############################################################################
# Native VNC
#
# It contains implementation of JNI native methods, some NDK scaffolding and
# some helpers for OpenGL ES rendering. This is the library loaded from Java.
###############################################################################
set(AVNC_NATIVE_SOURCE src/main/cpp/native-vnc.cpp)

add_library(native-vnc SHARED ${AVNC_NATIVE_SOURCE})

target_link_libraries(native-vnc vncclient)


# Link NDK libraries
find_library(LIB_LOG log)
target_link_libraries(native-vnc ${LIB_LOG})

find_library(LIB_GLES GLESv2)
target_link_libraries(native-vnc ${LIB_GLES})
