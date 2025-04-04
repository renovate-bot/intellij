/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.testing;

import com.google.common.io.Files;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.util.PlatformUtils;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;

/**
 * Test utilities specific to running IntelliJ integration tests in a blaze/bazel environment. To be
 * used with IntellijIntegrationSuite runner.
 */
class BlazeTestSystemProperties {

  private BlazeTestSystemProperties() {}

  /** The absolute path to the runfiles directory. */
  private static final String RUNFILES_PATH = TestUtils.getUserValue("TEST_SRCDIR");

  /** Sets up the necessary system properties for running IntelliJ tests via blaze/bazel. */
  public static void configureSystemProperties() {
    File sandbox = new File(TestUtils.getTmpDirFile(), "_intellij_test_sandbox");

    setSandboxPath("idea.home.path", new File(sandbox, "home"));
    setSandboxPath("idea.config.path", new File(sandbox, "config"));
    setSandboxPath("idea.system.path", new File(sandbox, "system"));
    String testUndeclaredOutputsDir = System.getenv("TEST_UNDECLARED_OUTPUTS_DIR");
    if (testUndeclaredOutputsDir != null) {
      setSandboxPath("idea.log.path", new File(testUndeclaredOutputsDir, "logs"));
    }

    setSandboxPath("java.util.prefs.userRoot", new File(sandbox, "userRoot"));
    setSandboxPath("java.util.prefs.systemRoot", new File(sandbox, "systemRoot"));

    setIfEmpty("idea.classpath.index.enabled", "false");

    // Some plugins have a since-build and until-build restriction, so we need
    // to update the build number here
    String buildNumber = readApiVersionNumber();
    if (buildNumber == null) {
      buildNumber = BuildNumber.currentVersion().asString();
    }
    setIfEmpty("idea.plugins.compatible.build", buildNumber);
    setIfEmpty(PlatformUtils.PLATFORM_PREFIX_KEY, determinePlatformPrefix(buildNumber));

    // b/166052760: Early in the android studio initialization, it accesses the user's home
    // directory for retrieving some analytics settings, and to also set up an SDK from
    // ~/Android/sdk, both of which it shouldn't be doing during testing. To fix this, we reset home
    // directory to point to a temporary directory. (Blaze may be doing this in general, so this
    // is more useful for bazel).
    System.setProperty("user.home", new File(sandbox, "userhome").getAbsolutePath());

    // Tests fail if they access files outside of the project roots and other system directories.
    // When the class path contains jars which contain `kotlin` packages the `BuiltinsVirtualFileProviderBaseImpl`
    // also access jars from the class path. Therefore, this checks needs either to be disabled or
    // the specific jars need to be added to the `VfsRootAccess` allow list.
    System.setProperty("NO_FS_ROOTS_ACCESS_CHECK", "true");

    setIfEmpty("idea.force.use.core.classloader", "true");
  }

  @Nullable
  private static String determinePlatformPrefix(String buildNumber) {
    if (buildNumber.startsWith("AI")) { // Android Studio
      return "AndroidStudio";
    } else if (buildNumber.startsWith("IU")) { // IntelliJ Ultimate
      return null;
    } else if (buildNumber.startsWith("IC")) { // IntelliJ Community
      return "Idea";
    } else if (buildNumber.startsWith("CL")) { // CLion
      return "CLion";
    } else {
      throw new RuntimeException("Unable to determine platform prefix for build: " + buildNumber);
    }
  }

  @Nullable
  private static String readApiVersionNumber() {
    String apiVersionFilePath = System.getProperty("blaze.idea.api.version.file");
    String runfilesWorkspaceRoot = System.getProperty("user.dir");
    if (apiVersionFilePath == null) {
      throw new RuntimeException("No api_version_file found in runfiles directory");
    }
    if (runfilesWorkspaceRoot == null) {
      throw new RuntimeException("Runfiles workspace root not found");
    }
    File apiVersionFile = new File(runfilesWorkspaceRoot, apiVersionFilePath);
    if (!apiVersionFile.canRead()) {
      return null;
    }
    try {
      return Files.asCharSource(apiVersionFile, StandardCharsets.UTF_8).readFirstLine();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Nullable
  private static String getPlatformApiPath() {
    String platformJar = PathManager.getJarPathForClass(Application.class);
    if (platformJar == null) {
      return null;
    }
    File jarFile = new File(platformJar).getAbsoluteFile();
    File jarDir = jarFile.getParentFile();
    if (jarDir == null) {
      return null;
    }
    if (jarDir.getName().equals("lib")) {
      // Building against IDE distribution.
      // root/ <- we want this
      // |-lib/
      // | `-openapi.jar (jarFile)
      // `-plugins/
      return jarDir.getParent();
    } else if (jarDir.getName().equals("core-api")) {
      // Building against source.
      // tools/idea/ <- we want this
      // |-platform/
      // | `-core-api/
      // |   `-libcore-api.jar (jarFile)
      // `-plugins/
      File platformDir = jarDir.getParentFile();
      if (platformDir != null && platformDir.getName().equals("platform")) {
        return platformDir.getParent();
      }
    }
    return null;
  }

  private static void setSandboxPath(String property, File path) {
    path.mkdirs();
    setIfEmpty(property, path.getPath());
  }

  private static void setIfEmpty(String property, @Nullable String value) {
    if (value == null) {
      return;
    }
    if (System.getProperty(property) == null) {
      System.setProperty(property, value);
    }
  }
}
