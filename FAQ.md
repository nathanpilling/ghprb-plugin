Frequently Asked Questions
=====

**Q: Startup seems slow. What can I do?**

A: A poolsize is set for 5 threads by default. This can be overridden with a 
JVM arg: `-Dorg.jenkinsci.plugins.ghprb.GhprbTrigger.poolSize=5`

---

**Q: I get "No ECDSA host key is known" when Jenkins tries to connect to the Git repository.**

A: Jenkins needs the remote host in its own known_hosts file. Two things to check:

1. Go to **Manage Jenkins → Security → Git Host Key Verification Configuration** and set it to **Accept first connection** (easiest for local/trusted servers).
2. Alternatively, add the host key manually to `$JENKINS_HOME/.ssh/known_hosts`:
   ```
   ssh-keyscan your-git-host.example.com >> $JENKINS_HOME/.ssh/known_hosts
   ```
   Note: `~/.ssh/known_hosts` on the OS user running Jenkins is NOT used by the Git plugin in all configurations.

---

**Q: The plugin logs "Error while accessing rate limit API" on every poll.**

A: The GitHub API credentials are not configured, so the plugin is connecting anonymously and hitting rate limits (or failing on private repos). Fix:

1. Go to **Manage Jenkins → Configure System → GitHub Pull Request Builder**.
2. Add a GitHub server entry with your server's API URL (for GitHub Enterprise: `https://your-host/api/v3`).
3. Set the **Credentials** field to a **Secret text** credential containing a personal access token with `repo` scope.
4. In the job config, select this credential in the **GitHub API credentials** dropdown under the GitHub Pull Request Builder trigger.

---

**Q: Builds are never triggered even though the plugin is polling and finding open PRs.**

A: Most likely the PR author is not whitelisted. The plugin logs `Author of #N username not in whitelist!` when this happens. Fix by adding the GitHub username to the **Admin list** field in the job's GitHub Pull Request Builder trigger configuration, or enable **Permit all** (only for trusted/internal repos).

---

**Q: The plugin finds PR #N but logs "no new comments nor commits" and doesn't trigger a build.**

A: This happens on the first poll after a job is configured — the plugin registers existing open PRs as already-seen and won't build them retroactively. To force a build, either push a new commit to the PR branch or comment `retest this please` on the PR.

---

**Q: "Failed to connect to repository" with SSH credential but the key works from the command line.**

A: The Git plugin may be using the wrong SSH key. Check that the credential selected in the Git SCM **Credentials** dropdown is an **SSH Username with private key** credential (not Username/Password). For SSH remote URLs (`git@...`), only SSH key credentials are valid.
