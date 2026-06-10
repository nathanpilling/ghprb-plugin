Contributing
======

This document describes contribution and development for this plugin.

What we expect of each other
-----

#### Contributors
* Pull requests should describe the problem and how it's being solved
* Pull requests with code should include tests (positive, negative, etc) for the conditions being introduced
* Pull requests should call out any new requirements as part of the change
* CHANGELOG additions would be appreciated
* Contributors are still human; things will go wrong & that's ok
  * When things go wrong, contributors should be willing to help solve them if their changes were invovled.

#### Maintainers
* We promise to give some response in a timely manner (even if it's "I can't help right now")
* When a change needs to be reverted, or a regression is introduced, we promise to contact the original contributor
* If we need to refactor a pull request, we'll work hard to maintain the original contributor's commits
* We are still human; things will go wrong & that's ok
  * When things go wrong, we will help solve them


Things to help you develop
----

#### Starter
* As always, you may find the Jenkins development wiki docs to be helpful. For starters, here's [Plugin Development](https://wiki.jenkins-ci.org/display/JENKINS/Plugin+tutorial)
* Much of the plugin relies on the Jenkins [Github-API](https://github.com/kohsuke/github-api) library. Reading through that can give you a good perspective on what ghprb can be capable of doing.

#### Tips
* There are many interactions with GitHub as part of the plugin, and we currently don't have a test harness that can stand in place of the GitHub API or cloning. If running an instance on localhost (for example, using a docker image or using hpi:run in an IDE), you can use ngrok, as described on [GitHub docs](https://developer.github.com/webhooks/configuring/#using-ngrok). That can be helpful for catching webhooks and directing them to the localhost instance. (Remember you're opening your localhost to the world, and we assume you understand the risks and the local firewall/port restrictions.)

#### Understanding the Codebase

* See [ARCHITECTURE.md](ARCHITECTURE.md) for an overview of how the plugin works — the two operating modes, the key classes, and the startup and webhook data flows.
* Key entry points:
  * `GhprbTrigger.start()` — called when a job with this trigger loads
  * `GhprbRootAction.doIndex()` — HTTP endpoint for incoming webhooks
  * `GhprbRepository.check()` — polling entrypoint (runs on cron schedule)
  * `GhprbTrigger.DescriptorImpl.doValidateJobConfiguration()` — AJAX endpoint for job configuration validation
* The linting system (`GhprbJobLinter`) validates job configuration at two points:
  * **Automatic**: runs in `GhprbTrigger.start()` and logs any issues
  * **On-demand**: called via AJAX from the web UI when users click "Validate Configuration"
  * Always non-blocking — validation issues do not prevent the trigger from running
