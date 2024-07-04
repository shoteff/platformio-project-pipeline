node {
    stage('Checkout') {
        def repoUrl = env.repositoryUrl
        def masterBranchName = env.masterBranch

        echo ("Checking out GIT repo: " + repoUrl)
        checkout (
            poll: false, 
            scm: scmGit(branches: [[name: '*/' + masterBranchName]], extensions: [localBranch()], userRemoteConfigs: [[url: repoUrl]])
        )
    }

    stage('Build') {
        timeout(activity: true, time: 10) {
        // some block
        }
    }

    stage('Upload') {
    // some block
    }    

}
