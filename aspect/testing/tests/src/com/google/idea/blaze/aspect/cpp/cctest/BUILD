load("@rules_cc//cc:cc_test.bzl", "cc_test")
load("@rules_java//java:defs.bzl", "java_test")
load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

licenses(["notice"])

cc_test(
    name = "simple",
    srcs = ["simple/simple.cc"],
)

intellij_aspect_test_fixture(
    name = "simple_fixture",
    transitive_configs = ["//command_line_option/fragment:test"],
    deps = [":simple"],
)

java_test(
    name = "CcTestTest",
    srcs = ["CcTestTest.java"],
    data = [":simple_fixture"],
    deps = [
        "//aspect/testing:BazelIntellijAspectTest",
        "//aspect/testing:guava",
        "//aspect/testing/rules:IntellijAspectTest",
        "//aspect/testing/rules:intellij_aspect_test_fixture_java_proto",
        "//intellij_platform_sdk:test_libs",
        "//proto:intellij_ide_info_java_proto",
        "//third_party/java/junit",
    ],
)
