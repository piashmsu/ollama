cmake_minimum_required(VERSION 3.22.1)
project("dolphinai")

# Phase 1 builds a thin native shim. The real llama.cpp integration is wired in
# Phase 2 by adding llama.cpp as a git submodule under app/src/main/cpp/llama.cpp
# and switching USE_LLAMA_CPP to ON.
option(USE_LLAMA_CPP "Build with bundled llama.cpp inference" OFF)

add_library(dolphinai SHARED
    dolphinai_jni.cpp
)

target_include_directories(dolphinai PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

if(USE_LLAMA_CPP)
    set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
    add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp ${CMAKE_BINARY_DIR}/llama.cpp)
    target_link_libraries(dolphinai PRIVATE llama)
    target_compile_definitions(dolphinai PRIVATE DOLPHIN_USE_LLAMA_CPP=1)
endif()

find_library(log-lib log)
target_link_libraries(dolphinai PRIVATE ${log-lib} android)
