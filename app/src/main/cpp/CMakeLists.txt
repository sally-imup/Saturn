cmake_minimum_required(VERSION 3.22.1)
project(saturn_jre C)

add_library(saturn_jre SHARED jre_launcher.c)

find_library(log-lib log)
target_link_libraries(saturn_jre ${log-lib} dl)
