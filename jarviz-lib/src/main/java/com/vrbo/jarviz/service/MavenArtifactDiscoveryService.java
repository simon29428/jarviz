/*
 * Copyright 2020 Expedia, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vrbo.jarviz.service;

import com.google.common.collect.ImmutableList;
import com.vrbo.jarviz.config.JarvizConfig;
import com.vrbo.jarviz.model.Artifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import static com.vrbo.jarviz.util.FileReadWriteUtils.toFullPath;

public class MavenArtifactDiscoveryService implements ArtifactDiscoveryService {

    private final Logger log = LoggerFactory.getLogger(MavenArtifactDiscoveryService.class);

    private final String localRepoPath;

    private final boolean continueOnMavenError;

    private final long mavenTimeOutSeconds;

    @Inject
    public MavenArtifactDiscoveryService(final JarvizConfig config) {
        this.localRepoPath = config.getArtifactDirectory();
        this.continueOnMavenError = config.getContinueOnMavenError();
        this.mavenTimeOutSeconds = config.getMavenTimeOutSeconds();
    }

    @Override
    public Path discoverArtifact(final Artifact artifact) throws ArtifactNotFoundException {
        final Path file = Paths.get(toFullPath(localRepoPath, artifact.toFileName()));
        if (!Files.exists(file)) {
            runMavenCopy(artifact);
        }

        return file;
    }

    private Process runMavenCopy(final Artifact artifact) throws ArtifactNotFoundException {
        try {
            final String artifactMavenId = artifact.toMavenId();
            log.info("Maven: fetching artifact {}", artifactMavenId);

            final String stripVersionSwitch = artifact.isVersionLatestOrRelease() ? "true" : "false";
            final String os = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
            final String mvnName = os.contains("win") ? "mvn.cmd" : "mvn";
            final String mvnCommand = String.format("%s dependency:copy -DoutputDirectory=%s -Dartifact=%s -Dmdep.stripVersion=%s", mvnName,
                localRepoPath, artifactMavenId, stripVersionSwitch);
            final Process process = Runtime.getRuntime().exec(mvnCommand);
            boolean failed = false;

            ForkJoinPool.commonPool().submit(() -> {
                try {
                    drainInputStream(process.getInputStream()).forEach(s -> log.info("{}", s));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            if (process.waitFor(mavenTimeOutSeconds, TimeUnit.SECONDS)) {
                if (process.exitValue() != 0) {
                    failed = true;
                    // In case, there is an error, let's log and throw exception
                    drainInputStream(process.getErrorStream()).forEach(s -> log.error("{}", s));
                    log.error("Maven command failed: {}", mvnCommand);
                }
            } else {
                failed = true;
                log.error("Maven command failed to execute in {} seconds: {}\n Consider increasing mavenTimeOutSeconds config value.",
                    mavenTimeOutSeconds, mvnCommand);
            }

            if (failed && !continueOnMavenError) {
                throw new ArtifactNotFoundException(
                    String.format("Unable to fetch the artifact %s from Maven repository", artifactMavenId));
            }

            return process;
        } catch (Exception e) {
            throw new ArtifactNotFoundException(e);
        }
    }

    private static List<String> drainInputStream(final InputStream stream) throws IOException {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        final ImmutableList.Builder<String> logLines = ImmutableList.builder();
        String s;
        while ((s = reader.readLine()) != null) {
            logLines.add(s);
        }
        return logLines.build();
    }
}
