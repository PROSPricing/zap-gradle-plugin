package com.pros.gradle;

import static org.junit.Assert.*

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.Test

public class ZapPluginTest {

    Project project

    @Before
    public void setup() {
        project = ProjectBuilder.builder().build()
    }

    @Test
    public void testApplyPluginByClass() {
        project.apply plugin: ZapPlugin
        verifyPlugin()
    }

    @Test
    public void testApplyPluginByName() {
        project.apply plugin: 'zap'
        verifyPlugin()
    }


    private verifyPlugin() {
        assertTrue(project.tasks.zapStart instanceof ZapStart)
        assertTrue(project.tasks.zapStart.finalizedBy.values.contains('zapStop'))
        assertTrue(project.tasks.zapActiveScan instanceof ZapActiveScan)
        assertTrue(project.tasks.zapActiveScan.dependsOn.contains(project.tasks.zapStart))
        assertTrue(project.tasks.zapActiveScan.finalizedBy.values.contains(project.tasks.zapStop))
        assertTrue(project.tasks.zapReport instanceof ZapReport)
        assertTrue(project.tasks.zapReport.dependsOn.contains(project.tasks.zapStart))
        assertTrue(project.tasks.zapReport.finalizedBy.values.contains(project.tasks.zapStop))
        assertTrue(project.tasks.zapStop instanceof ZapStop)
        assertTrue(project.tasks.zapStop.mustRunAfter.values.contains(project.tasks.zapStart))
        assertTrue(project.tasks.zapStop.mustRunAfter.values.contains('zapActiveScan'))
        assertTrue(project.tasks.zapStop.mustRunAfter.values.contains('zapReport'))
        assertTrue(project.zapConfig instanceof ZapPluginExtension)
    }

}
