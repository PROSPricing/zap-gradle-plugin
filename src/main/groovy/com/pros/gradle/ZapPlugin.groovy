/**
 * Copyright (c) 2013, PROS Inc. All right reserved.
 *
 * Released under BSD-3 style license.
 * See http://opensource.org/licenses/BSD-3-Clause
 */
package com.pros.gradle

import java.net.URL

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.DefaultTask;
import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.TaskAction;

class ZapPlugin implements Plugin<Project> {

    void apply(Project target) {
        target.extensions.create("zapConfig", ZapPluginExtension)

        target.getTasks().create('zapStart', ZapStart.class) {
            description = 'Starts the ZAP daemon. You must set the extension properties zap.jarPath and zap.proxyPort to the ZAP jar file location and the ZAP proxy port.'
            finalizedBy 'zapStop'
        }

        target.getTasks().create('zapStop', ZapStop) {
            description = 'Stops the ZAP server ONLY if it has been started during this gradle process. Otherwise does nothing'
            mustRunAfter project.tasks.zapStart
            mustRunAfter 'zapActiveScan'
            mustRunAfter 'zapReport'
        }

        target.getTasks().create('zapActiveScan', ZapActiveScan.class) {
            description = 'Runs the ZAP active scanner against zap.applicationUrl. It is recommended that this be done after any automated tests have completed so that the proxy is aware of those URLs.'
            dependsOn project.tasks.zapStart
            finalizedBy project.tasks.zapStop
        }

        target.getTasks().create('zapReport', ZapReport.class) {
            description = 'Generates a report with the current ZAP alerts for applicationUrl at reportOutputPath with type remoteFormat (HTML, JSON, or XML)'
            dependsOn project.tasks.zapStart
            finalizedBy project.tasks.zapStop
        }
    }
}

class ZapPluginExtension {
    def String zapInstallDir = ""
    def String proxyPort = "54300"
    def String reportFormat = "JSON"
    def String reportOutputPath = "zapReport"
    def String applicationUrl = ""
    def String activeScanTimeout = "30"
    protected Process zapProc = null
}

/**
 * Starts the ZAP daemon. This will persist after the gradle run if stopZap is not called.
 */
class ZapStart extends DefaultTask {
    @TaskAction
    def startZap() {
        if (project.zapConfig.zapProc != null)
        {
            return;
        }

        def workingDir = project.zapConfig.zapInstallDir
        def standardOutput = new ByteArrayOutputStream()
        def errorOutput = new ByteArrayOutputStream()
        Thread.start {
            ProcessBuilder builder = null
            if (Os.isFamily(Os.FAMILY_WINDOWS))
            {
                builder = new ProcessBuilder("java","-jar", "zap.jar", "-daemon", "-port", "${project.zapConfig.proxyPort.toInteger()}")
            }
            else
            {
                builder = new ProcessBuilder("/bin/bash", "-c", "java -jar zap.jar -daemon -port ${project.zapConfig.proxyPort.toInteger()}")
            }

            builder.directory(new File(workingDir))
            project.zapConfig.zapProc = builder.start()
            project.zapConfig.zapProc.consumeProcessOutput(standardOutput, errorOutput)
        }
        sleep 5000 // Wait for ZAP to start.
    }
}

/**
 * Grabs the alert report from the running ZAP instances.
 */
class ZapReport extends DefaultTask {
    @TaskAction
    def outputReport() {
        def format = project.zapConfig.reportFormat
        def url = new URL("http://zap/${format}/core/view/alerts/?zapapiformat=${format}&baseurl=${project.zapConfig.applicationUrl}")

        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", project.zapConfig.proxyPort.toInteger()));
        def connection = url.openConnection(proxy)
        def response = connection.content.text

        File report = new File(project.zapConfig.reportOutputPath)
        report.write(response)
    }
}

/**
 * Executes a ZAP active scan against the applicationUrl. This task will wait until the scan is complete before returning.
 */
class ZapActiveScan extends DefaultTask {
    @TaskAction
    def activeScan() {
        def format = project.zapConfig.reportFormat
        def url = new URL("http://zap/${format}/ascan/action/scan/?zapapiformat=${format}&url=${project.zapConfig.applicationUrl}&recurse=true&inScopeOnly=")

        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", project.zapConfig.proxyPort.toInteger()));
        def connection = url.openConnection(proxy)
        def response = connection.content.text
        println "Starting Active Scan: " + response
        if (response.contains("url_not_found")) // ZAP doesn't do status codes other than 200.
        {
            throw new RuntimeException("ZAP has no known links at " + project.zapConfig.applicationUrl)
        }

        checkStatusUntilScanComplete()
    }

    def checkStatusUntilScanComplete() {
        def responseText = "no responses yet"
        def responseCode = 200
        def maxRetries = 6 * project.zapConfig.activeScanTimeout.toInteger() // 10 second wait times 6 for one minute times number of minutes.
        def retryNum = 0
        while (!responseText.contains("100") && responseCode == 200)
        {
            if (retryNum >= maxRetries)
            {
                throw new RuntimeException("ZAP Active Scanner has not completed after ${project.zapConfig.activeScanTimeout} minutes. Exiting.")
            }
            def url = new URL("http://zap/JSON/ascan/view/status/?zapapiformat=JSON")

            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", project.zapConfig.proxyPort.toInteger()));
            def connection = url.openConnection(proxy)
            responseText = connection.content.text
            responseCode = connection.responseCode
            retryNum += 1
            sleep 10000
        }
    }
}

class ZapStop extends DefaultTask {
    @TaskAction
    def stopZap() {
        if (project.zapConfig.zapProc != null)
        {
            // Kill the process after waiting 1ms. The Process API doesn't expose kill directly.
            project.zapConfig.zapProc.waitForOrKill(1)
        }
    }
}

