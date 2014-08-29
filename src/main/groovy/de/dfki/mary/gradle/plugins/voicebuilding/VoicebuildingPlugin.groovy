package de.dfki.mary.gradle.plugins.voicebuilding

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.tasks.Copy

class VoicebuildingPlugin implements Plugin<Project> {
    final templateDir = "/de/dfki/mary/gradle/plugins/voicebuilding/templates"

    @Override
    void apply(Project project) {
        project.plugins.apply(JavaPlugin)
        project.plugins.apply(MavenPlugin)

        project.repositories {
            jcenter()
            maven {
                url project.maryttsRepositoryUrl
            }
        }

        project.sourceCompatibility = 1.7

        project.ext {
            voiceNameCamelCase = project.voiceName.split(/[^_A-Za-z0-9]/).collect { it.capitalize() }.join()
            generatedSrcDir = "$project.buildDir/generated-src"
            generatedTestSrcDir = "$project.buildDir/generated-test-src"
            voiceRegion = project.hasProperty('voiceRegion') ? voiceRegion : voiceLanguage.toUpperCase()
            voiceLocale = "${voiceLanguage}_$voiceRegion"
            voiceLocaleXml = "$voiceLanguage-$voiceRegion"
        }

        project.sourceSets {
            main {
                java {
                    srcDir project.ext.generatedSrcDir
                }
            }
            test {
                java {
                    srcDir project.ext.generatedTestSrcDir
                }
            }
        }

        project.jar {
            manifest {
                attributes('Created-By': "${System.properties['java.version']} (${System.properties['java.vendor']})",
                        'Built-By': System.properties['user.name'],
                        'Built-With': "gradle-${project.gradle.gradleVersion}, groovy-${GroovySystem.version}")
            }
        }

        addTasks(project)
    }

    private void addTasks(Project project) {
        project.task('generateSource', type: Copy) {
            from project.file(getClass().getResource("$templateDir/Config.java"))
            into project.generatedSrcDir
            expand project.properties
            rename { "marytts/voice/$project.voiceNameCamelCase/$it" }
        }
        project.tasks['compileJava'].dependsOn 'generateSource'

        project.task('generateTestSource', type: Copy) {
            from project.file(getClass().getResource("$templateDir/ConfigTest.java"))
            from project.file(getClass().getResource("$templateDir/LoadVoiceIT.java"))
            into project.generatedTestSrcDir
            expand project.properties
            rename { "marytts/voice/$project.voiceNameCamelCase/$it" }
        }
        project.tasks['compileTestJava'].dependsOn 'generateTestSource'

        project.processResources.doLast {
            // generate voice config
            project.copy {
                from project.file(getClass().getResource("$templateDir/voice${project.voiceType == "hsmm" ? "-hsmm" : ""}.config"))
                into "$destinationDir/marytts/voice/$project.voiceNameCamelCase"
                expand project.properties
            }
            // generate service loader
            project.copy {
                from project.file(getClass().getResource("$templateDir/marytts.config.MaryConfig"))
                into "$destinationDir/META-INF/services"
                expand project.properties
            }
        }

        project.task('generatePom') {
            def groupAsPathString = project.group.replace('.', '/')
            def pomDir = project.file("${project.tasks['processResources'].destinationDir}/META-INF/maven/$groupAsPathString/$project.name")
            def pomFile = project.file("$pomDir/pom.xml")
            def propFile = project.file("$pomDir/pom.properties")
            outputs.files project.files(pomFile, propFile)
            doLast {
                pomDir.mkdirs()
                project.pom { pom ->
                    pom.project {
                        description project.voiceDescription
                        licenses {
                            license {
                                name project.licenseName
                                url project.licenseUrl
                            }
                        }
                    }
                }.writeTo(pomFile)
                propFile.withWriter { dest ->
                    dest.println "version=$project.version"
                    dest.println "groupId=$project.group"
                    dest.println "artifactId=$project.name"
                }
            }
        }
        project.tasks['jar'].dependsOn 'generatePom'
    }
}
