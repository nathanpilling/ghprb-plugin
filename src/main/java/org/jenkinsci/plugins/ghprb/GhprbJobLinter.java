package org.jenkinsci.plugins.ghprb;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.coravy.hudson.plugins.github.GithubProjectProperty;
import hudson.model.Job;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Lints job configuration to ensure it's properly set up for the GitHub Pull Request Builder plugin.
 * 
 * @author GitHub PR Builder Plugin Contributors
 */
public class GhprbJobLinter {
    private static final Logger LOGGER = Logger.getLogger(GhprbJobLinter.class.getName());

    private final Job<?, ?> job;
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public GhprbJobLinter(Job<?, ?> job) {
        this.job = job;
    }

    /**
     * Performs all linting checks on the job configuration.
     */
    public void lint() {
        if (job == null) {
            errors.add("Job is null");
            return;
        }

        checkGitHubProjectUrl();
        checkGitSCM();
        checkBranchSpec();
        checkGitCredential();
        checkGitConfiguration();
        checkTriggerConfig();
    }

    /**
     * Validates that a GitHub project URL is configured and well-formed.
     */
    private void checkGitHubProjectUrl() {
        GithubProjectProperty githubProperty = job.getProperty(GithubProjectProperty.class);
        if (githubProperty == null || githubProperty.getProjectUrl() == null) {
            errors.add("GitHub project URL is not configured. Add a GitHub project URL in the job configuration.");
            return;
        }
        String url = githubProperty.getProjectUrl().toString();
        if (url.endsWith(".git") || url.endsWith(".git/")) {
            errors.add("GitHub project URL ends with '.git'. Remove the .git suffix (e.g. https://github.com/org/repo, not https://github.com/org/repo.git).");
        } else if (url.endsWith("/")) {
            warnings.add("GitHub project URL has a trailing slash. Consider removing it.");
        }
    }

    /**
     * Validates that Git SCM is configured.
     */
    private void checkGitSCM() {
        SCM scm = getSCM();
        if (scm == null) {
            errors.add("No SCM configured. Git SCM must be selected as the Source Code Management.");
            return;
        }

        if (!(scm instanceof GitSCM)) {
            errors.add("SCM is not Git. The GitHub Pull Request Builder requires Git SCM to be configured.");
        }
    }

    /**
     * Validates Git refspec and branch specifier configuration.
     */
    private void checkBranchSpec() {
        GitSCM gitScm = getGitSCM();
        if (gitScm == null) {
            return;
        }

        // Check for refspec configuration
        if (gitScm.getUserRemoteConfigs().isEmpty()) {
            errors.add("No Git remote URLs configured. Add a Repository URL in the Git SCM configuration.");
            return;
        }

        // Check refspec
        String refspec = gitScm.getUserRemoteConfigs().get(0).getRefspec();
        if (StringUtils.isEmpty(refspec)) {
            warnings.add("Git refspec is not configured. Consider setting refspec to include pull request branches. "
                    + "Recommended: +refs/pull/${ghprbPullId}/*:refs/remotes/origin/pr/${ghprbPullId}/*");
        } else if (!refspec.contains("${ghprbPullId}") && !refspec.contains("refs/pull")) {
            warnings.add("Git refspec does not appear to reference pull request branches. "
                    + "Recommended refspec: +refs/pull/${ghprbPullId}/*:refs/remotes/origin/pr/${ghprbPullId}/*");
        }

        // Check branch specifier
        if (gitScm.getBranches().isEmpty()) {
            errors.add("No branch specifiers configured. Set Branch Specifier to ${ghprbActualCommit} or ${sha1}.");
        } else {
            String branchName = gitScm.getBranches().get(0).getName();
            if (!branchName.contains("${ghprbActualCommit}") && !branchName.contains("${sha1}")) {
                errors.add("Branch specifier does not use ghprb variables. "
                        + "Set Branch Specifier to ${ghprbActualCommit} or ${sha1}.");
            }
        }
    }

    /**
     * Validates Git-specific configuration options.
     */
    private void checkGitConfiguration() {
        GitSCM gitScm = getGitSCM();
        if (gitScm == null) {
            return;
        }

        // Warn about shallow clones if applicable
        if (gitScm.getExtensions() != null && !gitScm.getExtensions().isEmpty()) {
            boolean hasCloneExtension = gitScm.getExtensions().stream()
                    .anyMatch(ext -> ext.getClass().getSimpleName().contains("CloneOption"));
            if (hasCloneExtension) {
                LOGGER.log(java.util.logging.Level.FINE, "Job {0} uses CloneOption extension", 
                        job != null ? job.getFullName() : "unknown");
            }
        }
    }

