package de.dfki.mary.voicebuilding

import de.dfki.mary.voicebuilding.tasks.*

import org.gradle.api.Plugin
import org.gradle.api.Project

class VoicebuildingDataPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.apply VoicebuildingBasePlugin

        project.configurations {
            create 'data'
            create 'marytts'
        }

        project.sourceSets {
            create 'data'
            create 'marytts'
        }

        project.dependencies {
            maryttsCompile localGroovy()
            project.afterEvaluate {
                maryttsCompile group: 'de.dfki.mary', name: "marytts-lang-$project.voice.locale.language", version: project.maryttsVersion
            }
        }

        project.task('wav', type: AudioConverterTask) {
            dependsOn project.processDataResources
            srcDir = project.file("$project.sourceSets.data.output.resourcesDir")
            destDir = project.file("$project.buildDir/wav")
        }

        project.generateSource {
            def maryttsGroovySrcDir = project.file("$destDir/marytts")
            project.sourceSets.marytts.groovy.srcDir maryttsGroovySrcDir
            ext.srcFileNames = ['BatchProcessor.groovy']
            doLast {
                srcFileNames.each { srcFileName ->
                    def destFile = project.file("$maryttsGroovySrcDir/$srcFileName")
                    destFile.parentFile.mkdirs()
                    destFile.withOutputStream { stream ->
                        stream << getClass().getResourceAsStream("/marytts/$srcFileName")
                    }
                }
            }
            project.compileMaryttsJava.dependsOn it
        }

        project.task('generateAllophones', type: MaryInterfaceBatchTask) {
            inputs.files project.maryttsClasses
            srcDir = project.file("$project.buildDir/text")
            destDir = project.file("$project.buildDir/prompt_allophones")
            inputType = 'TEXT'
            inputExt = 'txt'
            outputType = 'ALLOPHONES'
            outputExt = 'xml'
        }

        project.task('generatePhoneFeatures', type: MaryInterfaceBatchTask) {
            dependsOn project.generateAllophones
            srcDir = project.file("$project.buildDir/prompt_allophones")
            destDir = project.file("$project.buildDir/phonefeatures")
            inputType = 'ALLOPHONES'
            inputExt = 'xml'
            outputType = 'TARGETFEATURES'
            outputExt = 'pfeats'
        }

        project.task('generateHalfPhoneFeatures', type: MaryInterfaceBatchTask) {
            dependsOn project.generateAllophones
            srcDir = project.file("$project.buildDir/prompt_allophones")
            destDir = project.file("$project.buildDir/halfphonefeatures")
            inputType = 'ALLOPHONES'
            inputExt = 'xml'
            outputType = 'HALFPHONE_TARGETFEATURES'
            outputExt = 'hpfeats'
        }

        project.task('praatPitchmarker', type: PraatPitchmarkerTask) {
            dependsOn project.wav
            srcDir = project.file("$project.buildDir/wav")
            destDir = project.file("$project.buildDir/pm")
        }

        project.task('mcepMaker', type: MCEPMakerTask) {
            dependsOn project.praatPitchmarker
            srcDir = project.file("$project.buildDir/wav")
            pmDir = project.file("$project.buildDir/pm")
            destDir = project.file("$project.buildDir/mcep")
        }
    }
}
