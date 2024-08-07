/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.cpp;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.cpp.CompilerVersionChecker.VersionCheckException.IssueKind;

import java.io.ByteArrayOutputStream;
import java.io.File;

/** Runs a compiler to check its version. */
public class CompilerVersionCheckerImpl implements CompilerVersionChecker {

  @Override
  public String checkCompilerVersion(File executionRoot, File cppExecutable,
      ImmutableMap<String, String> checkerEnv)
  throws VersionCheckException {
    if (!executionRoot.exists()) {
      throw new VersionCheckException(IssueKind.MISSING_EXEC_ROOT, "");
    }
    if (!cppExecutable.exists()) {
      throw new VersionCheckException(IssueKind.MISSING_COMPILER, "");
    }
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream errStream = new ByteArrayOutputStream();
    int result =
        ExternalTask.builder(executionRoot)
            .args(cppExecutable.toString())
            .args("--version")
            .environmentVars(checkerEnv)
            .stdout(outputStream)
            .stderr(errStream)
            .build()
            .run();

    final var out = outputStream.toString();
    final var err = errStream.toString();

    if (result == 0) {
      return out;
    }

    // MSVC does not know the --version flag and will fail. However, the error message does contain
    // the compiler version.
    if (err.contains("Microsoft")) {
      return getMSVCVersion(err);
    }

    throw new VersionCheckException(
        IssueKind.GENERIC_FAILURE,
        String.format("stderr: \"%s\"\nstdout: \"%s\"", err, out));
  }

  private String getMSVCVersion(String err) {
    final var endOfLine = err.indexOf('\r');
    if (endOfLine < 0) {
      return err;
    }

    return err.substring(0, endOfLine);
  }
}
