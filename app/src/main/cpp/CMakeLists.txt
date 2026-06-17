cmake_minimum_required(VERSION 3.18.1)
project(kernux)

add_library(kernux-pty SHARED terminal_jni.c)

# Android NDK me PTY functions libc me hi hote hain
find_library(log-lib log)
target_link_libraries(kernux-pty ${log-lib})
