load("@bazel_skylib//rules:copy_file.bzl", "copy_file")
load("@rules_cc//cc:cc_library.bzl", "cc_library")
load(
    "//aswb/testdata/projects:test_projects.bzl",
    "test_project_package",
)

package(
    default_visibility = ["//visibility:private"],
)

test_project_package(
    name = "test_data",
    all_targets = [
        ":cc_genhdr",
    ],
    visibility = [
        "//aswb/testdata/projects:__pkg__",
        "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    ],
)

copy_file(
    name = "generated_header",
    src = "TestClass.h_",
    out = "TestClass.h",
)

cc_library(
    name = "cc_genhdr",
    srcs = ["TestClass.cc"],
    hdrs = ["TestClass.h"],
    deps = [],
)
