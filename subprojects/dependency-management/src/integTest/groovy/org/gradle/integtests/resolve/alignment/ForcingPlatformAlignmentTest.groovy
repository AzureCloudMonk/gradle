/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.integtests.resolve.alignment

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.RequiredFeatures
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.publish.RemoteRepositorySpec
import org.gradle.test.fixtures.server.http.MavenHttpModule
import spock.lang.IgnoreIf
import spock.lang.Unroll

@IgnoreIf({
    // This test is very expensive due to the permutation testing.
    // Because it tests the internal state of the resolution engine, the Gradle execution model does not matter.
    // Se we run the tests only in embedded mode
    !GradleContextualExecuter.embedded
})
class ForcingPlatformAlignmentTest extends AbstractAlignmentSpec {

    def "can force a virtual platform version by forcing one of its leaves"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each {
                path "databind:$it -> core:$it"
                path "databind:$it -> annotations:$it"
                path "kotlin:$it -> core:$it"
                path "kotlin:$it -> annotations:$it"
            }
        }

        given:
        buildFile << """
            dependencies {
                conf("org:core:2.9.4")
                conf("org:databind:2.7.9") {
                  force = true
                }
                conf("org:kotlin:2.9.4.1")        
            }
        """

        and:
        "a rule which infers module set from group and version"()

        when:
        expectAlignment {
            module('core') tries('2.9.4', '2.9.4.1') alignsTo('2.7.9') byVirtualPlatform()
            module('databind') alignsTo('2.7.9') byVirtualPlatform()
            module('kotlin') tries('2.9.4.1') alignsTo('2.7.9') byVirtualPlatform()
            module('annotations') tries('2.9.4.1') alignsTo('2.7.9') byVirtualPlatform()
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:core:2.9.4", "org:core:2.7.9") {
                    forced()
                }
                module("org:databind:2.7.9") {
                    module('org:annotations:2.7.9')
                    module('org:core:2.7.9')
                }
                edge("org:kotlin:2.9.4.1", "org:kotlin:2.7.9") {
                    forced()
                    module('org:core:2.7.9')
                    module('org:annotations:2.7.9')
                }
            }
        }
    }

    def "can force a virtual platform version by forcing one of its leaves through resolutionStrategy.force"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each {
                path "databind:$it -> core:$it"
                path "databind:$it -> annotations:$it"
                path "kotlin:$it -> core:$it"
                path "kotlin:$it -> annotations:$it"
            }
        }

        given:
        buildFile << """
            dependencies {
                conf("org:core:2.9.4")
                conf("org:databind:2.7.9")
                conf("org:kotlin:2.9.4.1")        
            }
            
            configurations {
                conf.resolutionStrategy.force("org:databind:2.7.9")
            }
        """

        and:
        "a rule which infers module set from group and version"()

        when:
        expectAlignment {
            module('core') tries('2.9.4', '2.9.4.1') alignsTo('2.7.9') byVirtualPlatform()
            module('databind') alignsTo('2.7.9') byVirtualPlatform()
            module('kotlin') tries('2.9.4.1') alignsTo('2.7.9') byVirtualPlatform()
            module('annotations') tries('2.9.4.1') alignsTo('2.7.9') byVirtualPlatform()
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:core:2.9.4", "org:core:2.7.9") {
                    forced()
                }
                module("org:databind:2.7.9") {
                    module('org:annotations:2.7.9')
                    module('org:core:2.7.9')
                }
                edge("org:kotlin:2.9.4.1", "org:kotlin:2.7.9") {
                    forced()
                    module('org:core:2.7.9')
                    module('org:annotations:2.7.9')
                }
            }
        }
    }

    def "can force a virtual platform version by forcing one of its leaves through resolutionStrategy.substitution"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each {
                path "databind:$it -> core:$it"
                path "databind:$it -> annotations:$it"
                path "kotlin:$it -> core:$it"
                path "kotlin:$it -> annotations:$it"
            }
        }

        given:
        buildFile << """
            dependencies {
                conf("org:core:2.9.4")
                conf("org:databind:2.7.9")
                conf("org:kotlin:2.9.4.1")        
            }
            
            configurations {
                conf.resolutionStrategy.dependencySubstitution {
                    substitute module("org:databind") with module("org:databind:2.7.9")
                }
            }
        """

        and:
        "a rule which infers module set from group and version"()

        when:
        expectAlignment {
            module('core') tries('2.9.4', '2.9.4.1') alignsTo('2.7.9') byVirtualPlatform()
            module('databind') alignsTo('2.7.9') byVirtualPlatform()
            module('kotlin') tries('2.9.4.1') alignsTo('2.7.9') byVirtualPlatform()
            module('annotations') tries('2.9.4.1') alignsTo('2.7.9') byVirtualPlatform()
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:core:2.9.4", "org:core:2.7.9") {
                    forced()
                }
                module("org:databind:2.7.9") {
                    module('org:annotations:2.7.9')
                    module('org:core:2.7.9')
                }
                edge("org:kotlin:2.9.4.1", "org:kotlin:2.7.9") {
                    forced()
                    module('org:core:2.7.9')
                    module('org:annotations:2.7.9')
                }
            }
        }
    }

    @Unroll
    def "fails if forcing a virtual platform version by forcing multiple leaves with different versions"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each {
                path "databind:$it -> core:$it"
                path "databind:$it -> annotations:$it"
                path "kotlin:$it -> core:$it"
                path "kotlin:$it -> annotations:$it"
            }
        }

        given:
        buildFile << """
            dependencies {
                conf("$dep1") {
                    force = true
                }
                conf("org:kotlin:2.9.4.1")

                conf("$dep2") {
                    force = true
                }
            }
        """

        and:
        "a rule which infers module set from group and version"()

        when:
        allowAllRepositoryInteractions()
        fails ':checkDeps'

        then:
        failureCauseContains("Multiple forces on different versions for virtual platform org:platform")

        where:
        dep1                 | dep2
        'org:core:2.9.4'     | 'org:databind:2.7.9'
        'org:databind:2.7.9' | 'org:core:2.9.4'
    }

    def "fails if forcing a virtual platform version by forcing multiple leaves with different versions through resolutionStrategy"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each {
                path "databind:$it -> core:$it"
                path "databind:$it -> annotations:$it"
                path "kotlin:$it -> core:$it"
                path "kotlin:$it -> annotations:$it"
            }
        }

        given:
        buildFile << """
            configurations {
                conf.resolutionStrategy {
                    force('org:core:2.9.4')
                    force('org:databind:2.7.9')
                }
            }
            dependencies {
                conf("org:core:2.9.4.1")
                conf("org:kotlin:2.9.4.1")

                conf("org:databind:2.9.4.1")
            }
        """

        and:
        "a rule which infers module set from group and version"()

        when:
        allowAllRepositoryInteractions()
        fails ':checkDeps'

        then:
        failureCauseContains("Multiple forces on different versions for virtual platform org:platform")
    }

    def "fails if forcing a virtual platform version by forcing multiple leaves with different versions through resolutionStrategy.dependencySubstitution"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each {
                path "databind:$it -> core:$it"
                path "databind:$it -> annotations:$it"
                path "kotlin:$it -> core:$it"
                path "kotlin:$it -> annotations:$it"
            }
        }

        given:
        buildFile << """
            configurations {
                conf.resolutionStrategy.dependencySubstitution {
                    substitute module('org:core') with module('org:core:2.9.4')
                    substitute module('org:databind') with module('org:databind:2.7.9')
                }
            }
            dependencies {
                conf("org:core:2.9.4.1")
                conf("org:kotlin:2.9.4.1")

                conf("org:databind:2.9.4.1")
            }
        """

        and:
        "a rule which infers module set from group and version"()

        when:
        allowAllRepositoryInteractions()
        fails ':checkDeps'

        then:
        failureCauseContains("Multiple forces on different versions for virtual platform org:platform")
    }

    def "fails if forcing a virtual platform version and forcing a leaf with different version"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each {
                path "databind:$it -> core:$it"
                path "databind:$it -> annotations:$it"
                path "kotlin:$it -> core:$it"
                path "kotlin:$it -> annotations:$it"
            }
        }

        given:
        buildFile << """
            dependencies {
                conf("org:core:2.9.4")
                
                conf enforcedPlatform("org:platform:2.9.4")
                
                conf("org:kotlin:2.9.4.1")

                conf("org:databind:2.7.9") {
                    force = true
                }
            }
        """

        and:
        "a rule which infers module set from group and version"()

        when:
        allowAllRepositoryInteractions()
        fails ':checkDeps'

        then:
        failureCauseContains("Multiple forces on different versions for virtual platform org:platform")
    }

    def "fails if forcing a virtual platform version and forcing a leaf with different version through resolutionStrategy"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each {
                path "databind:$it -> core:$it"
                path "databind:$it -> annotations:$it"
                path "kotlin:$it -> core:$it"
                path "kotlin:$it -> annotations:$it"
            }
        }

        given:
        buildFile << """
            configurations {
                conf.resolutionStrategy {
                    force('org:databind:2.7.9')
                }
            }
            dependencies {
                conf("org:core:2.9.4")
                
                conf enforcedPlatform("org:platform:2.9.4")
                
                conf("org:kotlin:2.9.4.1")

                conf("org:databind:2.9.4")
            }
        """

        and:
        "a rule which infers module set from group and version"()

        when:
        allowAllRepositoryInteractions()
        fails ':checkDeps'

        then:
        failureCauseContains("Multiple forces on different versions for virtual platform org:platform")
    }

    def "fails if forcing a virtual platform version by forcing multiple leaves with different versions, including transitively"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each {
                path "databind:$it -> core:$it"
                path "databind:$it -> annotations:$it"
                path "kotlin:$it -> core:$it"
                path "kotlin:$it -> annotations:$it"
            }
        }

        given:
        settingsFile << """
include 'other'
"""
        buildFile << """
            dependencies {
                conf project(path: ':other', configuration: 'conf')
                conf("org:kotlin:2.9.4.1")

                conf("org:databind:2.7.9") {
                    force = true
                }
            }
            
            project(':other') {
                configurations {
                    conf
                }
                dependencies {
                    conf("org:core:2.9.4") {
                        force = true
                    }
                    components.all(InferModuleSetFromGroupAndVersion)
                }
            }
        """

        and:
        "a rule which infers module set from group and version"()

        when:
        allowAllRepositoryInteractions()
        fails ':checkDeps'

        then:
        failureCauseContains("Multiple forces on different versions for virtual platform org:platform")
    }

    def "succeeds if forcing a virtual platform version by forcing multiple leaves with same version"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each {
                path "databind:$it -> core:$it"
                path "databind:$it -> annotations:$it"
                path "kotlin:$it -> core:$it"
                path "kotlin:$it -> annotations:$it"
            }
        }

        given:
        buildFile << """
            dependencies {
                conf("org:core:2.7.9") {
                    force = true
                }
                conf("org:kotlin:2.9.4.1")

                conf("org:databind:2.7.9") {
                    force = true
                }
            }
        """

        and:
        "a rule which infers module set from group and version"()

        expect:
        allowAllRepositoryInteractions()
        succeeds ':checkDeps'
    }

    def "succeeds if forcing a virtual platform version by forcing multiple leaves with same version through resolutionStrategy"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each {
                path "databind:$it -> core:$it"
                path "databind:$it -> annotations:$it"
                path "kotlin:$it -> core:$it"
                path "kotlin:$it -> annotations:$it"
            }
        }

        given:
        buildFile << """
            configurations {
                conf.resolutionStrategy {
                    force('org:core:2.7.9')
                    force('org:databind:2.7.9')
                }
            }
            dependencies {
                conf("org:core:2.9.4")
                conf("org:kotlin:2.9.4.1")

                conf("org:databind:2.7.9")
            }
        """

        and:
        "a rule which infers module set from group and version"()

        expect:
        allowAllRepositoryInteractions()
        succeeds ':checkDeps'
    }

    def "succeeds if forcing a virtual platform version and forcing a leaf with same version"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each {
                path "databind:$it -> core:$it"
                path "databind:$it -> annotations:$it"
                path "kotlin:$it -> core:$it"
                path "kotlin:$it -> annotations:$it"
            }
        }

        given:
        buildFile << """
            dependencies {
                conf("org:core:2.9.4")
                
                conf enforcedPlatform("org:platform:2.7.9")
                
                conf("org:kotlin:2.9.4.1")

                conf("org:databind:2.7.9") {
                    force = true
                }
            }
        """

        and:
        "a rule which infers module set from group and version"()

        expect:
        allowAllRepositoryInteractions()
        succeeds ':checkDeps'
    }

    def "succeeds if forcing a virtual platform version and forcing a leaf with same version through resolutionStrategy"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each {
                path "databind:$it -> core:$it"
                path "databind:$it -> annotations:$it"
                path "kotlin:$it -> core:$it"
                path "kotlin:$it -> annotations:$it"
            }
        }

        given:
        buildFile << """
            configurations {
                conf.resolutionStrategy {
                    force('org:databind:2.7.9')
                }
            }
            dependencies {
                conf("org:core:2.9.4")
                
                conf enforcedPlatform("org:platform:2.7.9")
                
                conf("org:kotlin:2.9.4.1")

                conf("org:databind:2.9.4")
            }
        """

        and:
        "a rule which infers module set from group and version"()

        expect:
        allowAllRepositoryInteractions()
        succeeds ':checkDeps'
    }

    @RequiredFeatures([
        @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven"),
   ])
    def 'forced platform turning selector state to force after being selected and deselected'() {
        repository {
            ['1.0', '2.0'].each {
                path "webapp:$it -> xml:$it"
                path "org:other:$it"
            }
            'root:root:1.0' {
                dependsOn('org:webapp:1.0')
                dependsOn('org:other:1.0')
            }
        }
        def bomDep = mavenHttpRepo.module('org', 'xml', '2.0')
        mavenHttpRepo.module('bom', 'bom', '1.0').dependencyConstraint(bomDep).hasPackaging('pom').publish()

        given:
        buildFile << """
            configurations {
                conf.resolutionStrategy.force 'org:other:1.0'
            }
            dependencies {
                conf platform("bom:bom:1.0")
                conf "root:root:1.0"
            }
        """

        and:
        "align the 'org' group only"()

        when:
        repositoryInteractions {
            group('org') {
                ['webapp', 'xml', 'other'].each { mod ->
                    module(mod) {
                        ['1.0', '2.0'].each { v ->
                            version(v) {
                                allowAll()
                            }
                        }
                    }
                }
            }
            'bom:bom:1.0' {
                allowAll()
            }
            'root:root:1.0' {
                allowAll()
            }
        }

        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("bom:bom:1.0") {
                    configuration = 'platform-runtime'
                    constraint("org:xml:2.0", "org:xml:1.0")
                }
                module("root:root:1.0") {
                    module('org:webapp:1.0') {
                        module('org:xml:1.0')
                    }
                    module('org:other:1.0') {
                        forced()
                    }
                }
            }
        }


    }

    @Unroll
    def "can force a virtual platform version by forcing the platform itself via a dependency"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each {
                path "databind:$it -> core:$it"
                path "databind:$it -> annotations:$it"
                path "kotlin:$it -> core:$it"
                path "kotlin:$it -> annotations:$it"
            }
        }

        given:
        buildFile << """
            dependencies {
                $dependencies
            }
        """

        and:
        "a rule which infers module set from group and version"()

        when:
        allowAllRepositoryInteractions()
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:core:2.9.4", "org:core:2.7.9") {
                    forced()
                }
                module("org:databind:2.7.9") {
                    module('org:annotations:2.7.9')
                    module('org:core:2.7.9')
                }
                edge("org:kotlin:2.9.4.1", "org:kotlin:2.7.9") {
                    forced()
                    module('org:core:2.7.9')
                    module('org:annotations:2.7.9')
                }
                module('org:platform:2.7.9:default') {
                    noArtifacts()
                    constraint('org:core:2.7.9')
                    constraint('org:databind:2.7.9')
                    constraint('org:annotations:2.7.9')
                    constraint('org:kotlin:2.7.9')
                }
            }
        }

        where: "order of dependencies doesn't matter"
        dependencies << [
            'conf("org:core:2.9.4")',
            'conf("org:databind:2.7.9")',
            'conf("org:kotlin:2.9.4.1")',
            'conf enforcedPlatform("org:platform:2.7.9")'
        ].permutations()*.join("\n")
    }

    @Unroll("can force a virtual platform version by forcing the platform itself via a constraint")
    def "can force a virtual platform version by forcing the platform itself via a constraint"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each {
                path "databind:$it -> core:$it"
                path "databind:$it -> annotations:$it"
                path "kotlin:$it -> core:$it"
                path "kotlin:$it -> annotations:$it"
            }
        }

        given:
        buildFile << """
            dependencies {
                $dependencies
            }
        """

        and:
        "a rule which infers module set from group and version"()

        when:
        allowAllRepositoryInteractions()
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:core:2.9.4", "org:core:2.7.9") {
                    forced()
                }
                module("org:databind:2.7.9") {
                    module('org:annotations:2.7.9')
                    module('org:core:2.7.9')
                }
                edge("org:kotlin:2.9.4.1", "org:kotlin:2.7.9") {
                    forced()
                    module('org:core:2.7.9')
                    module('org:annotations:2.7.9')
                }
                constraint('org:platform:2.7.9', 'org:platform:2.7.9') {
                    noArtifacts()
                    byConstraint("belongs to platform org:platform:2.7.9")
                    forced()
                    constraint('org:core:2.7.9')
                    constraint('org:databind:2.7.9')
                    constraint('org:annotations:2.7.9')
                    constraint('org:kotlin:2.7.9')
                }
            }
            virtualConfiguration('org:platform:2.7.9')
        }

        where: "order of dependencies doesn't matter"
        dependencies << [
            'conf("org:core:2.9.4")',
            'conf("org:databind:2.7.9")',
            'conf("org:kotlin:2.9.4.1")',
            'constraints { conf enforcedPlatform("org:platform:2.7.9") }'
        ].permutations()*.join("\n")
    }


    @Unroll("can force a published platform version by forcing the platform itself via a dependency")
    @RequiredFeatures([
        @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven"),
        @RequiredFeature(feature = GradleMetadataResolveRunner.EXPERIMENTAL_RESOLVE_BEHAVIOR, value = "true")
    ])
    def "can force a published platform version by forcing the platform itself via a dependency"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each { v ->
                path "databind:$v -> core:$v"
                path "databind:$v -> annotations:$v"
                path "kotlin:$v -> core:$v"
                path "kotlin:$v -> annotations:$v"

                platform("org", "platform", v, [
                    "org:core:$v",
                    "org:databind:$v",
                    "org:kotlin:$v",
                    "org:annotations:$v",
                ])
            }
        }

        given:
        buildFile << """
            dependencies {
                $dependencies
            }
        """

        and:
        "a rule which infers module set from group and version"()

        when:
        allowAllRepositoryInteractions()
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:core:2.9.4", "org:core:2.7.9") {
                    forced()
                }
                module("org:databind:2.7.9") {
                    module('org:annotations:2.7.9')
                    module('org:core:2.7.9')
                }
                edge("org:kotlin:2.9.4.1", "org:kotlin:2.7.9") {
                    forced()
                    module('org:core:2.7.9')
                    module('org:annotations:2.7.9')
                }
                String expectedVariant = GradleMetadataResolveRunner.isGradleMetadataEnabled()?'enforced-platform':'enforced-platform-runtime'
                module("org:platform:2.7.9:$expectedVariant") {
                    constraint('org:core:2.7.9')
                    constraint('org:databind:2.7.9')
                    constraint('org:annotations:2.7.9')
                    constraint('org:kotlin:2.7.9')
                }
            }
        }

        where: "order of dependencies doesn't matter"
        dependencies << [
            'conf("org:core:2.9.4")',
            'conf("org:databind:2.7.9")',
            'conf("org:kotlin:2.9.4.1")',
            'conf enforcedPlatform("org:platform:2.7.9")',
        ].permutations()*.join("\n")
    }

    @RequiredFeatures([
        @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven"),
        @RequiredFeature(feature = GradleMetadataResolveRunner.EXPERIMENTAL_RESOLVE_BEHAVIOR, value = "true")
    ])
    @Unroll("can force a published platform version by forcing the platform itself via a constraint")
    def "can force a published platform version by forcing the platform itself via a constraint"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each { v ->
                path "databind:$v -> core:$v"
                path "databind:$v -> annotations:$v"
                path "kotlin:$v -> core:$v"
                path "kotlin:$v -> annotations:$v"

                platform("org", "platform", v, [
                    "org:core:$v",
                    "org:databind:$v",
                    "org:kotlin:$v",
                    "org:annotations:$v",
                ])
            }
        }

        given:
        buildFile << """
            dependencies {
                $dependencies
            }
        """

        and:
        "a rule which infers module set from group and version"()

        when:
        allowAllRepositoryInteractions()
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:core:2.9.4", "org:core:2.7.9") {
                    forced()
                }
                module("org:databind:2.7.9") {
                    module('org:annotations:2.7.9')
                    module('org:core:2.7.9')
                }
                edge("org:kotlin:2.9.4.1", "org:kotlin:2.7.9") {
                    forced()
                    module('org:core:2.7.9')
                    module('org:annotations:2.7.9')
                }
                constraint('org:platform:2.7.9', 'org:platform:2.7.9') {
                    noArtifacts()
                    byConstraint("belongs to platform org:platform:2.7.9")
                    forced()
                    constraint('org:core:2.7.9')
                    constraint('org:databind:2.7.9')
                    constraint('org:annotations:2.7.9')
                    constraint('org:kotlin:2.7.9')
                }
            }
            virtualConfiguration('org:platform:2.7.9')
        }

        where: "order of dependencies doesn't matter"
        dependencies << [
            'conf("org:core:2.9.4")',
            'conf("org:databind:2.7.9")',
            'conf("org:kotlin:2.9.4.1")',
            'constraints { conf enforcedPlatform("org:platform:2.7.9") }',
        ].permutations()*.join("\n")
    }

    def setup() {
        repoSpec.metaClass.platform = this.&platform.curry(repoSpec)
    }

    /**
     * Generates a BOM, or Gradle metadata
     * @param repo
     * @param platformGroup
     * @param platformName
     * @param platformVersion
     * @param members
     */
    void platform(RemoteRepositorySpec repo, String platformGroup, String platformName, String platformVersion, List<String> members) {
        ['platform', 'enforced-platform'].each { kind ->
            repo.group(platformGroup) {
                module(platformName) {
                    version(platformVersion) {
                        variant(kind) {
                            attribute('org.gradle.component.category', kind)
                            members.each { member ->
                                constraint(member)
                            }
                        }
                        // this is used only in BOMs
                        members.each { member ->
                            constraint(member)
                        }

                        withModule(MavenHttpModule) {
                            // make it a BOM
                            hasPackaging('pom')
                        }
                    }
                }
            }
        }
    }

    void allowAllRepositoryInteractions() {
        repositoryInteractions {
            group('org') {
                ['core', 'databind', 'annotations', 'kotlin', 'platform'].each { mod ->
                    module(mod) {
                        ['2.7.9', '2.9.4', '2.9.4.1'].each { v ->
                            version(v) {
                                // Not interested in the actual interactions, especially with
                                // the complexity introduced by permutation testing
                                allowAll()
                            }
                        }
                    }
                }

            }
        }
    }

}
