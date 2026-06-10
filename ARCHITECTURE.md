# Architecture

This document explains how the GitHub PR Trigger plugin works internally — the problem it solves, the two operating modes, the data flow through the system, and the non-obvious design decisions worth knowing about before modifying the code.

## The problem it solves

Jenkins freestyle jobs have no built-in concept of a pull request. They know about branches and commits, but not about the PR lifecycle: opened, updated, commented on, closed. This plugin bridges that gap — it makes Jenkins treat a PR as a first-class trigger event, and reports results back to GitHub as commit statuses.

This plugin is specifically for **freestyle jobs**. Pipeline and Multibranch Pipeline jobs should use the GitHub Branch Source plugin instead.

---

## Two operating modes

The plugin works in one of two modes, configured per-job:

**Webhook mode** (`useGitHubHooks = true`): GitHub POSTs to `/ghprbhook/` every time a PR event happens. Jenkins reacts immediately. The cron schedule still exists but is a no-op — `GhprbTrigger.run()` returns early when this flag is set.

**Polling mode** (`useGitHubHooks = false`): Jenkins calls `GhprbRepository.check()` on the cron schedule (default `H/5 * * * *`). It calls the GitHub API to list all open PRs and diffs them against its cached state to decide what changed.

Most production deployments use webhooks. Polling exists as a fallback if you can't expose Jenkins to the internet.

---

## Startup sequence

When Jenkins loads a job that has this trigger configured, it calls `GhprbTrigger.start()`. That method:

1. Reads the `GithubProjectProperty` (the "GitHub Project URL" field on the job) to extract the repo name, e.g. `owner/my-repo`.
2. Creates a `GhprbRepository` for that repo and calls `repository.load()`, which deserialises any previously persisted PR state from `JENKINS_HOME/jobs/<jobname>/ghprb-pull-requests.xml`.
3. Creates a `GhprbBuilds` instance (handles build lifecycle callbacks).
4. Creates a `Ghprb` instance (the business logic helper).
5. If in webhook mode: registers the job in `DESCRIPTOR.repoJobs` — an in-memory `Map<String, Set<Job>>` — and optionally submits a `StartHookRunnable` to create or verify the webhook on GitHub.

That `repoJobs` map is the routing table used when a webhook arrives.

---

## Key classes

| Class | Responsibility |
|---|---|
| `GhprbTrigger` | Jenkins `Trigger` subclass. Entry point for configuration, startup, build scheduling, and routing incoming webhook events to the right repository. |
| `GhprbTrigger.DescriptorImpl` | Holds global configuration (phrases, auth entries, defaults). Also owns the `repoJobs` routing map. |
| `GhprbRootAction` | HTTP endpoint at `/ghprbhook/`. Receives GitHub webhook POSTs, validates signatures, parses payloads, and fans out to relevant triggers asynchronously. |
| `GhprbRepository` | Manages state for a single GitHub repository. Holds the `ConcurrentHashMap<Integer, GhprbPullRequest>` of open PRs, persists it to XML, and handles both webhook and polling update paths. |
| `GhprbPullRequest` | Represents a single PR and its state machine. Decides whether a given event should trigger a build based on whitelist, skip phrases, labels, branch filters, and file path filters. |
| `GhprbBuilds` | Hooks into `RunListener` to fire at build start and completion. Posts commit statuses to GitHub and optionally posts comments or closes PRs. |
| `Ghprb` | Stateless business logic helper. Pattern matching, whitelist/admin/org checks, credentials lookup, extension merging. |
| `GhprbGitHubAuth` | Wraps a Jenkins credentials ID. Builds authenticated GitHub connections via `GitHubBuilder`. Also handles HMAC-SHA1 webhook signature verification. |

---

## Webhook data flow

```
GitHub POST /ghprbhook/
  └─ GhprbRootAction.doIndex()
       ├─ parse Content-Type (application/json or form-encoded)
       └─ handleAction(event, signature, payload)
            ├─ parse payload anonymously to extract repo name
            ├─ getTriggers(repoName)          ← looks up repoJobs map, verifies signatures
            └─ for each matching trigger:
                 pool.submit(StartTrigger)    ← CachedThreadPool, async
                      ├─ re-parse payload with trigger's authenticated GitHub connection
                      └─ trigger.handlePR() or trigger.handleComment()
                           └─ GhprbRepository.onPullRequestHook() or onIssueCommentHook()
                                └─ GhprbPullRequest.check() / tryBuild()
                                     └─ GhprbBuilds.build()
                                          └─ trigger.scheduleBuild2()  ← queues Jenkins build
```

### Why the payload is parsed twice

The payload is first parsed with an anonymous GitHub connection (enough to extract the repo name for routing), then re-parsed with each job's authenticated connection. This gives the downstream code an authorised view of the objects — needed to read private repo details, post statuses, and make API calls on behalf of the configured credentials.

### CSRF exemption

`GhprbRootActionCrumbExclusion` exempts `/ghprbhook/` from Jenkins' CSRF protection. This is intentional — GitHub cannot provide a Jenkins crumb. Signature verification (HMAC-SHA1 via `X-Hub-Signature`) is the security control instead. Jobs with no secret configured accept any payload; always configure a webhook secret in production.

---

