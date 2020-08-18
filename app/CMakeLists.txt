
cmake_minimum_required(VERSION 3.10)

project(NativeVNC C CXX)

#LibVNCClient
set(NATIVE_LIBVNC_SOURCE ../libvncserver)
add_subdirectory(${NATIVE_LIBVNC_SOURCE} libvncserver)  # (source dir, build dir)


# Native VNC Helper
#
# It contains implementation of JNI native methods, some NDK scaffolding and
# some helpers for OpenGL ES rendring.
#
# It also wraps LibVNCClient so that we have to manage only one native library
# from Java side.
set(NATIVE_VNC_SOURCE src/main/cpp/native-vnc.cpp)

add_library(native-vnc SHARED ${NATIVE_VNC_SOURCE})

target_link_libraries(native-vnc vncclient)
target_include_directories(native-vnc PRIVATE ../libvncserver)
target_include_directories(native-vnc PRIVATE ${CMAKE_CURRENT_BINARY_DIR}/libvncserver)

# Link NDK libraries
find_library(LIB_LOG log)
target_link_libraries(native-vnc ${LIB_LOG})

find_library(LIB_GLES GLESv2)
target_link_libraries(native-vnc ${LIB_GLES})