    /**
     * Validates that the Git credential type matches the remote URL scheme.
     */
    private void checkGitCredential() {
        GitSCM gitScm = getGitSCM();
        if (gitScm == null || gitScm.getUserRemoteConfigs().isEmpty()) {
            return;
        }
        UserRemoteConfig remote = gitScm.getUserRemoteConfigs().get(0);
        String remoteUrl = remote.getUrl();
        String credentialsId = remote.getCredentialsId();

        boolean isSshUrl = remoteUrl != null &&
                (remoteUrl.startsWith("git@") || remoteUrl.startsWith("ssh://"));

        if (isSshUrl) {
            if (StringUtils.isEmpty(credentialsId)) {
                errors.add("Git remote URL uses SSH but no credential is selected. "
                        + "Add an SSH Username with private key credential and select it in the Git SCM configuration.");
                return;
            }
            StandardCredentials cred = Ghprb.lookupCredentials(job, credentialsId, remoteUrl);
            if (cred != null && cred instanceof UsernamePasswordCredentials) {
                errors.add("Git remote URL uses SSH (git@...) but the selected credential is Username/Password. "
                        + "Create an SSH Username with private key credential instead.");
            }
        }
    }

    /**
     * Validates the GHPRB trigger configuration on the job.
     */
    private void checkTriggerConfig() {
        GhprbTrigger trigger = Ghprb.extractTrigger(job);
        if (trigger == null) {
            errors.add("GitHub Pull Request Builder trigger is not enabled on this job.");
            return;
        }

        // Check admin list
        String adminlist = trigger.getAdminlist();
        if (StringUtils.isEmpty(adminlist) || adminlist.trim().isEmpty()) {
            warnings.add("Admin list is empty. No users are whitelisted as admins. "
                    + "Add at least one GitHub username to the Admin list, or enable 'Permit all'.");
        }

        // Check GitHub API credentials
        String gitHubAuthId = trigger.getGitHubAuthId();
        if (StringUtils.isEmpty(gitHubAuthId)) {
            errors.add("GitHub API credentials are not configured. "
                    + "Select a GitHub API credential in the trigger configuration "
                    + "(configure it first in Manage Jenkins → Configure System → GitHub Pull Request Builder).");
        } else {
            GhprbGitHubAuth auth = trigger.getDescriptor().getGitHubAuth(gitHubAuthId);
            if (auth != null && StringUtils.isEmpty(auth.getCredentialsId())) {
                warnings.add("GitHub API is using an anonymous connection. "
                        + "API rate limits will apply and private repositories will not be accessible. "
                        + "Configure a personal access token credential.");
            }
        }
    }

    /**
     * Gets the SCM configuration from the job.
     */
    private SCM getSCM() {
        // For AbstractProject types (FreeStyleProject, MatrixProject, etc.)
        if (job instanceof hudson.model.AbstractProject) {
            return ((hudson.model.AbstractProject) job).getScm();
        }
        // For other types, try to access via reflection as fallback
        try {
            java.lang.reflect.Method getScmMethod = job.getClass().getMethod("getScm");
            return (SCM) getScmMethod.invoke(job);
        } catch (Exception e) {
            LOGGER.log(java.util.logging.Level.FINE, 
                    "Unable to determine SCM for job type: " + job.getClass().getName(), e);
        }
        return null;
    }

    /**
     * Gets the Git SCM configuration from the job, or null if not Git SCM.
     */
    private GitSCM getGitSCM() {
        SCM scm = getSCM();
        if (scm instanceof GitSCM) {
            return (GitSCM) scm;
        }
        return null;
    }

    /**
     * Returns a list of validation errors found during linting.
     */
    public List<String> getErrors() {
        return errors;
    }

    /**
     * Returns a list of validation warnings found during linting.
     */
    public List<String> getWarnings() {
        return warnings;
    }

    /**
     * Checks if there are any errors.
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Checks if there are any warnings.
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /**
     * Converts linting results to a FormValidation object.
     */
    public FormValidation toFormValidation() {
        if (hasErrors()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Job configuration issues detected:\n");
            for (String error : errors) {
                sb.append("• ").append(error).append("\n");
            }
            if (hasWarnings()) {
                sb.append("\nWarnings:\n");
                for (String warning : warnings) {
                    sb.append("• ").append(warning).append("\n");
                }
            }
            return FormValidation.error(sb.toString());
        }

        if (hasWarnings()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Job configuration recommendations:\n");
            for (String warning : warnings) {
                sb.append("• ").append(warning).append("\n");
            }
            return FormValidation.warning(sb.toString());
        }

        return FormValidation.ok("Job is properly configured for GitHub Pull Request Builder");
    }

    /**
     * Returns a human-readable summary of linting results.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        if (hasErrors()) {
            sb.append("Configuration Errors:\n");
            for (String error : errors) {
                sb.append("  - ").append(error).append("\n");
            }
        }
        
        if (hasWarnings()) {
            sb.append("Configuration Warnings:\n");
            for (String warning : warnings) {
                sb.append("  - ").append(warning).append("\n");
            }
        }
        
        if (!hasErrors() && !hasWarnings()) {
            sb.append("Job configuration is valid.\n");
        }
        
        return sb.toString();
    }
}
