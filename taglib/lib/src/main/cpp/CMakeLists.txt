
#
# Copyright (C) The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

cmake_minimum_required(VERSION 3.4.1)

# configure import libs
set(distribution_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../distribution)

# shared lib will also be tucked into APK and sent to target
# refer to app/build.gradle, jniLibs section for that purpose.
# ${ANDROID_ABI} is handy for our purpose here. Probably this ${ANDROID_ABI} is
# the most valuable thing of this sample, the rest are pretty much normal cmake
add_library(lib_taglib SHARED IMPORTED)

set_target_properties(lib_taglib PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/taglib/lib/${ANDROID_ABI}/libtaglib.so)

# build application's shared lib
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++11")

add_library(file-scanner SHARED
        FileScanner.cpp)

add_library(artwork-provider SHARED
        ArtworkProvider.cpp)

target_include_directories(file-scanner PRIVATE
        ${distribution_DIR}/taglib/include)

target_include_directories(artwork-provider PRIVATE
        ${distribution_DIR}/taglib/include)

target_link_libraries(file-scanner
        android
        lib_taglib
        log)

target_link_libraries(artwork-provider
        android
        lib_taglib
        log)