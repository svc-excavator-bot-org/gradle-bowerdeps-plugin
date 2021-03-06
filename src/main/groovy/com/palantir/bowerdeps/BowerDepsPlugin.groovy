/*
 * Copyright 2015 Palantir Technologies, Inc.
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

package com.palantir.bowerdeps

import groovy.json.JsonSlurper
import org.gradle.api.Plugin
import org.gradle.api.Project

class BowerDepsPlugin implements Plugin<Project> {
    static final String BOWER_MANIFEST_NAME = 'bower.json'

    static final String BOWERDEPS_CONFIGURATION_NAME = 'bowerdeps'

    void apply(Project project) {
        project.plugins.apply('java-base')

        def bowerDepsExtension = project.extensions.create(BowerDepsExtension.NAME, BowerDepsExtension, project)
        def bowerDepsConfig = project.configurations.create(BOWERDEPS_CONFIGURATION_NAME)

        project.afterEvaluate {
            def manifest = getManifest(bowerDepsExtension.workDir)
            if (manifest == null) {
                return
            }

            if (bowerDepsExtension.buildTask == null) {
                if (project.plugins.hasPlugin('com.moowork.grunt')) {
                    bowerDepsExtension.buildTask = 'grunt_default'
                } else if (project.plugins.hasPlugin('com.moowork.gulp')) {
                    bowerDepsExtension.buildTask = 'gulp_default'
                }
            }

            if (bowerDepsExtension.buildTask != null) {
                getManifestDependencies(manifest, bowerDepsExtension.workDir).each { key ->
                    project.rootProject.allprojects.each { proj ->
                        if (proj.path =~ ":${key}\$") {
                            project.dependencies { deps ->
                                deps.add(BOWERDEPS_CONFIGURATION_NAME, deps.project([
                                    path: proj.path,
                                    configuration: BOWERDEPS_CONFIGURATION_NAME
                                ]))
                            }
                        }
                    }
                }

                project.tasks.getByName(bowerDepsExtension.buildTask).dependsOn(bowerDepsConfig.buildDependencies)
                project.tasks.getByName(bowerDepsExtension.buildTask).inputs.files(bowerDepsConfig)

                getManifestArtifacts(manifest).each { artifact ->
                    project.artifacts.add(BOWERDEPS_CONFIGURATION_NAME, [
                        file: new File(bowerDepsExtension.workDir, artifact),
                        builtBy: project.tasks.getByName(bowerDepsExtension.buildTask)
                    ])
                }
            }
        }
    }

    Object getManifest(File workDir) {
        File bowerManifest = new File(workDir, BOWER_MANIFEST_NAME)
        if (bowerManifest.exists()) {
            return (new JsonSlurper()).parse(bowerManifest)
        }
    }

    List<String> getManifestArtifacts(Object manifest) {
        List<String> artifacts = []

        if (manifest.main instanceof String) {
            artifacts.add(manifest.main)
        } else {
            manifest.main.each {
                artifacts.addAll(it)
            }
        }

        if (manifest.devMain instanceof String) {
            artifacts.add(manifest.devMain)
        } else {
            manifest.devMain.each {
                artifacts.addAll(it)
            }
        }

        return artifacts
    }

    List<String> getManifestDependencies(Object manifest, File workDir) {
        List<String> dependencies = []

        manifest.dependencies.each { key, value ->
            def f = new File(workDir, value)
            if (f.exists()) {
                dependencies.add(key)
            }
        }

        manifest.devDependencies.each { key, value ->
            def f = new File(workDir, value)
            if (f.exists()) {
                dependencies.add(key)
            }
        }

        return dependencies
    }
}
