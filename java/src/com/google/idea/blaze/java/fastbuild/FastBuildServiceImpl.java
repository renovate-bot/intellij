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
package com.google.idea.blaze.java.fastbuild;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.buildresult.BuildResult;
import com.google.idea.blaze.base.command.buildresult.BuildResult.Status;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.GetArtifactsException;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelperProvider;
import com.google.idea.blaze.base.command.buildresult.BuildResultParser;
import com.google.idea.blaze.base.command.buildresult.LocalFileArtifact;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.command.info.BlazeInfoRunner;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ScopedTask;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.sync.aspects.storage.AspectStorageService;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.common.Interners;
import com.google.idea.blaze.java.AndroidBlazeRules;
import com.google.idea.blaze.java.JavaBlazeRules;
import com.google.idea.blaze.java.fastbuild.FastBuildChangedFilesService.ChangedSources;
import com.google.idea.blaze.java.fastbuild.FastBuildException.BlazeBuildError;
import com.google.idea.blaze.java.fastbuild.FastBuildLogDataScope.FastBuildLogOutput;
import com.google.idea.blaze.java.fastbuild.FastBuildState.BuildOutput;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

final class FastBuildServiceImpl implements FastBuildService, ProjectComponent {

  private static final ImmutableSetMultimap<BuildSystemName, Kind> SUPPORTED_KINDS =
      ImmutableSetMultimap.<BuildSystemName, Kind>builder()
          .putAll(
              BuildSystemName.Bazel,
              JavaBlazeRules.RuleTypes.JAVA_TEST.getKind(),
              JavaBlazeRules.RuleTypes.JAVA_JUNIT5_TEST.getKind())
          .putAll(
              BuildSystemName.Blaze,
              AndroidBlazeRules.RuleTypes.ANDROID_ROBOLECTRIC_TEST.getKind(),
              AndroidBlazeRules.RuleTypes.ANDROID_LOCAL_TEST.getKind(),
              AndroidBlazeRules.RuleTypes.KT_ANDROID_LOCAL_TEST.getKind(),
              JavaBlazeRules.RuleTypes.JAVA_TEST.getKind(),
              JavaBlazeRules.RuleTypes.JAVA_JUNIT5_TEST.getKind())
          .build();

  private final Project project;
  private final ProjectViewManager projectViewManager;
  private final FastBuildIncrementalCompiler incrementalCompiler;
  private final FastBuildChangedFilesService changedFilesManager;
  private final Thread shutdownHook;

  private final ConcurrentHashMap<Label, FastBuildState> builds;

  FastBuildServiceImpl(Project project) {
    this.project = project;
    this.projectViewManager = ProjectViewManager.getInstance(project);
    this.incrementalCompiler = FastBuildIncrementalCompiler.getInstance(project);
    this.changedFilesManager = FastBuildChangedFilesService.getInstance(project);
    this.builds = new ConcurrentHashMap<>();
    this.shutdownHook = new Thread(this::resetBuilds);
  }

  @Override
  public boolean supportsFastBuilds(BuildSystemName buildSystemName, Kind kind) {
    return SUPPORTED_KINDS.get(buildSystemName).contains(kind);
  }

  @Override
  public void resetBuild(Label label) {
    FastBuildState build = builds.remove(label);
    if (build != null) {
      FileUtil.delete(build.compilerOutputDirectory());
      changedFilesManager.resetBuild(label);
    }
  }

  @Override
  public Future<FastBuildInfo> createBuild(
      BlazeContext context, Label label, String blazeBinaryPath, List<String> blazeFlags)
      throws FastBuildException {

    try {
      FastBuildParameters buildParameters =
          generateBuildParameters(blazeBinaryPath, blazeFlags, context);
      FastBuildState buildState =
          builds.compute(
              label,
              (unused, buildInfo) -> updateBuild(context, label, buildParameters, buildInfo));
      return Futures.transform(
          buildState.newBuildOutput(),
          buildOutput ->
              FastBuildInfo.create(
                  label,
                  buildOutput.deployJar(),
                  ImmutableList.of(buildState.compilerOutputDirectory(), buildOutput.deployJar()),
                  buildOutput.blazeData(),
                  buildOutput.blazeInfo()),
          directExecutor());
    } catch (FastBuildTunnelException e) {
      throw e.asFastBuildException();
    }
  }

