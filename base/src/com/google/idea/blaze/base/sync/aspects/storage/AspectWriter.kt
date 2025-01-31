/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.aspects.storage

import com.google.idea.blaze.base.sync.SyncScope
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import java.nio.file.Path

/**
 * Extension point for writing aspect files to the workspace.
 */
interface AspectWriter {

  companion object {
    val EP_NAME = ExtensionPointName.Companion.create<AspectWriter>("com.google.idea.blaze.AspectWriter");
  }

  /**
   * Name of the aspects to copy, used for debugging and logging.
   */
  fun name(): String

  /**
   * Write all aspect files to the destination directory.
   * Files are resolved relative to the destination directory.
   */
  @Throws(SyncScope.SyncFailedException::class)
  fun write(dst: Path, project: Project)
}