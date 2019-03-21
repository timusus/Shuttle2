# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.
cmake_minimum_required(VERSION 3.4.1)

add_subdirectory(src/main/cpp/taglib-2)

include_directories(
    src/main/cpp/taglib-2/taglib
    src/main/cpp/taglib-2/3rdparty/utf8-cpp
)

# Creates and names a library, sets it as either STATIC or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you. Gradle automatically packages shared libraries with your APK.
add_library(
        # Sets the name of the library.
        file-scanner

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        src/main/cpp/FileScanner.cpp
)

add_library(
        # Sets the name of the library.
        artwork-provider

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        src/main/cpp/ArtworkProvider.cpp
)

# Searches for a specified prebuilt library and stores the path as a variable. Because CMake includes system libraries in the search path by default, you only need to specify the name of the
# public NDK library you want to add. CMake verifies that the library exists before completing its build.
find_library(
        # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that you want CMake to locate.
        log
)

# Specifies libraries CMake should link to your target library. You can link multiple libraries, such as libraries you define in this build script, prebuilt third-party libraries, or system libraries.
target_link_libraries(
        # Specifies the target library.
        file-scanner

        tag

        # Links the target library to the log library included in the NDK.
        ${log-lib}
)
target_link_libraries(
        # Specifies the target library.
        artwork-provider

        tag

        # Links the target library to the log library included in the NDK.
        ${log-lib}
)

# This speeds up tag reading in debug builds. Possibly remove this when releasing, as presumably optimizations are already included in release builds.
target_compile_options(tag PRIVATE "$<$<CONFIG:RELEASE>:-O2>" "$<$<CONFIG:DEBUG>:-O2>")