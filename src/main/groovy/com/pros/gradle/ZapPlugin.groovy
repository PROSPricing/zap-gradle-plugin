/**
 * Copyright (c) 2013, PROS Inc. All right reserved.
 *
 * Released under BSD-3 style license.
 * See http://opensource.org/licenses/BSD-3-Clause
 */
package com.pros.gradle

import java.net.URL
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.tasks.Exec

class ZapPlugin implements Plugin<Project> {

    ZAPExecutor zap = null
    void apply(Project target) {
        target.extensions.create("zapConfig", ZapPluginExtension)
        zap = new ZAPExecutor(target)
        target.task('zapStart') << {
            zap.startZap()
        }
        target.tasks.zapStart.description = 'Starts the ZAP daemon. You must set the extension properties zap.jarPath and zap.proxyPort to the ZAP jar file location and the ZAP proxy port.'

        target.task('zapActiveScan', dependsOn: target.tasks.zapStart) << {
            zap.activeScan()
        }
        target.tasks.zapActiveScan.description = 'Runs the ZAP active scanner against zap.applicationUrl. It is recommended that this be done after any automated tests have completed so that the proxy is aware of those URLs.'

        target.task('zapReport', dependsOn: target.tasks.zapStart) << {
            zap.outputReport()
        }
        target.tasks.zapReport.description = 'Generates a report with the current ZAP alerts for applicationUrl at reportOutputPath with type remoteFormat (HTML, JSON, or XML)'

        target.task('zapStop', dependsOn: target.tasks.zapStart) << {
            zap.stopZap()
        }
        target.tasks.zapStop.description = 'Stops the ZAP server ONLY if it has been started during this gradle process. Otherwise does nothing'
    }
}

class ZAPExecutor {
    Process zapProc = null
    Project proj = null

    ZAPExecutor(Project project) {
        proj = project
    }

    /*
     * Starts the ZAP daemon. This will persist after the gradle run if stopZap is not called.
     */
    def startZap() {
        if (zapProc != null)
        {
            return;
        }

        def workingDir = proj.zapConfig.zapInstallDir
        def standardOutput = new ByteArrayOutputStream()
        def errorOutput = new ByteArrayOutputStream()
        Thread.start {
            ProcessBuilder builder = null
            if (Os.isFamily(Os.FAMILY_WINDOWS))
            {
                builder = new ProcessBuilder("cmd", "/c", "java -jar zap.jar -daemon -port ${proj.zapConfig.proxyPort.toInteger()}")
            }
            else
            {
                builder = new ProcessBuilder("/bin/bash", "-c", "java -jar zap.jar -daemon -port ${proj.zapConfig.proxyPort.toInteger()}")
            }

            builder.directory(new File(workingDir))
            zapProc = builder.start()
            zapProc.consumeProcessOutput(standardOutput, errorOutput)
        }
        sleep 5000 // Wait for ZAP to start.
    }

    /*
     * Grabs the alert report from the running ZAP instances.
     */
    def outputReport() {
        def format = proj.zapConfig.reportFormat
        def url = new URL("http://zap/${format}/core/view/alerts/?zapapiformat=${format}&baseurl=${proj.zapConfig.applicationUrl}")

        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", proj.zapConfig.proxyPort.toInteger()));
        def connection = url.openConnection(proxy)
        def response = connection.content.text

        File report = new File(proj.zapConfig.reportOutputPath)
        report.write(response)
    }

    /*
     * Executes a ZAP active scan against the applicationUrl. This task will wait until the scan is complete before returning.
     */
    def activeScan() {
        def format = proj.zapConfig.reportFormat
        def url = new URL("http://zap/${format}/ascan/action/scan/?zapapiformat=${format}&url=${proj.zapConfig.applicationUrl}&recurse=true&inScopeOnly=")

        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", proj.zapConfig.proxyPort.toInteger()));
        def connection = url.openConnection(proxy)
        def response = connection.content.text
        println "Starting Active Scan: " + response
        if (response.contains("url_not_found")) // ZAP doesn't do status codes other than 200.
        {
            throw new RuntimeException("ZAP has no known links at " + proj.zapConfig.applicationUrl)
        }

        checkStatusUntilScanComplete()
    }

    def checkStatusUntilScanComplete() {
        def responseText = "no responses yet"
        def responseCode = 200
        def maxRetries = 6 * proj.zapConfig.activeScanTimeout.toInteger() // 10 second wait times 6 for one minute times number of minutes.
        def retryNum = 0
        while (!responseText.contains("100") && responseCode == 200)
        {
            if (retryNum >= maxRetries)
            {
                throw new RuntimeException("ZAP Active Scanner has not completed after ${proj.zapConfig.activeScanTimeout} minutes. Exiting.")
            }
            def url = new URL("http://zap/JSON/ascan/view/status/?zapapiformat=JSON")

            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", proj.zapConfig.proxyPort.toInteger()));
            def connection = url.openConnection(proxy)
            responseText = connection.content.text
            responseCode = connection.responseCode
            retryNum += 1
            sleep 10000
        }
    }

    def stopZap() {
        if (zapProc != null)
        {
            // Kill the process after waiting 1ms. The Process API doesn't expose kill directly.
            zapProc.waitForOrKill(1)
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
}
