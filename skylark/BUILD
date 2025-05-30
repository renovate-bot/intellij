load("@rules_java//java:defs.bzl", "java_library")
load(
    "//:build-visibility.bzl",
    "DEFAULT_TEST_VISIBILITY",
    "PLUGIN_PACKAGES_VISIBILITY",
    "SKYLARK_PACKAGES_VISIBILITY",
)
load(
    "//build_defs:build_defs.bzl",
    "intellij_plugin",
    "intellij_plugin_library",
    "stamped_plugin_xml",
)

#
# Description: A Starlark debugging client for IntelliJ.
#
load(
    "//testing:test_defs.bzl",
    "intellij_unit_test_suite",
)

licenses(["notice"])

java_library(
    name = "skylark",
    srcs = glob(["src/**/*.java"]),
    visibility = SKYLARK_PACKAGES_VISIBILITY,
    deps = [
        "//base",
        "//common/experiments",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//proto:proto_deps",
    ],
)

intellij_plugin_library(
    name = "plugin_library",
    plugin_xmls = ["src/META-INF/blaze-skylark.xml"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [":skylark"],
)

stamped_plugin_xml(
    name = "skylark_plugin_xml",
    plugin_id = "com.google.idea.blaze.skylark",
    plugin_name = "com.google.idea.blaze.skylark",
)

intellij_plugin(
    name = "skylark_integration_test_plugin",
    testonly = 1,
    plugin_xml = ":skylark_plugin_xml",
    tags = [
        "incomplete-deps",  # remove this suppression and add any missing deps, see go/java-import-deps-checking-lsc
    ],
    deps = [
        ":plugin_library",
        "//base:plugin_library",
    ],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    test_package_root = "com.google.idea.blaze.skylark",
    visibility = DEFAULT_TEST_VISIBILITY,
    deps = [
        ":skylark",
        "//base",
        "//base:unit_test_utils",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//testing:lib",
        "//third_party/java/junit",
    ],
)
