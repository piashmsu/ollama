cmake_minimum_required(VERSION 3.22.1)
project("dolphinai")

# Phase 3 activates real llama.cpp inference. The submodule under
# app/src/main/cpp/llama.cpp is built as part of this project. To fall back
# to the stub for very lean builds, pass `-DUSE_LLAMA_CPP=OFF`.
option(USE_LLAMA_CPP "Build with bundled llama.cpp inference" ON)

add_library(dolphinai SHARED
    dolphinai_jni.cpp
)

target_include_directories(dolphinai PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

if(USE_LLAMA_CPP)
    # Trim llama.cpp's optional surface area for an Android-only build.
    set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_COMMON   OFF CACHE BOOL "" FORCE)
    set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)
    set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)
    set(GGML_LLAMAFILE       OFF CACHE BOOL "" FORCE)
    set(GGML_BUILD_TESTS     OFF CACHE BOOL "" FORCE)
    set(GGML_BUILD_EXAMPLES  OFF CACHE BOOL "" FORCE)
    # arm64-v8a / Android-NDK-friendly defaults
    set(GGML_CCACHE          OFF CACHE BOOL "" FORCE)

    add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp ${CMAKE_BINARY_DIR}/llama.cpp)

    target_include_directories(dolphinai PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
        ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include
    )
    target_link_libraries(dolphinai PRIVATE llama ggml)
    target_compile_definitions(dolphinai PRIVATE DOLPHIN_USE_LLAMA_CPP=1)
endif()

find_library(log-lib log)
target_link_libraries(dolphinai PRIVATE ${log-lib} android)
