cmake_minimum_required(VERSION 3.22.1)
project("nodehost")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Embedding headers (node.h, uv.h, v8.h) shipped with the nodejs-mobile build.
include_directories("${CMAKE_CURRENT_SOURCE_DIR}/include/node")

add_library(nodehost SHARED node_host.cpp)

add_library(node SHARED IMPORTED)
set_target_properties(node PROPERTIES IMPORTED_LOCATION
    "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libnode.so")

# 16KB page alignment for our own library (libnode.so is vendored already aligned).
target_link_options(nodehost PRIVATE "-Wl,-z,max-page-size=16384")

find_library(log-lib log)
target_link_libraries(nodehost node android ${log-lib})
