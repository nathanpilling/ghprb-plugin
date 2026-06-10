j = namespace("jelly:core")
f = namespace("/lib/form")

// Configuration Validation Panel
div(id: "ghprb-lint-panel", style: "margin: 15px 0; padding: 15px; border: 1px solid #ddd; border-radius: 5px; background-color: #f9f9f9;") {
  div(style: "margin-bottom: 10px; font-weight: bold;") {
    text(_("Configuration Validation"))
  }
  
  button(
    type: "button",
    class: "btn btn-default",
    id: "ghprb-lint-button",
    style: "margin-bottom: 10px; padding: 8px 15px;",
    onclick: "validateGhprbConfiguration()"
  ) {
    text(_("Validate Configuration"))
  }
  
  div(id: "ghprb-lint-result", style: "display: none;") {
    div(id: "ghprb-lint-errors", class: "ghprb-lint-errors", style: "color: #d32f2f; margin: 10px 0;")
    div(id: "ghprb-lint-warnings", class: "ghprb-lint-warnings", style: "color: #f57f17; margin: 10px 0;")
    div(id: "ghprb-lint-success", class: "ghprb-lint-success", style: "color: #388e3c; margin: 10px 0; font-weight: bold;")
  }
  
  div(id: "ghprb-lint-loading", style: "display: none; color: #1976d2;") {
    text(_("Validating configuration..."))
  }
}

script() {
  raw("""
function validateGhprbConfiguration() {
  var resultDiv = document.getElementById('ghprb-lint-result');
  var loadingDiv = document.getElementById('ghprb-lint-loading');
  var errorsDiv = document.getElementById('ghprb-lint-errors');
  var warningsDiv = document.getElementById('ghprb-lint-warnings');
  var successDiv = document.getElementById('ghprb-lint-success');
  
  // Clear previous results
  errorsDiv.innerHTML = '';
  warningsDiv.innerHTML = '';
  successDiv.innerHTML = '';
  
  // Show loading
  loadingDiv.style.display = 'block';
  resultDiv.style.display = 'none';
  
  // Get current job name from the form
  var jobName = document.getElementsByName('name')[0];
  if (!jobName || !jobName.value) {
    loadingDiv.style.display = 'none';
    errorsDiv.innerHTML = '<strong>Error:</strong> Job name is required';
    resultDiv.style.display = 'block';
    return;
  }
  
  // Call the validation endpoint
  var path = window.location.pathname;
  var rootPath = path.substring(0, path.indexOf('/job/') > -1 ? path.indexOf('/job/') : path.lastIndexOf('/'));
  
  fetch(rootPath + '/descriptor/org.jenkinsci.plugins.ghprb.GhprbTrigger/validateJobConfiguration?jobName=' + 
        encodeURIComponent(jobName.value), {
    method: 'GET',
    credentials: 'same-origin'
  })
  .then(function(response) {
    loadingDiv.style.display = 'none';
    if (!response.ok) {
      throw new Error('HTTP error, status = ' + response.status);
    }
    return response.json();
  })
  .then(function(data) {
    resultDiv.style.display = 'block';
    
    if (data.errors && data.errors.length > 0) {
      var errorsList = '<strong style="color: #d32f2f;">Configuration Issues Found:</strong><ul style="margin: 8px 0; padding-left: 20px;">';
      for (var i = 0; i < data.errors.length; i++) {
        errorsList += '<li style="margin: 4px 0;">' + data.errors[i] + '</li>';
      }
      errorsList += '</ul>';
      errorsDiv.innerHTML = errorsList;
    }
    
    if (data.warnings && data.warnings.length > 0) {
      var warningsList = '<strong style="color: #f57f17;">Recommendations:</strong><ul style="margin: 8px 0; padding-left: 20px;">';
      for (var i = 0; i < data.warnings.length; i++) {
        warningsList += '<li style="margin: 4px 0;">' + data.warnings[i] + '</li>';
      }
      warningsList += '</ul>';
      warningsDiv.innerHTML = warningsList;
    }
    
    if ((!data.errors || data.errors.length === 0) && (!data.warnings || data.warnings.length === 0)) {
      successDiv.innerHTML = '<strong style="color: #388e3c;">✓ Configuration is correctly set up for GitHub Pull Request Builder!</strong>';
    }
  })
  .catch(function(error) {
    loadingDiv.style.display = 'none';
    resultDiv.style.display = 'block';
    errorsDiv.innerHTML = '<strong>Error:</strong> Unable to validate configuration: ' + error.message;
    console.error('Validation error:', error);
  });
}
""")
}

