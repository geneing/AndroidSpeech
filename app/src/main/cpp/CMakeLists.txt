cmake_minimum_required(VERSION 3.22)
project(crisp_android_tts LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

option(CRISP_ANDROID_OPTIMIZED_DEBUG "Build Android debug native code with optimization and debug info" ON)

if(ANDROID AND CRISP_ANDROID_OPTIMIZED_DEBUG AND CMAKE_BUILD_TYPE STREQUAL "Debug")
    message(STATUS "CRISP_ANDROID_OPTIMIZED_DEBUG=ON: using optimized native Debug flags")
    set(CMAKE_C_FLAGS_DEBUG "-O3 -g -DNDEBUG" CACHE STRING "Android optimized debug C flags" FORCE)
    set(CMAKE_CXX_FLAGS_DEBUG "-O3 -g -DNDEBUG" CACHE STRING "Android optimized debug CXX flags" FORCE)
    add_compile_definitions(CRISP_ANDROID_OPTIMIZED_DEBUG=1)
else()
    add_compile_definitions(CRISP_ANDROID_OPTIMIZED_DEBUG=0)
endif()

if(NOT DEFINED CRISPASR_ROOT)
    get_filename_component(CRISPASR_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../../CrispASR" ABSOLUTE)
endif()

if(NOT EXISTS "${CRISPASR_ROOT}/CMakeLists.txt")
    message(FATAL_ERROR "CRISPASR_ROOT must point at the CrispASR source tree: ${CRISPASR_ROOT}")
endif()

set(CRISPASR_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(CRISPASR_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(CRISPASR_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(CRISPASR_CURL OFF CACHE BOOL "" FORCE)
set(CRISPASR_SDL2 OFF CACHE BOOL "" FORCE)
set(CRISPASR_WITH_ESPEAK_NG OFF CACHE STRING "" FORCE)

if(CRISPASR_ANDROID_ENABLE_VULKAN)
    set(GGML_VULKAN ON CACHE BOOL "" FORCE)
endif()

add_subdirectory("${CRISPASR_ROOT}" "${CMAKE_BINARY_DIR}/crispasr")

add_library(crisp_android_tts SHARED native_tts.cpp)

target_compile_definitions(crisp_android_tts PRIVATE
        CRISP_ANDROID_CMAKE_BUILD_TYPE="${CMAKE_BUILD_TYPE}")

target_include_directories(crisp_android_tts PRIVATE
        "${CRISPASR_ROOT}/src"
        "${CRISPASR_ROOT}/include"
        "${CRISPASR_ROOT}/ggml/include")

find_library(LOG_LIB log)
find_library(ANDROID_LIB android)

if(ANDROID AND LOG_LIB)
    foreach(CRISP_ANDROID_LOG_TARGET kokoro crispasr)
        if(TARGET ${CRISP_ANDROID_LOG_TARGET})
            target_link_libraries(${CRISP_ANDROID_LOG_TARGET} PUBLIC ${LOG_LIB})
        endif()
    endforeach()
endif()

target_link_libraries(crisp_android_tts
        PRIVATE
        kokoro
        vibevoice
        qwen3_tts
        ${LOG_LIB}
        ${ANDROID_LIB})
