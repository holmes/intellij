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
package com.google.idea.blaze.base.run.testmap;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.run.SourceToTargetFinder;
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.targetmaps.ReverseDependencyMap;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import java.io.File;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the test map */
@RunWith(JUnit4.class)
public class TestMapTest extends BlazeTestCase {

  private MockBlazeProjectDataManager mockBlazeProjectDataManager;

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);
    applicationServices.register(ExperimentService.class, new MockExperimentService());
    mockBlazeProjectDataManager = new MockBlazeProjectDataManager();
    projectServices.register(BlazeProjectDataManager.class, mockBlazeProjectDataManager);
    projectServices.register(SyncCache.class, new SyncCache(project));

    ExtensionPointImpl<SourceToTargetFinder> ep =
        registerExtensionPoint(SourceToTargetFinder.EP_NAME, SourceToTargetFinder.class);
    ep.registerExtension(new ProjectSourceToTargetFinder());
  }

  @Test
  public void testTrivialTestMap() throws Exception {
    mockBlazeProjectDataManager.targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:test")
                    .setKind("java_test")
                    .addSource(sourceRoot("test/Test.java")))
            .build();

    Collection<TargetInfo> targets =
        SourceToTargetFinder.findTargetsForSourceFile(
            project, new File("/test/Test.java"), Optional.of(RuleType.TEST));

    assertThat(targets.stream().map(t -> t.label).collect(Collectors.toList()))
        .containsExactly(Label.create("//test:test"));
  }

  @Test
  public void testOneStepRemovedTestMap() throws Exception {
    mockBlazeProjectDataManager.targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:test")
                    .setKind("java_test")
                    .addDependency("//test:lib"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:lib")
                    .setKind("java_library")
                    .addSource(sourceRoot("test/Test.java")))
            .build();

    Collection<TargetInfo> targets =
        SourceToTargetFinder.findTargetsForSourceFile(
            project, new File("/test/Test.java"), Optional.of(RuleType.TEST));

    assertThat(targets.stream().map(t -> t.label).collect(Collectors.toList()))
        .containsExactly(Label.create("//test:test"));
  }

  @Test
  public void testTwoCandidatesTestMap() throws Exception {
    mockBlazeProjectDataManager.targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:test")
                    .setKind("java_test")
                    .addDependency("//test:lib"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:test2")
                    .setKind("java_test")
                    .addDependency("//test:lib"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:lib")
                    .setKind("java_library")
                    .addSource(sourceRoot("test/Test.java")))
            .build();

    Collection<TargetInfo> targets =
        SourceToTargetFinder.findTargetsForSourceFile(
            project, new File("/test/Test.java"), Optional.of(RuleType.TEST));

    assertThat(targets.stream().map(t -> t.label).collect(Collectors.toList()))
        .containsExactly(Label.create("//test:test"), Label.create("//test:test2"));
  }

  @Test
  public void testBfsPreferred() throws Exception {
    mockBlazeProjectDataManager.targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:lib")
                    .setKind("java_library")
                    .addSource(sourceRoot("test/Test.java")))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:lib2")
                    .setKind("java_library")
                    .addDependency("//test:lib"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:test2")
                    .setKind("java_test")
                    .addDependency("//test:lib2"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:test")
                    .setKind("java_test")
                    .addDependency("//test:lib"))
            .build();

    Collection<TargetInfo> targets =
        SourceToTargetFinder.findTargetsForSourceFile(
            project, new File("/test/Test.java"), Optional.of(RuleType.TEST));

    assertThat(targets.stream().map(t -> t.label).collect(Collectors.toList()))
        .containsExactly(Label.create("//test:test"), Label.create("//test:test2"))
        .inOrder();
  }

  @Test
  public void testSourceIncludedMultipleTimesFindsAll() throws Exception {
    mockBlazeProjectDataManager.targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:test")
                    .setKind("java_test")
                    .addDependency("//test:lib"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:test2")
                    .setKind("java_test")
                    .addDependency("//test:lib2"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:lib")
                    .setKind("java_library")
                    .addSource(sourceRoot("test/Test.java")))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:lib2")
                    .setKind("java_library")
                    .addSource(sourceRoot("test/Test.java")))
            .build();

    Collection<TargetInfo> targets =
        SourceToTargetFinder.findTargetsForSourceFile(
            project, new File("/test/Test.java"), Optional.of(RuleType.TEST));

    assertThat(targets.stream().map(t -> t.label).collect(Collectors.toList()))
        .containsExactly(Label.create("//test:test"), Label.create("//test:test2"));
  }

  @Test
  public void testSourceIncludedMultipleTimesShouldOnlyGiveOneInstanceOfTest() throws Exception {
    mockBlazeProjectDataManager.targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:test")
                    .setKind("java_test")
                    .addDependency("//test:lib")
                    .addDependency("//test:lib2"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:lib")
                    .setKind("java_library")
                    .addSource(sourceRoot("test/Test.java")))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:lib2")
                    .setKind("java_library")
                    .addSource(sourceRoot("test/Test.java")))
            .build();

    Collection<TargetInfo> targets =
        SourceToTargetFinder.findTargetsForSourceFile(
            project, new File("/test/Test.java"), Optional.of(RuleType.TEST));

    assertThat(targets.stream().map(t -> t.label).collect(Collectors.toList()))
        .containsExactly(Label.create("//test:test"));
  }

  private ArtifactLocation sourceRoot(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }

  private static class MockBlazeProjectDataManager implements BlazeProjectDataManager {

    private TargetMap targetMap = new TargetMap(ImmutableMap.of());

    @Nullable
    @Override
    public BlazeProjectData getBlazeProjectData() {
      return MockBlazeProjectDataBuilder.builder(new WorkspaceRoot(new File("/")))
          .setTargetMap(targetMap)
          .setArtifactLocationDecoder(location -> new File("/", location.getRelativePath()))
          .setReverseDependencies(ReverseDependencyMap.createRdepsMap(targetMap))
          .build();
    }
  }
}