f.entry(field: "gitHubAuthId", title:_("GitHub API credentials")) {
  f.select()
}

f.entry(field: "adminlist", title: _("Admin list")) {
  f.textarea(default: descriptor.adminlist) 
}
f.entry(field: "useGitHubHooks", title: "Use github hooks for build triggering") {
  f.checkbox() 
}
f.advanced() {
  f.entry(field: "triggerPhrase", title: _("Trigger phrase")) {
    f.textbox() 
  }
  f.entry(field: "onlyTriggerPhrase", title: "Only use trigger phrase for build triggering") {
    f.checkbox() 
  }
  f.entry(field: "autoCloseFailedPullRequests", title: _("Close failed pull request automatically?")) {
    f.checkbox(default: descriptor.autoCloseFailedPullRequests) 
  }
  f.entry(field: "skipBuildPhrase", title: _("Skip build phrase")) {
    f.textbox(default: descriptor.skipBuildPhrase)
  }
  f.entry(field: "displayBuildErrorsOnDownstreamBuilds", title: _("Display build errors on downstream builds?")) {
    f.checkbox(default: descriptor.displayBuildErrorsOnDownstreamBuilds)
  }
  f.entry(field: "cron", title: _("Crontab line"), help: "/descriptor/hudson.triggers.TimerTrigger/help/spec") {
    f.textbox(default: descriptor.cron, checkUrl: "'descriptorByName/hudson.triggers.TimerTrigger/checkSpec?value=' + encodeURIComponent(this.value)") 
  }
  f.entry(field: "whitelist", title: _("White list")) {
    f.textarea() 
  }
  f.entry(field: "orgslist", title: _("List of organizations. Their members will be whitelisted.")) {
    f.textarea() 
  }
  f.entry(field: "blackListLabels", title: _("List of GitHub labels for which the build should not be triggered.")) {
    f.textarea()
  }
  f.entry(field: "whiteListLabels", title: _("List of GitHub labels for which the build should only be triggered. (Leave blank for 'any')")) {
    f.textarea()
  }
  f.entry(field: "allowMembersOfWhitelistedOrgsAsAdmin", title: "Allow members of whitelisted organizations as admins") {
    f.checkbox() 
  }
  f.entry(field: "permitAll", title: "Build every pull request automatically without asking (Dangerous!).") {
    f.checkbox() 
  }
  f.entry(field: "buildDescTemplate", title: _("Build description template")) {
      f.textarea()
  }
  f.entry(field: "blackListCommitAuthor", title: _("Blacklist commit authors")) {
    f.textbox(default: descriptor.blackListCommitAuthor)
  }
  f.entry(field: "whiteListTargetBranches", title: _("Whitelist Target Branches:")) {
    f.repeatable(field: "whiteListTargetBranches", minimum: "1", add: "Add Branch") {
      table(width: "100%") {
        f.entry(field: "branch") {
          f.textbox() 
        }
        f.entry(title: "") {
          div(align: "right") {
            f.repeatableDeleteButton(value: "Delete Branch") 
          }
        }
      }
    }
  }
  f.entry(field: "blackListTargetBranches", title: _("Blacklist Target Branches:")) {
    f.repeatable(field: "blackListTargetBranches", minimum: "1", add: "Add Branch") {
      table(width: "100%") {
        f.entry(field: "branch") {
          f.textbox() 
        }
        f.entry(title: "") {
          div(align: "right") {
            f.repeatableDeleteButton(value: "Delete Branch") 
          }
        }
      }
    }
  }
  f.entry(field: "includedRegions", title: _("Included regions")) {
    f.textarea()
  }
  f.entry(field: "excludedRegions", title: _("Excluded regions")) {
    f.textarea()
  }
}
f.advanced(title: _("Trigger Setup")) {
  f.entry(title: _("Trigger Setup")) {
    f.hetero_list(items: instance == null ? null : instance.extensions, 
        name: "extensions", oneEach: "true", hasHeader: "true", descriptors: descriptor.getExtensionDescriptors()) 
  }
}
