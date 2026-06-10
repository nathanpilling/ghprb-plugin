package org.jenkinsci.plugins.ghprb;

import hudson.model.FreeStyleProject;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link GhprbJobLinter}.
 */
public class GhprbJobLinterTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    private FreeStyleProject job;
    private GhprbJobLinter linter;

    @Before
    public void setUp() throws Exception {
        job = jenkinsRule.createFreeStyleProject("test-job");
    }

    @Test
    public void testLinterWithNoGitSCM() {
        linter = new GhprbJobLinter(job);
        linter.lint();

        // With no SCM configured, linter should detect errors or warnings
        assertTrue("Linter should have results", linter.hasErrors() || linter.hasWarnings());
    }

    @Test
    public void testLinterWithoutGitHubProjectUrl() {
        // Create linter without GitHub project URL
        linter = new GhprbJobLinter(job);
        linter.lint();

        // Should detect missing GitHub URL
        assertTrue("Should have errors", linter.hasErrors() || linter.hasWarnings());
        assertTrue("Should detect issue with URL or SCM",
                linter.getErrors().stream().anyMatch(e -> e.contains("GitHub") || e.contains("SCM"))
                || linter.getWarnings().stream().anyMatch(w -> w.contains("GitHub") || w.contains("SCM")));
    }

    @Test
    public void testLinterWithNullJob() {
        linter = new GhprbJobLinter(null);
        linter.lint();

        assertTrue("Should have errors for null job", linter.hasErrors());
    }

    @Test
    public void testFormValidationWithErrors() throws Exception {
        // Create linter with missing config
        linter = new GhprbJobLinter(job);
        linter.lint();

        // Should produce FormValidation
        assertNotNull("FormValidation should not be null", linter.toFormValidation());
    }

    @Test
    public void testToStringRepresentation() throws Exception {
        linter = new GhprbJobLinter(job);
        linter.lint();

        String result = linter.toString();
        assertNotNull("toString should not be null", result);
        assertTrue("toString should contain some content", result.length() > 0);
    }

    @Test
    public void testTriggerLintJob() throws Exception {
        GhprbTrigger trigger = GhprbTestUtil.getTrigger();
        
        // The trigger should have a lintJob method
        GhprbJobLinter linter = trigger.lintJob();
        assertNotNull("Linter should not be null", linter);
        // Since trigger has no job set, linter will have errors
        assertTrue("Should detect missing job/configuration", linter.hasErrors() || linter.hasWarnings());
    }
}
