load("@rules_cc//cc:cc_library.bzl", "cc_library")

cc_library(
    name = "cc",
    srcs = ["TestClass.cc"],
    hdrs = ["TestClass.h"],
    copts = ["-w"],
    visibility = ["//querysync/javatests/com/google/idea/blaze/qsync/testdata:__subpackages__"],
)
