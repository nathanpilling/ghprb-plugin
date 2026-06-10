package org.jenkinsci.plugins.ghprb.manager.factory;

import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import org.jenkinsci.plugins.ghprb.manager.GhprbBuildManager;
import org.jenkinsci.plugins.ghprb.manager.impl.GhprbDefaultBuildManager;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author mdelapenya (Manuel de la Peña)
 */
public class GhprbBuildManagerFactoryUtilTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void shouldReturnDefaultManager() throws Exception {
        // GIVEN
        MatrixProject project = jenkinsRule.getInstance().createProject(MatrixProject.class, "PRJ");

        GhprbBuildManager buildManager = GhprbBuildManagerFactoryUtil.getBuildManager(new MatrixBuild(project));

        // THEN
        assertThat(buildManager).isInstanceOf(GhprbDefaultBuildManager.class);
    }
}
