# Native library for the PhotoScan Android app.
#
# Built by Gradle through externalNativeBuild. It needs:
#   COLMAP_SRC     patched COLMAP source tree made by scripts/prepare_colmap.sh
#   PREFIX_PATH    installed dependencies made by scripts/build_deps.sh
#                  (install/arm64-v8a)
cmake_minimum_required(VERSION 3.22)
project(photoscan_native CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT COLMAP_SRC)
  message(FATAL_ERROR "Set -DCOLMAP_SRC to the patched COLMAP source tree")
endif()
if(NOT PREFIX_PATH)
  message(FATAL_ERROR "Set -DPREFIX_PATH to the dependency install prefix")
endif()

list(APPEND CMAKE_PREFIX_PATH "${PREFIX_PATH}")

# Only the library parts of COLMAP are needed: no GUI, CUDA, tests or Python.
set(GUI_ENABLED OFF CACHE BOOL "" FORCE)
set(CUDA_ENABLED OFF CACHE BOOL "" FORCE)
set(OPENGL_ENABLED OFF CACHE BOOL "" FORCE)
set(TESTS_ENABLED OFF CACHE BOOL "" FORCE)
set(MVS_ENABLED ON CACHE BOOL "" FORCE)
set(ONNX_ENABLED OFF CACHE BOOL "" FORCE)
set(FETCH_ONNX OFF CACHE BOOL "" FORCE)
set(FETCH_FAISS OFF CACHE BOOL "" FORCE)
set(CGAL_ENABLED OFF CACHE BOOL "" FORCE)
set(OPENMP_ENABLED OFF CACHE BOOL "" FORCE)
set(DOWNLOAD_ENABLED OFF CACHE BOOL "" FORCE)
set(UNINSTALL_ENABLED OFF CACHE BOOL "" FORCE)
set(CCACHE_ENABLED OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory("${COLMAP_SRC}" colmap-build EXCLUDE_FROM_ALL)

add_library(photoscan_native SHARED jni/photoscan_jni.cc)
target_link_libraries(photoscan_native PRIVATE colmap_controllers colmap_scene log)
target_include_directories(photoscan_native PRIVATE "${COLMAP_SRC}/src")
