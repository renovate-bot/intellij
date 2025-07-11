# Copyright 2011 Google Inc.  All rights reserved.
#
# Description:
#   Defines a package group that restricts access to the JetBrains
#   plugin apis to known packages that build plugins. Only packages
#   listed here may depend on these libraries.

load("@rules_java//java:defs.bzl", "java_library")
load(
    "//:build-visibility.bzl",
    "INTELLIJ_PLUGINS_VISIBILITY",
    "create_test_libs_visibility_package",
)
load(
    ":build_defs.bzl",
    "select_from_plugin_api_directory",
)

package(default_visibility = INTELLIJ_PLUGINS_VISIBILITY)

licenses(["notice"])

create_test_libs_visibility_package()

config_setting(
    name = "intellij-oss-oldest-stable",
    values = {
        "define": "ij_product=intellij-oss-oldest-stable",
    },
)

config_setting(
    name = "intellij-ue-oss-oldest-stable",
    values = {
        "define": "ij_product=intellij-ue-oss-oldest-stable",
    },
)

config_setting(
    name = "intellij-oss-latest-stable",
    values = {
        "define": "ij_product=intellij-oss-latest-stable",
    },
)

config_setting(
    name = "intellij-ue-oss-latest-stable",
    values = {
        "define": "ij_product=intellij-ue-oss-latest-stable",
    },
)

config_setting(
    name = "intellij-oss-under-dev",
    values = {
        "define": "ij_product=intellij-oss-under-dev",
    },
)

config_setting(
    name = "intellij-ue-oss-under-dev",
    values = {
        "define": "ij_product=intellij-ue-oss-under-dev",
    },
)

config_setting(
    name = "intellij-under-dev",
    values = {
        "define": "ij_product=intellij-under-dev",
    },
)

config_setting(
    name = "intellij-ue-under-dev",
    values = {
        "define": "ij_product=intellij-ue-under-dev",
    },
)

config_setting(
    name = "intellij-2025.1",
    values = {"define": "ij_product=intellij-2025.1"},
)

config_setting(
    name = "intellij-2025.2",
    values = {"define": "ij_product=intellij-2025.2"},
)

config_setting(
    name = "intellij-2025.1-mac",
    values = {
        "define": "ij_product=intellij-2025.1",
        "cpu": "darwin_x86_64",
    },
)

config_setting(
    name = "intellij-2025.2-mac",
    values = {
        "define": "ij_product=intellij-2025.2",
        "cpu": "darwin_x86_64",
    },
)

config_setting(
    name = "intellij-ue-2025.1",
    values = {"define": "ij_product=intellij-ue-2025.1"},
)

config_setting(
    name = "intellij-ue-2025.2",
    values = {"define": "ij_product=intellij-ue-2025.2"},
)

config_setting(
    name = "intellij-ue-2025.1-mac",
    values = {
        "define": "ij_product=intellij-ue-2025.1",
        "cpu": "darwin_x86_64",
    },
)

config_setting(
    name = "intellij-ue-2025.2-mac",
    values = {
        "define": "ij_product=intellij-ue-2025.2",
        "cpu": "darwin_x86_64",
    },
)

config_setting(
    name = "clion-oss-oldest-stable",
    values = {
        "define": "ij_product=clion-oss-oldest-stable",
    },
)

config_setting(
    name = "clion-oss-latest-stable",
    values = {
        "define": "ij_product=clion-oss-latest-stable",
    },
)

config_setting(
    name = "clion-oss-under-dev",
    values = {
        "define": "ij_product=clion-oss-under-dev",
    },
)

config_setting(
    name = "clion-2025.1",
    values = {"define": "ij_product=clion-2025.1"},
)

config_setting(
    name = "clion-2025.2",
    values = {"define": "ij_product=clion-2025.2"},
)

config_setting(
    name = "clion-2025.1-mac",
    values = {
        "define": "ij_product=clion-2025.1",
        "cpu": "darwin_x86_64",
    },
)

config_setting(
    name = "clion-2025.2-mac",
    values = {
        "define": "ij_product=clion-2025.2",
        "cpu": "darwin_x86_64",
    },
)

# The purpose of this rule is to hide the versioning
# complexity from users of this api.
# There will be additional versions added in the future
java_library(
    name = "plugin_api_internal",
    visibility = ["//visibility:private"],
    exports = select_from_plugin_api_directory(
        clion = [
            ":sdk",
            ":clion_plugins",
        ],
        intellij = [
            ":sdk",
            ":java",
            ":kotlin",
            ":platform_images",
        ],
    ),
)

# The outward facing plugin api
java_library(
    name = "plugin_api",
    neverlink = 1,
    exports = [":plugin_api_internal"],
)

# For tests, we need the IJ API at runtime, so we can't use the neverlink rule.
java_library(
    name = "plugin_api_for_tests",
    testonly = 1,
    data = select_from_plugin_api_directory(
        # Provide access to clion bundled tools to tests (like debugers).
        clion = [":clion_tools"],
        # From 2022.1 forward, the version of kotlinc will be obtained
        # by KotlinPluginLayout from plugins/kotlin/kotlinc/build.txt
        # so we need to provide this to our tests
        intellij = [":kotlinc_version"],
    ),
    exports = [":plugin_api_internal"],
)

# These libraries should only be used by open-source plugins.
# Otherwise, prefer using the google3 versions of these libraries. Note that
# //third_party/java/truth must be repackaged, because it pulls in Guava.
java_library(
    name = "test_libs",
    testonly = 1,
    visibility = [":test_libs_visibility"],
    exports = [
        "//third_party/java/truth",
        "@bytebuddy//jar",
        "@bytebuddy_agent//jar",
        "@mockito//jar",
        "@objenesis//jar",
    ],
)

