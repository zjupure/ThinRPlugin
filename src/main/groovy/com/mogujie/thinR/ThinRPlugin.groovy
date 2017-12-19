package com.mogujie.thinR

import com.android.builder.Version
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Created by dolphinWang on 15/11/02.
 */
public class ThinRPlugin implements Plugin<Project> {


    ThinRExtension extension


    @Override
    void apply(Project project) {

        if (!project.plugins.hasPlugin("com.android.application")) {
            throw new GradleException("thinR plugin can be only apply to app module")
        }

        project.extensions.create("thinR", ThinRExtension)

        if(Utils.compareVersion(Version.ANDROID_GRADLE_PLUGIN_VERSION, "3.0.0") >= 0) {
            project.android.registerTransform(new ThinRTransform())
        }

        project.afterEvaluate {

            extension = project.extensions.findByName("thinR") as ThinRExtension
            PrintUtil.logLevel = extension.logLevel
            PrintUtil.info(extension.toString())

            project.android.applicationVariants.all { variant ->
                ContextProvider contextProvider = new ContextProvider(project, variant.name.capitalize() as String)
                boolean skipThinR = contextProvider.isSkipThinR(extension)
                PrintUtil.info("skipThinR: " + skipThinR)
                if (!skipThinR) {
                    doWhenDexFirst(project, contextProvider)
                }
            }
        }

    }

    void doWhenDexFirst(Project project, ContextProvider contextProvider) {
        String intermediatesPath = Utils.joinPath(project.buildDir.absolutePath, "intermediates")
        contextProvider.dexTask.doFirst {
            PrintUtil.info("${it.name}.doFirst with task hook method")
            long time1 = System.currentTimeMillis()
            ThinRProcessor thinRProcessor = new ThinRProcessor(contextProvider.getRClassDir(), contextProvider.getPackageName())
            Collection<File> inputFile = contextProvider.getDexInputFile(new ContextProvider.Filter() {
                @Override
                boolean accept(String path) {
                    return path.startsWith(intermediatesPath)
                }
            })
            inputFile.each { file ->
                PrintUtil.info("start process input : " + file)
                if (file.isDirectory()) {
                    thinRProcessor.processDir(file)
                } else if (file.name.endsWith(".jar")) {
                    thinRProcessor.processJar(file)
                } else if (file.name.endsWith(".class")){
                    thinRProcessor.processClass(file)
                } else {
                    PrintUtil.info("other input file ${file}")
                }
            }

            long time2 = System.currentTimeMillis()
            PrintUtil.info("${it.name} process time: " + (time2 - time1))
        }
    }


}