## PR state machine

`GhprbRepository` holds a `ConcurrentHashMap<Integer, GhprbPullRequest>` — one entry per open PR — persisted to `ghprb-pull-requests.xml` between restarts.

When a PR event arrives, the repository either creates a new `GhprbPullRequest` (new PR number) or updates the existing one. `GhprbPullRequest.init()` runs on first encounter; `check()` runs on subsequent events.

`GhprbPullRequest.check()` decides whether to trigger a build by evaluating, in order:

1. **Whitelist**: is the PR author trusted? If not, build does not fire until an admin posts the "ok to test" phrase.
2. **Skip phrases**: if the PR title or body matches (default: `[skip ci]`), no build.
3. **Label filtering**: white/blacklisted GitHub labels block or gate builds.
4. **Branch filtering**: target branch allow/deny lists.
5. **File path filtering**: included/excluded region patterns (like GitSCM's sparse checkout, but for deciding whether to trigger at all).
6. **Trigger phrase**: if `onlyTriggerPhrase` is set, opening a PR alone is insufficient — someone must comment the configured trigger phrase.

---

## Build scheduling and environment variables

`GhprbBuilds.build()` → `trigger.scheduleBuild()` queues the Jenkins build. Before queuing, the plugin assembles a `GhprbParametersAction` containing these environment variables:

| Variable | Content |
|---|---|
| `sha1` | Commit to build — merge ref (`origin/pr/N/merge`) if merge mode, actual head SHA otherwise |
| `ghprbActualCommit` | PR head commit SHA, always |
| `ghprbPullId` | PR number |
| `ghprbSourceBranch` / `ghprbTargetBranch` | Branch names |
| `ghprbPullAuthorLogin` / `ghprbPullAuthorEmail` | PR author |
| `ghprbTriggerAuthorLogin` | Who triggered this build (may differ from author — e.g. the person who commented "retest") |
| `ghprbCommentBody` | The comment that triggered the build, if any |
| `ghprbPullLink` | URL to the PR on GitHub |
| `ghprbPullTitle` / `ghprbPullDescription` | PR metadata |
| `ghprbActualCommitAuthor` / `ghprbActualCommitAuthorEmail` | Git commit author |
| `ghprbGhRepository` | `owner/repo` name |
| `ghprbCredentialsId` | Credentials ID used for this job |

Your Git SCM step uses `${sha1}` as the refspec — that's the handshake between this plugin and the git plugin.

---

## Build lifecycle callbacks

`GhprbBuilds` hooks into Jenkins' `RunListener` to fire at two points:

**`onStarted`**: Posts a `PENDING` commit status to GitHub. Also checks mergeability and can abort the build early if the PR cannot be merged.

**`onCompleted`**: Posts the final commit status (SUCCESS / FAILURE / ERROR). Optionally posts a comment on the PR with build results, log excerpts, or a link back to Jenkins. If `autoCloseFailedPullRequests` is configured, closes the PR on failure.

Both go through the extension system.

---

## The extension system

Rather than hardcoding every behaviour, the plugin uses a `GhprbExtension` interface hierarchy with pluggable implementations. Global defaults live on `DescriptorImpl`; per-job overrides live on the trigger. `Ghprb.getJobExtensions()` merges them, with job-level settings winning.

Key extension interfaces:

| Interface | Purpose |
|---|---|
| `GhprbCommitStatus` | Controls what gets posted as a GitHub commit status. Default impl: `GhprbSimpleStatus`. |
| `GhprbBuildStep` | Called when a build is scheduled. |
| `GhprbBuildStatus` | Posts comments on build completion. |
| `GhprbGlobalDefault` | Marks an extension that has a global default overridable per-job. |

---

## Non-obvious design decisions

**`repoJobs` is in-memory only.** The routing map from repo name → jobs is rebuilt on startup as each job's trigger calls `start()`. A webhook arriving before all jobs have started will be silently dropped for the jobs that haven't registered yet.

**PR state is per-job, not per-repo.** Two Jenkins jobs watching the same GitHub repo each maintain their own `ghprb-pull-requests.xml` and their own copy of PR state. They do not share it.

**`GhprbParametersAction` is not `ParametersAction`.** The plugin uses a custom subclass specifically to bypass Jenkins' SECURITY-170 hardening, which prevents injecting parameters not declared in the job's parameter definitions. This class is load-bearing and cannot be replaced with the standard `ParametersAction`.

**Previous `BuildData` is injected before queuing.** The trigger fetches `BuildData` from the previous build of this PR and passes it as an action when scheduling the new build. This allows the git plugin to compute a correct changelog — what changed since the last build of this PR, not since the last build of the job. It is removed from the action list after the build completes to avoid polluting the build history.

**Double async dispatch in webhook path.** When a webhook arrives, `StartTrigger` is submitted to a `CachedThreadPool`, and then inside that runnable, `triggerPr()` / `triggerComment()` spawn *another* anonymous `Thread`. Errors in the inner thread are logged but not propagated. This is original plugin design and is a known rough edge.

**Polling is always scheduled, even in webhook mode.** The cron fires on every interval regardless. `GhprbTrigger.run()` checks `getUseGitHubHooks()` and returns immediately if webhooks are enabled. This is harmless overhead.