  private FastBuildParameters generateBuildParameters(
      String blazeBinaryPath, List<String> userBlazeFlags, BlazeContext context) {

    ProjectViewSet projectViewSet = projectViewManager.getProjectViewSet();
    BlazeInvocationContext invocationContext =
        BlazeInvocationContext.runConfigContext(
            ExecutorType.FAST_BUILD_RUN, BlazeCommandRunConfigurationType.getInstance(), true);
    List<String> buildFlags =
        BlazeFlags.blazeFlags(
            project, projectViewSet, BlazeCommandName.BUILD, context, invocationContext);
    List<String> infoFlags =
        BlazeFlags.blazeFlags(
            project, projectViewSet, BlazeCommandName.INFO, context, invocationContext);

    return FastBuildParameters.builder()
        .setBlazeBinary(blazeBinaryPath)
        // TODO(b/64714884): reenable this once one version enforcement is turned on for java_tests
        // Right now there's a discrepancy because enforcement is disabled for java_test rules, but
        // turns back on if you build a java_test_deploy.jar (as we do). So force it off for the
        // deploy jar too.
        .addBuildFlags(ImmutableList.of("--experimental_one_version_enforcement=off"))
        .addBuildFlags(buildFlags)
        .addBuildFlags(userBlazeFlags)
        .addInfoFlags(infoFlags)
        .build();
  }

  private FastBuildState updateBuild(
      BlazeContext context,
      Label label,
      FastBuildParameters buildParameters,
      @Nullable FastBuildState existingBuildState) {

    context.output(FastBuildLogOutput.keyValue("label", label.toString()));

    if (existingBuildState != null && !existingBuildState.newBuildOutput().isDone()) {
      // Don't start a new build if an existing one is still running.
      context.output(FastBuildLogOutput.keyValue("reused_existing_build_future", "true"));
      return existingBuildState;
    }

    BuildOutput completedBuildOutput = getCompletedBuild(existingBuildState);
    ChangedSources changedSources = ChangedSources.fullCompile();
    if (completedBuildOutput != null) {
      changedSources = changedFilesManager.getAndResetChangedSources(label);
    }

    if (changedSources.needsFullCompile()) {
      File compileDirectory = getCompilerOutputDirectory(existingBuildState);
      ListenableFuture<BuildOutput> newBuildOutput =
          buildDeployJarAsync(context, label, buildParameters);
      changedFilesManager.newBuild(label, newBuildOutput);
      return FastBuildState.create(newBuildOutput, compileDirectory, buildParameters);
    } else {
      existingBuildState = existingBuildState.withCompletedBuildOutput(completedBuildOutput);
      return performIncrementalCompilation(
          context, label, existingBuildState, changedSources.changedSources());
    }
  }

  private static File getCompilerOutputDirectory(@Nullable FastBuildState buildState) {
    if (buildState == null || !buildState.compilerOutputDirectory().exists()) {
      return createCompilerOutputDirectory();
    }
    return buildState.compilerOutputDirectory();
  }

  private static File createCompilerOutputDirectory() {
    try {
      return Files.createTempDirectory("ide-fastbuild-").toFile();
    } catch (IOException e) {
      throw new FastBuildTunnelException(e);
    }
  }

  @Nullable
  private static BuildOutput getCompletedBuild(@Nullable FastBuildState buildState) {
    if (buildState == null || !buildState.compilerOutputDirectory().exists()) {
      return null;
    }

    BuildOutput buildOutput;
    try {
      buildOutput = buildState.newBuildOutput().get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("InterruptedException caught when calling get() on a done future");
    } catch (CancellationException | ExecutionException e) {
      // Whoever initially launched this build will log the exception, so we don't need to worry
      // about it.
      buildOutput = buildState.completedBuildOutput().orElse(null);
    }

    return buildOutput != null && buildOutput.deployJar().exists() ? buildOutput : null;
  }

