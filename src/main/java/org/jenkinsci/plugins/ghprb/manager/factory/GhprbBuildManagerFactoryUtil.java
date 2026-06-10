package org.jenkinsci.plugins.ghprb.manager.factory;

import hudson.model.Run;
import org.jenkinsci.plugins.ghprb.manager.GhprbBuildManager;
import org.jenkinsci.plugins.ghprb.manager.configuration.JobConfiguration;
import org.jenkinsci.plugins.ghprb.manager.impl.GhprbDefaultBuildManager;

/**
 * @author mdelapenya (Manuel de la Peña)
 */
public final class GhprbBuildManagerFactoryUtil {

    private GhprbBuildManagerFactoryUtil() {
    }

    public static GhprbBuildManager getBuildManager(Run<?, ?> build) {
        return getBuildManager(build, JobConfiguration.builder().printStackTrace(false).build());
    }

    public static GhprbBuildManager getBuildManager(Run<?, ?> build, JobConfiguration jobConfiguration) {
        return new GhprbDefaultBuildManager(build);
    }

}
