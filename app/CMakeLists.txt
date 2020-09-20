
cmake_minimum_required(VERSION 3.10)

project(NativeVNC C CXX ASM)

option(AVNC_BUILD_ALL "Build with JPEG an SSL" OFF)
set(AVNC_EXTERN_DIR ../extern)
set(BUILD_SHARED_LIBS OFF)

###############################################################################
# Utilities
###############################################################################

# Make sure given submodule is checked out
macro(avnc_check_submodule name)
    if (NOT EXISTS "${PROJECT_SOURCE_DIR}/${AVNC_EXTERN_DIR}/${name}/CMakeLists.txt")
        message(FATAL_ERROR "git submodule for ${name} is not initialized.
                             Please run 'git submodule update --init'.")
    endif ()
endmacro()

# Make sure libressl-portable is initialized
macro(avnc_check_libressl)
    if (NOT EXISTS "${PROJECT_SOURCE_DIR}/${AVNC_EXTERN_DIR}/libressl/VERSION")
        message(FATAL_ERROR "libressl is not initialized properly.
                             Please run './autogen.sh' in 'libressl' submodule.")
    endif ()
endmacro()


# Requred to enable SIMD support
if (CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    set(CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS} --target=aarch64-linux-android${ANDROID_VERSION}")
elseif (CMAKE_ANDROID_ARCH_ABI MATCHES "^armeabi.*")  # armeabi-v7a || armeabi-v6 || armeabi
    set(CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS} --target=arm-linux-androideabi${ANDROID_VERSION}")
endif ()


###############################################################################
# JPEG
###############################################################################
if (AVNC_BUILD_ALL)
    avnc_check_submodule(libjpeg-turbo)

    set(AVNC_LIBJPEG_SRC_DIR ${AVNC_EXTERN_DIR}/libjpeg-turbo)
    set(AVNC_LIBJPEG_BUILD_DIR ${CMAKE_BINARY_DIR}/libjpeg-turbo)

    add_subdirectory(${AVNC_LIBJPEG_SRC_DIR} ${AVNC_LIBJPEG_BUILD_DIR})

    # Set these variables so FindJPEG can find the library
    set(JPEG_LIBRARY ${AVNC_LIBJPEG_BUILD_DIR}/libturbojpeg.a)
    set(JPEG_INCLUDE_DIR ${AVNC_LIBJPEG_SRC_DIR})

    include_directories(${AVNC_LIBJPEG_SRC_DIR} ${AVNC_LIBJPEG_BUILD_DIR})
endif ()

###############################################################################
# SSL
###############################################################################
if (AVNC_BUILD_ALL)
    avnc_check_submodule(libressl)
    avnc_check_libressl()

    set(AVNC_LIBSSL_SRC_DIR ${AVNC_EXTERN_DIR}/libressl)
    set(AVNC_LIBSSL_BUILD_DIR ${CMAKE_BINARY_DIR}/libressl)

    add_subdirectory(${AVNC_LIBSSL_SRC_DIR} ${AVNC_LIBSSL_BUILD_DIR})

    # Set these variables so FindOpenSSL can find the library
    set(OPENSSL_SSL_LIBRARY ${AVNC_LIBSSL_BUILD_DIR}/ssl/libssl.a)
    set(OPENSSL_CRYPTO_LIBRARY ${AVNC_LIBSSL_BUILD_DIR}/crypto/libcrypto.a)
    set(OPENSSL_INCLUDE_DIR ${AVNC_LIBSSL_BUILD_DIR}/include)

    include_directories(${AVNC_LIBSSL_SRC_DIR}/include)
endif ()


###############################################################################
# LibVNC
###############################################################################
avnc_check_submodule(libvncserver)

set(AVNC_LIBVNC_SRC_DIR ${AVNC_EXTERN_DIR}/libvncserver)
set(AVNC_LIBVNC_BUILD_DIR ${CMAKE_BINARY_DIR}/libvncserver)

add_subdirectory(${AVNC_LIBVNC_SRC_DIR} ${AVNC_LIBVNC_BUILD_DIR})  # (source dir, build dir)

include_directories(${AVNC_LIBVNC_SRC_DIR} ${AVNC_LIBVNC_BUILD_DIR})


###############################################################################
# Native VNC
#
# It contains implementation of JNI native methods, some NDK scaffolding and
# some helpers for OpenGL ES rendring. This is the library loaded from Java.
###############################################################################
set(AVNC_NATIVE_SOURCE src/main/cpp/native-vnc.cpp)

add_library(native-vnc SHARED ${AVNC_NATIVE_SOURCE})

target_link_libraries(native-vnc vncclient)


# Link NDK libraries
find_library(LIB_LOG log)
target_link_libraries(native-vnc ${LIB_LOG})

find_library(LIB_GLES GLESv2)
target_link_libraries(native-vnc ${LIB_GLES})