  private ListenableFuture<FastBuildState.BuildOutput> buildDeployJarAsync(
      BlazeContext context, Label label, FastBuildParameters buildParameters) {

    return ProgressiveTaskWithProgressIndicator.builder(
            project, "Building deploy jar for fast builds")
        .submitTaskWithResult(
            new ScopedTask<BuildOutput>(context) {
              @Override
              protected BuildOutput execute(BlazeContext context1) {
                // Explicitly depend on local build helper because the deploy jar is expected to
                // be available locally
                try (BuildResultHelper buildResultHelper =
                    BuildResultHelperProvider.createForLocalBuild(project)) {
                  return buildDeployJar(context1, label, buildParameters, buildResultHelper);
                }
              }
            });
  }

  private FastBuildState.BuildOutput buildDeployJar(
      BlazeContext context,
      Label label,
      FastBuildParameters buildParameters,
      BuildResultHelper resultHelper) {
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
    BlazeInfo blazeInfo = getBlazeInfo(context, buildParameters);

    BlazeVersionData blazeVersionData =
        BlazeVersionData.build(
            Blaze.getBuildSystemProvider(project).getBuildSystem(), workspaceRoot, blazeInfo);

    final var projectData = BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      throw new RuntimeException("not synced yet; please sync project");
    }

    try {
      AspectStorageService.of(project).prepare(context, projectData, blazeVersionData);
    } catch (SyncFailedException e) {
      throw new RuntimeException("could not prepare aspects", e);
    }

    FastBuildDeployJarStrategy deployJarStrategy =
        FastBuildDeployJarStrategy.getInstance(Blaze.getBuildSystemName(project));
    Label deployJarLabel = deployJarStrategy.createDeployJarLabel(label, blazeVersionData);
    context.output(
        new StatusOutput(
            "Building base deploy jar for fast builds: " + deployJarLabel.targetName()));

    FastBuildAspectStrategy aspectStrategy =
        FastBuildAspectStrategy.getInstance(Blaze.getBuildSystemName(project));

    Stopwatch timer = Stopwatch.createStarted();

    BlazeCommand.Builder command =
        BlazeCommand.builder(buildParameters.blazeBinary(), BlazeCommandName.BUILD, project)
            .addTargets(deployJarStrategy.getBuildTargets(label, blazeVersionData))
            .addBlazeFlags(deployJarStrategy.getBuildFlags(blazeVersionData))
            .addBlazeFlags(buildParameters.buildFlags())
            .addBlazeFlags(resultHelper.getBuildFlags());

    aspectStrategy.addAspectAndOutputGroups(
        command, blazeVersionData, project, /* additionalOutputGroups...= */ "default");

    int exitCode =
        ExternalTask.builder(workspaceRoot)
            .addBlazeCommand(command.build())
            .context(context)
            .stderr(
                LineProcessingOutputStream.of(
                    BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(context)))
            .build()
            .run();
    BuildResult result = BuildResult.fromExitCode(exitCode);
    context.output(
        FastBuildLogOutput.keyValue("deploy_jar_build_result", result.status.toString()));
    context.output(FastBuildLogOutput.milliseconds("deploy_jar_build_time_ms", timer));
    if (result.status != Status.SUCCESS) {
      throw new FastBuildTunnelException(new BlazeBuildError("Blaze failure building deploy jar"));
    }

    File deployJar;
    try (final var bepStream = resultHelper.getBepStream(Optional.empty())) {
      ImmutableList<File> deployJarArtifacts =
          LocalFileArtifact.getLocalFiles(
              com.google.idea.blaze.common.Label.of(label.toString()),
              BlazeBuildOutputs.fromParsedBepOutput(
                BuildResultParser.getBuildOutput(bepStream, Interners.STRING))
                  .getOutputGroupTargetArtifacts(
                      deployJarStrategy.deployJarOwnerLabel(label, blazeVersionData).toString(),
                      aspectStrategy.getAspectOutputGroup())
                  .stream()
                  .filter(artifact -> artifact.getArtifactPath().endsWith(deployJarLabel.targetName().toString()))
                  .collect(Collectors.toUnmodifiableSet()),
              BlazeContext.create(),
              project);
      checkState(deployJarArtifacts.size() == 1);
      deployJar = deployJarArtifacts.get(0);
    } catch (GetArtifactsException e) {
      throw new RuntimeException("Blaze failure building deploy jar: " + e.getMessage());
    }