# The version of guava bundled with the IntelliJ plugin API.
java_library(
    name = "guava",
    exports = select_from_plugin_api_directory(
        clion = [":guava"],
        intellij = [":guava"],
    ),
)

java_library(
    name = "jsr305",
    exports = ["@jsr305_annotations//jar"],
)

# The version of truth bundled with the IntelliJ plugin API.
java_library(
    name = "truth",
    testonly = 1,
    exports = [
        "//third_party/java/truth",
        "@diffutils//jar",
    ],
)

# IntelliJ Mercurial plugin
java_library(
    name = "hg4idea",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        clion = [":hg4idea"],
        intellij = [":hg4idea"],
    ),
)

# IntelliJ Kotlin plugin
java_library(
    name = "kotlin",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        clion = [],
        intellij = [":kotlin"],
    ),
)

# IntelliJ Kotlin plugin
java_library(
    name = "kotlin_for_tests",
    testonly = 1,
    exports = select_from_plugin_api_directory(
        clion = [],
        intellij = [":kotlin"],
    ),
)

# IntelliJ JUnit plugin
java_library(
    name = "junit",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        clion = [],
        intellij = [":junit"],
    ),
)

# terminal plugin
java_library(
    name = "terminal",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        clion = [":terminal"],
        intellij = [":terminal"],
    ),
)

java_library(
    name = "terminal_for_tests",
    testonly = 1,
    exports = select_from_plugin_api_directory(
        clion = [":terminal"],
        intellij = [":terminal"],
    ),
)

# Bundled plugins required by integration tests
java_library(
    name = "bundled_plugins",
    testonly = 1,
    runtime_deps = select_from_plugin_api_directory(
        clion = [":bundled_plugins"],
        intellij = [":bundled_plugins"],
    ),
)

# IntelliJ Shared Indexes plugin
java_library(
    name = "shared_indexes",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        clion = [],
        intellij = [":shared_indexes"],
        intellij_ue = [":shared_indexes"],
    ),
)

# IntelliJ Shared Indexes plugin
java_library(
    name = "shared_indexes_for_tests",
    testonly = 1,
    exports = select_from_plugin_api_directory(
        clion = [],
        intellij = [":shared_indexes"],
        intellij_ue = [":shared_indexes"],
    ),
)

# IntelliJ Git4Idea plugin
java_library(
    name = "git4idea",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        clion = [],
        intellij = [":git4idea"],
    ),
)

java_library(
    name = "git4idea_for_tests",
    testonly = 1,
    exports = select_from_plugin_api_directory(
        clion = [],
        intellij = [":git4idea"],
    ),
)

# JetBrains com.jetbrains.codeWithMe plugin
java_library(
    name = "cwm",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        clion = [],
        intellij = [":cwm"],
    ),
)

java_library(
    name = "cwm_for_tests",
    testonly = 1,
    exports = select_from_plugin_api_directory(
        clion = [],
        intellij = [":cwm"],
    ),
)

# IntelliJ maven plugin
java_library(
    name = "maven",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        clion = [],
        intellij = [":maven"],
    ),
)

java_library(
    name = "maven_for_tests",
    testonly = 1,
    exports = select_from_plugin_api_directory(
        clion = [],
        intellij = [":maven_bundled_plugins"],
    ),
)

# IntelliJ maven model plugin
java_library(
    name = "maven_model",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        clion = [],
        intellij = [":maven_model"],
    ),
)

java_library(
    name = "maven_model_for_tests",
    testonly = 1,
    exports = select_from_plugin_api_directory(
        clion = [],
        intellij = [":maven_model"],
    ),
)

# IntelliJ yaml plugin
java_library(
    name = "yaml",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        clion = [],
        intellij = [":yaml"],
    ),
)

java_library(
    name = "yaml_for_tests",
    testonly = 1,
    exports = select_from_plugin_api_directory(
        clion = [],
        intellij = [":yaml"],
    ),
)

# IntelliJ gradle plugin
java_library(
    name = "gradle",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        clion = [],
        intellij = [":gradle"],
    ),
)

java_library(
    name = "gradle_for_tests",
    testonly = 1,
    exports = select_from_plugin_api_directory(
        clion = [],
        intellij = [":gradle"],
    ),
)

# IntelliJ DatabaseTools plugin
java_library(
    name = "database_tools",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        clion = [],
        intellij = [],
        intellij_ue = [":database_tools"],
    ),
)

java_library(
    name = "database_tools_for_tests",
    testonly = 1,
    exports = select_from_plugin_api_directory(
        clion = [],
        intellij = [],
        intellij_ue = [":database_tools"],
    ),
)

# IntelliJ JavaEE plugin
java_library(
    name = "javaee",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        clion = [],
        intellij = [],
        intellij_ue = [":javaee"],
    ),
)

java_library(
    name = "javaee_for_tests",
    testonly = 1,
    exports = select_from_plugin_api_directory(
        clion = [],
        intellij = [],
        intellij_ue = [":javaee"],
    ),
)

# IntelliJ Markdown plugin
java_library(
    name = "markdown",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        clion = [],
        intellij = [":markdown"],
    ),
)

java_library(
    name = "markdown_for_tests",
    testonly = 1,
    exports = select_from_plugin_api_directory(
        clion = [],
        intellij = [":markdown"],
    ),
)

java_library(
    name = "json_for_tests",
    testonly = 1,
    exports = select_from_plugin_api_directory(
        clion = [],
        intellij = [":json"],
    ),
)

filegroup(
    name = "application_info_json",
    srcs = select_from_plugin_api_directory(
        clion = [":application_info_json"],
        intellij = [":application_info_json"],
    ),
)
