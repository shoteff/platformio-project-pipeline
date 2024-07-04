// src/utils.groovy

RED = '\033[0;31m'
WHITE = '\033[0m'

//  --------------- project logic ---------------

void cleanBrandMakerMvnRepo() {
    sh "rm -rf ${MAVEN_REPO}/com/brandmaker"
}

boolean isDevBranch(String branchName) {
    return branchName.contains('dev_')
}

boolean isReleaseBranch(String releaseBranchRegex, String branchName) {
    return branchName ==~ /${releaseBranchRegex}/
}

String getVersionFromBranchName(String branchName) {
    return getVersionFromBranchNameOrDefault(branchName, 'master')
}

String getVersionFromBranchNameOrDefault(String branchName, String defaultValue = null) {
    return branchName ==~ VERSION_PATTERN ? (branchName =~ VERSION_PATTERN)[0][1] : defaultValue
}

void checkoutAndBuildBmProductConfig(
        String projectBranchNamePrefix, //mp, rm, shop
        String gitlabCredentialsId,
        String gitlabTargetBranch,
        String jdk,
        String mvn
) {
    def version = this.getVersionFromBranchName(gitlabTargetBranch)
    def isDev = this.isDevBranch(gitlabTargetBranch)
    def branchName

    if (version == 'master') {
        branchName = isDev ? "dev_${projectBranchNamePrefix}_master" : 'master'
    }
    else {
        branchName = (isDev ? "dev_${projectBranchNamePrefix}_" : '') + "release-${version}"
    }

    this.gitlabCheckout('bm-product/bm-product-config', gitlabCredentialsId, 'bm-product-config', branchName)
    this.runMaven(jdk, mvn, 'mvn clean install -U -B -f bm-product-config/pom.xml')
}

//  --------------- pipeline ---------------

void skipStage() {
    catchError(buildResult: 'SUCCESS', stageResult: 'NOT_BUILT', catchInterruptions: false) {
        error "Skip stage - ${STAGE_NAME}"
    }
}

void checkForAutoCancelled(boolean autoCancelled) {
    if (autoCancelled) {
        error('Aborting')
    }
}

boolean isParamExist(String propertyName) {
    return params[propertyName] != null && params[propertyName] != "";
}

//  --------------- Map ---------------

Map<String, String> makeRemote(Map<String, String> cfg) {
    return makeFilteredMap(cfg, 'remote_')
}
Map<String, String> makeFilteredMap(Map<String, String> cfg, String propPrefix) {
    Map<String, String> filteredMap = [:]
    for (e in cfg) {
        if (e.getKey().startsWith(propPrefix)) {
            String key = e.getKey().substring(propPrefix.length())
            filteredMap.put(key, e.getValue())
        }
    }
    return filteredMap
}

Map<String, String> loadPropertyMap(String file) {
    Map<String, String> res = [:]

    Map<String, Object> props = readProperties file: "$file"

    props.each { key, value ->
        res[key] = "$value"
    }

    //    echo("Loaded props from file: $file, \n\n Props: ${props}")
    return res
}

String mapToPropertiesString(Map<String, String> map) {
    return mapToString(map, '\n')
}

String mapToString(Map<String, String> map, String entriesSeparator = ',', String keyValuePrefix = '', String keyValueSeparator = '=') {
    List<String> propertiesList = mapToStringList(map, keyValuePrefix, keyValueSeparator)
    String propertiesString = propertiesList.join(entriesSeparator)
    return propertiesString
}

List<String> mapToStringList(Map<String, String> map, String keyValuePrefix = '', String keyValueSeparator = '=') {
    List<String> propertiesList = []
    for (entry in map) {
        propertiesList.add(keyValuePrefix + entry.key + keyValueSeparator + entry.value)
    }
    return propertiesList
}

//  --------------- shell ---------------

/**
 * Runs shell script without expansion of environment variables (bash options -ve).
 * @param command
 * @param envVariables = map of environment variables within this execution
 * @return
 */
boolean runWithEnv(Map<String, String> envVariables = [:], Closure command) {
    List<String> envVarsList = mapToStringList(envVariables)
    withEnv(envVarsList) {
        return command()
    }
}

/**
 * Runs shell script without expansion of environment variables (bash options -ve).
 * @param command
 * @return
 */
String runShell(String command, boolean withResult = false) {
    return sh(script: "#!/bin/sh -ve\n${command}\n", returnStdout: withResult)
}

boolean remoteSshCommandWithReturn(remote, remoteCommand) {
    boolean res = true

    try {
        sshCommand remote: remote, command: remoteCommand
    } catch(e) {
        res = false
    }

    return res
}

//  --------------- Git ---------------

String getGitlabRelativePathFromSshUrl(String gitlabSshUrl) {
    int beginIndex = gitlabSshUrl.indexOf(":") + 1
    int endIndex = gitlabSshUrl.indexOf(".git")

    return gitlabSshUrl.substring(beginIndex, endIndex)
}

void gitlabCheckout(
        String gitlabRelativePath,
        String gitlabCredentialsId,
        String targetDir,
        String gitlabBranch,
        String gitCloneReference = ''
) {
    checkout(changelog: true, poll: true, scm: [
            $class: 'GitSCM',
            branches: [[name: "origin/${gitlabBranch}"]],
            browser: [$class: 'GitLab', repoUrl: "https://gitlab.dev.brandmaker.com/${gitlabRelativePath}", version: "15.3"],
            doGenerateSubmoduleConfigurations: false,
            extensions: [
                    [$class: 'RelativeTargetDirectory', relativeTargetDir: "${targetDir}"],
                    [$class: 'PruneStaleBranch'],
                    //[$class: 'WipeWorkspace'],
                    [$class: 'CloneOption', noTags: true, shallow: false, honorRefspec: true, reference: "${gitCloneReference}"],
                    [$class: 'LocalBranch', localBranch: "${gitlabBranch}"],
                    //[$class: 'CleanCheckout'],
                    [$class: 'CleanBeforeCheckout']
            ],
            submoduleCfg: [],
            userRemoteConfigs: [[
                                credentialsId: "${gitlabCredentialsId}",
                                //refspec: '+refs/heads/*:refs/remotes/origin/* +refs/merge-requests/*/head:refs/remotes/origin/merge-requests/*',
                                name: 'origin', url: "git@gitlab.dev.brandmaker.com:${gitlabRelativePath}.git"]]
    ])
}

