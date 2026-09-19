cmake_minimum_required(VERSION 3.22.1)
project("pocketforgellm")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Headers come from an MNN framework checkout passed in by Gradle; the matching
# libMNN.so is vendored under src/main/jniLibs/<abi>.
if(NOT DEFINED MNN_SOURCE_ROOT)
    message(FATAL_ERROR "MNN_SOURCE_ROOT not set (see gradle.properties)")
endif()

include_directories("${MNN_SOURCE_ROOT}/include")
include_directories("${MNN_SOURCE_ROOT}/transformers/llm/engine/include")

add_library(${CMAKE_PROJECT_NAME} SHARED llm_jni.cpp)

# 16KB page alignment (Android 15+ devices).
target_link_options(${CMAKE_PROJECT_NAME} PRIVATE "-Wl,-z,max-page-size=16384")

add_library(MNN SHARED IMPORTED)
set_target_properties(MNN PROPERTIES IMPORTED_LOCATION
    "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libMNN.so")

target_link_libraries(${CMAKE_PROJECT_NAME} android log MNN)