    ImmutableList<File> ideInfoFiles;
    try (final var bepStream = resultHelper.getBepStream(Optional.empty())) {
      ideInfoFiles = LocalFileArtifact.getLocalFiles(
          com.google.idea.blaze.common.Label.of(label.toString()),
          BlazeBuildOutputs.fromParsedBepOutput(
            BuildResultParser.getBuildOutput(bepStream, Interners.STRING))
              .getOutputGroupArtifacts(
                  aspectStrategy.getAspectOutputGroup())
              .stream()
              .filter(artifact ->
                  aspectStrategy.getAspectOutputFilePredicate().test(
                      artifact.getArtifactPath().toString()
                  )
              ).collect(Collectors.toUnmodifiableSet()),
          BlazeContext.create(),
          project);
    } catch (GetArtifactsException e) {
      throw new RuntimeException("Blaze failure building ide info files: " + e.getMessage());
    }

    // if targets are built with multiple configurations, just take the first one
    // TODO(brendandouglas): choose a consistent configuration instead
    ImmutableMap<Label, FastBuildBlazeData> blazeData =
        ideInfoFiles.stream()
            .map(aspectStrategy::readFastBuildBlazeData)
            .collect(toImmutableMap(FastBuildBlazeData::label, i -> i, (i, j) -> i));
    return BuildOutput.create(deployJar, blazeData, blazeInfo);
  }

  private BlazeInfo getBlazeInfo(BlazeContext context, FastBuildParameters buildParameters) {
    BuildSystemName buildSystemName = Blaze.getBuildSystemName(project);
    ListenableFuture<BlazeInfo> blazeInfoFuture =
        BlazeInfoRunner.getInstance()
            .runBlazeInfo(
                project,
                Blaze.getBuildSystemProvider(project)
                    .getBuildSystem()
                    .getDefaultInvoker(project),
                context,
                buildSystemName,
                buildParameters.infoFlags());
    BlazeInfo info =
        FutureUtil.waitForFuture(context, blazeInfoFuture)
            .timed(buildSystemName.getName() + "Info", EventType.BlazeInvocation)
            .withProgressMessage(
                String.format("Running %s info...", buildSystemName.name().toLowerCase()))
            .onError(String.format("Could not run %s info", buildSystemName.name().toLowerCase()))
            .run()
            .result();
    if (info == null) {
      throw new RuntimeException(
          String.format("%s info failed", buildSystemName.name().toLowerCase()));
    }
    return info;
  }

  private FastBuildState performIncrementalCompilation(
      BlazeContext context,
      Label label,
      FastBuildState existingBuildState,
      Set<File> modifiedFiles) {

    ListenableFuture<BuildOutput> compilationResult =
        incrementalCompiler.compile(context, label, existingBuildState, modifiedFiles);
    Futures.addCallback(
        compilationResult,
        new FutureCallback<BuildOutput>() {
          @Override
          public void onSuccess(BuildOutput result) {}

          @Override
          public void onFailure(Throwable t) {
            // We want to unconditionally try to recompile these files, even if they haven't changed
            // again. It's sort of silly, but otherwise you get into this situation:
            // 1. Compile a successful build and run tests.
            // 2. Add a syntax error to a file
            // 3. Recompile; compilation fails.
            // 4. Run fast build again immediately. No files have changed, so compilation is
            //    skipped. The tests will run and pass, despite the syntax error.
            changedFilesManager.addFilesFromFailedCompilation(label, modifiedFiles);
          }
        },
        directExecutor());
    return existingBuildState.withNewBuildOutput(compilationResult);
  }

  private static class FastBuildTunnelException extends RuntimeException {

    FastBuildTunnelException(Throwable cause) {
      super(cause);
    }

    FastBuildException asFastBuildException() {
      Throwable cause = getCause();
      return cause instanceof FastBuildException
          ? (FastBuildException) cause
          : new FastBuildException(cause);
    }
  }

  @Override
  public void projectOpened() {
    Runtime.getRuntime().addShutdownHook(shutdownHook);
  }

  @Override
  public void projectClosed() {
    Runtime.getRuntime().removeShutdownHook(shutdownHook);
    resetBuilds();
  }

  private void resetBuilds() {
    builds.keySet().forEach(this::resetBuild);
  }
}