void gitCheckoutWithMerge(
        String gitlabSourceRelativePath,
        String gitlabTargetRelativePath,
        String gitlabCredentialsId,
        String targetDir,
        String gitlabSourceBranch,
        String gitlabTargetBranch,
        String gitCloneReference = ''
) {
    checkout(changelog: true, poll: true, scm: [
            $class: 'GitSCM',
            branches: [[name: "origin-source/${gitlabSourceBranch}"]],
            browser: [$class: 'GitLab', repoUrl: "https://gitlab.dev.brandmaker.com/${gitlabSourceRelativePath}", version: "15.3"],
            doGenerateSubmoduleConfigurations: false,
            extensions: [
                    [$class: 'RelativeTargetDirectory', relativeTargetDir: "${targetDir}"],
                    [$class: 'PruneStaleBranch'],
                    //[$class: 'WipeWorkspace'],
                    [$class: 'CloneOption', noTags: true, shallow: false, honorRefspec: true, reference: "${gitCloneReference}"],
                    [$class: 'PreBuildMerge', options: [fastForwardMode: 'FF_ONLY', mergeRemote: 'origin-target', mergeStrategy: 'DEFAULT', mergeTarget: "${gitlabTargetBranch}"]],
                    //[$class: 'LocalBranch', localBranch: ''],
                    [$class: 'ChangelogToBranch', options: [compareRemote: 'origin-target', compareTarget: "${gitlabTargetBranch}"]],
                    //[$class: 'CleanCheckout'],
                    [$class: 'CleanBeforeCheckout']
            ],
            submoduleCfg: [],
            userRemoteConfigs: [[
                                credentialsId: "${gitlabCredentialsId}",
                                refspec: '+refs/heads/*:refs/remotes/origin-source/*',
                                //refspec: '+refs/heads/*:refs/remotes/origin/* +refs/merge-requests/*/head:refs/remotes/origin/merge-requests/*',
                                name: 'origin-source', url: "git@gitlab.dev.brandmaker.com:${gitlabSourceRelativePath}.git"],
                                [
                                credentialsId: "${gitlabCredentialsId}",
                                name: 'origin-target', url: "git@gitlab.dev.brandmaker.com:${gitlabTargetRelativePath}.git"]
                                ]
    ])
}

void gitRebase(String targetDir, String gitlabTargetBranch) {
    def result = sh (
            script: "cd ${targetDir} && git -c 'user.name=Build Tools' -c 'user.email=dev-tools-build@brandmaker.com' rebase origin-target/${gitlabTargetBranch}",
            returnStatus: true
    )

    if (result != 0) {
        echo "${RED}The git rebase produce error (conflict) and we are aborting it!${WHITE}"
        sh "cd ${targetDir} && git rebase --abort"
        sh 'exit 1'
    }
}

void gitSafeForcePush(String targetDir, String gitlabSourceBranch) {
    def result = sh (
            script: "cd ${targetDir} && git -c 'user.name=Build Tools' -c 'user.email=dev-tools-build@brandmaker.com' " +
                    "push -o ci.skip --force-with-lease=${gitlabSourceBranch} origin-source HEAD:refs/heads/${gitlabSourceBranch}",
            returnStatus: true
    )

    if (result != 0) {
        echo "${RED}The git push --force-with-lease produce error (conflict)!${WHITE}"
        sh 'exit 1'
    }
}

String generateGitlabMRComment(String action = 'Build') {
    String icon = getResultIcon(currentBuild.currentResult)

    return "${icon} Jenkins $action ${currentBuild.currentResult?:'FAILURE'}\n\nResults available at: [Jenkins " +
            "[${JOB_NAME} #${BUILD_NUMBER}]](${BUILD_URL})"
}

String getResultIcon(String result) {
    if (result == 'SUCCESS') {
        return ":white_check_mark:";
    } else if (result == 'ABORTED') {
        return ":point_up:";
    } else if (result == 'UNSTABLE') {
        return ":warning:";
    } else {
        return ":negative_squared_cross_mark:";
    }
}

String buildResultToGitlabStatus() {
    return currentBuild.currentResult == 'SUCCESS' ? 'success' : 'failed'
}

//  --------------- Email ---------------

void notifyBuild(String recipientsList) {
    def body = "Check console output at <a href=\"$BUILD_URL/console\">$BUILD_URL</a> to view the results."

    def currentResult = currentBuild.result ?: 'SUCCESS'
    def previousResult = currentBuild.previousBuild?.result
    def isBackToNormal = previousResult != null && previousResult != currentResult && currentResult == 'SUCCESS'
    def subjectStatusMessage = isBackToNormal ? "is back to normal" : "$currentBuild.currentResult!"

    def subject = "$JOB_NAME - Jenkins Build #$BUILD_NUMBER - $subjectStatusMessage"

    if (currentResult == 'FAILURE' /*|| currentResult == 'UNSTABLE' || isBackToNormal*/) {
        mail (
                to: "${recipientsList}",
                subject: "$subject",
                body: "$body",
                mimeType: 'text/html',
                from: 'jenkins@brandmaker.com'
        )
    }
}

return this
