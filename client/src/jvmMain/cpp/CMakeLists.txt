# ============================================================
# CMakeLists.txt — Libretro JNI bridge
#
# Builds `emulator_core` as a shared library that:
#   - Exposes JNI functions to Kotlin
#   - Loads libretro cores at runtime via dlopen / LoadLibrary
#
# Output:
#   - macOS:   libemulator_core.dylib
#   - Linux:   libemulator_core.so
#   - Windows: emulator_core.dll
#
# The output is placed in resources/native/<platform>/ so it
# gets bundled into the Compose Desktop app automatically.
# ============================================================

cmake_minimum_required(VERSION 3.20)
project(emulator_core LANGUAGES CXX)

# --- C++ standard ---
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# --- Default to Release if no build type specified ---
if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    set(CMAKE_BUILD_TYPE Release CACHE STRING "Build type" FORCE)
endif()

# --- JNI ---
find_package(JNI REQUIRED)

# ============================================================
# The shared library
# ============================================================

add_library(emulator_core SHARED
        libretro_bridge.cpp
)

target_include_directories(emulator_core PRIVATE
        ${JNI_INCLUDE_DIRS}
        ${CMAKE_CURRENT_SOURCE_DIR}/libretro-common/include
)

# ============================================================
# Platform-specific configuration
# ============================================================

if(APPLE)
    # macOS: detect arch (arm64 vs x86_64) for output folder
    if(CMAKE_OSX_ARCHITECTURES MATCHES "arm64" OR CMAKE_HOST_SYSTEM_PROCESSOR STREQUAL "arm64")
        set(PLATFORM_FOLDER "macos-arm64")
    else()
        set(PLATFORM_FOLDER "macos-x64")
    endif()

    # Allow loading dylibs from anywhere via @rpath
    set_target_properties(emulator_core PROPERTIES
            MACOSX_RPATH ON
            INSTALL_RPATH "@loader_path"
    )

    # Link to dl (already part of libSystem on macOS, but explicit is fine)

elseif(UNIX)
    # Linux
    if(CMAKE_HOST_SYSTEM_PROCESSOR MATCHES "aarch64|arm64")
        set(PLATFORM_FOLDER "linux-arm64")
    else()
        set(PLATFORM_FOLDER "linux-x64")
    endif()

    target_link_libraries(emulator_core PRIVATE dl)

    # rpath for finding sibling libraries
    set_target_properties(emulator_core PROPERTIES
            INSTALL_RPATH "$ORIGIN"
            BUILD_WITH_INSTALL_RPATH TRUE
    )

elseif(WIN32)
    set(PLATFORM_FOLDER "windows-x64")

    # On Windows, drop the "lib" prefix so the file is `emulator_core.dll`
    set_target_properties(emulator_core PROPERTIES PREFIX "")

    # MSVC: enable wide-char support and disable some noisy warnings
    if(MSVC)
        target_compile_definitions(emulator_core PRIVATE
                _CRT_SECURE_NO_WARNINGS
                UNICODE
                _UNICODE
                NOMINMAX
        )
        target_compile_options(emulator_core PRIVATE /W3)
    else()
        # MinGW
        target_compile_options(emulator_core PRIVATE -Wall)
    endif()
endif()

# ============================================================
# Output location
#
# Send the built library straight into the Kotlin resources tree
# so Compose Desktop picks it up at packaging time.
#
# Final path:
#   composeApp/src/jvmMain/resources/native/<platform>/<libname>
# ============================================================

# Adjust this path if your project layout differs.
# Default assumes: cpp/CMakeLists.txt → ../resources/native/...
set(OUTPUT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../resources/native/${PLATFORM_FOLDER}")

set_target_properties(emulator_core PROPERTIES
        LIBRARY_OUTPUT_DIRECTORY         "${OUTPUT_DIR}"   # .so / .dylib
        LIBRARY_OUTPUT_DIRECTORY_RELEASE "${OUTPUT_DIR}"
        LIBRARY_OUTPUT_DIRECTORY_DEBUG   "${OUTPUT_DIR}"
        RUNTIME_OUTPUT_DIRECTORY         "${OUTPUT_DIR}"   # .dll on Windows
        RUNTIME_OUTPUT_DIRECTORY_RELEASE "${OUTPUT_DIR}"
        RUNTIME_OUTPUT_DIRECTORY_DEBUG   "${OUTPUT_DIR}"
)

# Ensure the output directory exists before linking
add_custom_command(TARGET emulator_core PRE_LINK
        COMMAND ${CMAKE_COMMAND} -E make_directory "${OUTPUT_DIR}"
)

# ============================================================
# Status output (so you can see what got configured)
# ============================================================

message(STATUS "============================================")
message(STATUS "  emulator_core build configuration")
message(STATUS "============================================")
message(STATUS "  Build type:      ${CMAKE_BUILD_TYPE}")
message(STATUS "  Platform folder: ${PLATFORM_FOLDER}")
message(STATUS "  Output dir:      ${OUTPUT_DIR}")
message(STATUS "  C++ standard:    ${CMAKE_CXX_STANDARD}")
message(STATUS "  JNI include:     ${JNI_INCLUDE_DIRS}")
message(STATUS "============================================")