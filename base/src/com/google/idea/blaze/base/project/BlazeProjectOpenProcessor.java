/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.project;

import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessor;
import icons.BlazeIcons;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.swing.Icon;
import java.nio.file.Paths;

/** Allows directly opening a project with project data directory embedded within the project. */
public class BlazeProjectOpenProcessor extends ProjectOpenProcessor {
  @Override
  public @NotNull String getName() {
    return "Bazel";
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return BlazeIcons.Logo;
  }

  private static final String DEPRECATED_PROJECT_DATA_SUBDIRECTORY = ".project";

  @Nullable
  public static VirtualFile getIdeaSubdirectory(VirtualFile file) {
    VirtualFile projectSubdirectory = file.findChild(BlazeDataStorage.PROJECT_DATA_SUBDIRECTORY);
    if (projectSubdirectory == null || !projectSubdirectory.isDirectory()) {
      projectSubdirectory = file.findChild(DEPRECATED_PROJECT_DATA_SUBDIRECTORY);
      if (projectSubdirectory == null || !projectSubdirectory.isDirectory()) {
        return null;
      }
    }
    VirtualFile ideaSubdirectory = projectSubdirectory.findChild(Project.DIRECTORY_STORE_FOLDER);
    VirtualFile blazeSubdirectory =
        projectSubdirectory.findChild(BlazeDataStorage.BLAZE_DATA_SUBDIRECTORY);
    return ideaSubdirectory != null
            && ideaSubdirectory.isDirectory()
            && blazeSubdirectory != null
            && blazeSubdirectory.isDirectory()
        ? ideaSubdirectory
        : null;
  }

  @Override
  public boolean canOpenProject(VirtualFile file) {
    return getIdeaSubdirectory(file) != null;
  }

  @Override
  public boolean isStrongProjectInfoHolder() {
    return Registry.is("bazel.project.auto.open.if.present");
  }

  @Override
  public boolean lookForProjectsInDirectory() {
    return false;
  }

  @Nullable
  @Override
  public Project doOpenProject(
      VirtualFile file, @Nullable Project projectToClose, boolean forceOpenInNewFrame) {
    ProjectManager pm = ProjectManager.getInstance();
    if (projectToClose != null) {
      pm.closeProject(projectToClose);
    }

    VirtualFile ideaSubdirectory = getIdeaSubdirectory(file);
    if (ideaSubdirectory == null) {
      return null;
    }
    VirtualFile projectSubdirectory = ideaSubdirectory.getParent();
    OpenProjectTask options = OpenProjectTask.build().withForceOpenInNewFrame(forceOpenInNewFrame).withProjectToClose(projectToClose);
    return ProjectUtil.openProject(Paths.get(projectSubdirectory.getPath()),options);
  }
}
