def platformioBuildScript = 'C://Users/Administrator/Desktop/python-http-server-dont-delete/platformio-build.bat'

node {
    stage('Checkout') {
        def repoUrl = env.repositoryUrl
        def masterBranchName = env.masterBranch

        echo ("Cleaning workspace...")
        cleanWs()

        echo ("Checking out GIT repo: " + repoUrl)
        checkout (
            poll: false, 
            scm: scmGit(branches: [[name: '*/' + masterBranchName]], extensions: [localBranch()], userRemoteConfigs: [[url: repoUrl]])
        )
    }

    stage('Build') {
        timeout(activity: true, time: 10) {
            bat platformioBuildScript
        }
    }

    stage('Upload') {
    // some block
    }    

}
