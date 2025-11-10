cmake_minimum_required(VERSION 3.22)
project(mgba_jni)

set(CMAKE_C_STANDARD 11)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# mGBA includes
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/mgba/include)

# Find JNI automatically
find_package(JNI REQUIRED)
include_directories(${JNI_INCLUDE_DIRS})

# Build shared library
add_library(mgba_jni SHARED EmulatorBridge.c)

# Link against mGBA core
target_link_libraries(mgba_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/mgba/build/libmgba.dylib
)
