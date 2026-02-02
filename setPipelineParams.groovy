def call(Map userParms) {
    //Set a map which will be populated by the below
    def returnParams = [:]
    def dtprojFilesPathList = []
    //Define the current java version to default to
    def default_java_version = 'java_1.8'

    //Get the git repository information
    def gitRepoDetails = getGitRepoDetails()

    //Get the Author Email address from commit id
    def author_email = getAuthorEmail(GIT_COMMIT)
  
    //Map in the type
    returnParams.type = userParms.type

    //Use the Groovy features of the map function to set the values we want
    //The get function will either retrieve the value from the userParms map
    //or use the default following the comma
    returnParams.repoName = userParms.get('repoName', gitRepoDetails.repoName)
    returnParams.projectName = userParms.get('projectName', gitRepoDetails.projectName)
    returnParams.componentName = userParms.get('componentName', gitRepoDetails.componentName)
    
    //To get the author email
    returnParams.author_email = "${author_email}"

    //Allow filtering of the archive folder - this is the folder within the target to package
    returnParams.archiveFolder = userParms.get('archiveFolder', null)

    returnParams.groupID = userParms.get('groupID', returnParams.projectName).toLowerCase()
    returnParams.nexusPrefix = userParms.get('nexusPrefix', gitRepoDetails.nexusPrefix)
    
    returnParams.packaging = userParms.get('packaging', 'zip')
    
    //buildDriectory sets the build into a subdirectory
    returnParams.buildDirectory = getBuildDirectory(userParms.get('buildDirectory', null))
    returnParams.preBuild = userParms.get('preBuild', null)
  
    //Allowing other folders to be zipped
    returnParams.separateFolder = userParms.get('separateFolder', 'empty')
  
    //Allowing dotnet core repositories to include config folder at different level
    returnParams.dotnetExtraFolder = userParms.get('dotnetExtraFolder', '')

    //Allowing msbuild repositories to include config folder at different level
    returnParams.msbuildExtraFolder = userParms.get('msbuildExtraFolder', '\\')

    //Set Sonar quality gate param of QG with full blocker  
    returnParams.sonarQualityGateCheckFull = userParms.get('sonarQualityGateCheckFull', 'false')

    //SBOM report generation using dependency-check tool
    returnParams.sbomdpcheckScan = userParms.get('sbomdpcheckScan', 'true')
  
    //Set Sonar quality gate parameters
    //Forcing SonarBlocking Rules for the COG Projects under Neil
    // COG - Project covers blocking rule for AccountRiskScore and LimitProfiles in it
    // ISDG - Project covers Ignite and Claims biztalk aps
    def COGRepoNames = ["CREG", "FIRM", "ISDG", "Radarmanager", "Guidance", "UWP", "WV", "Workview", "COG", "FirstRate", "PLP"] as String[]
    def RepomatchFound = false // Flag to track if a match was found
    for (RepoNames in COGRepoNames) {
        if (returnParams.repoName.toLowerCase().startsWith(RepoNames.toLowerCase())) {
      echo "Forcing SonarBlock Rule for the COG Repos which is under Neil"
      returnParams.sonarQualityGateCheck = 'true'
      println "sonarQualityGateCheck value is : $returnParams.sonarQualityGateCheck"
      RepomatchFound = true 
      break 
        }
    }
  //Execute the else block only if no match was found
    if (!RepomatchFound) {
        echo "Applying SonarBlock Rule for the Repo which is not under Neil"
        returnParams.sonarQualityGateCheck = userParms.get('sonarQualityGateCheck', 'false')
    }
  
    //Nodejs build command with env name
    returnParams.NodejsBuildWithEnv = userParms.get('NodejsBuildWithEnv', 'false')
  
    //Windows Nodejs build command with restore Modules
    //returnParams.restoreModCommand = userParms.get('restoreModCommand', 'npm install & npx playwright install chromium')  
    returnParams.restoreModCommand = userParms.get('restoreModCommand', 'npm install')
    returnParams.clean_workspace = userParms.get('clean_workspace', 'true')
    
    // Define a map of control branches that are used to collate and build additional options
    // these branches will be used to checkout the Jenkinsfile and collate;
    // productionBranches - branches that will generate release artefacts that can be delivered to production. Deployments permitted.
    // protectedBranches - branches that can be deployed from without cross check with devTemplates but are not for production
    // devTemplates - templates that are available to deploy to without a protected branch. Read from develop

    // Map in deployments defined in Jenkinsfile
    returnParams.deploy = userParms.get('deploy', null)

    // Provide option to skip all deployments
    returnParams.skipDeploys = userParms.get('skipDeploys', null)
  
    if (returnParams.skipDeploys == "null" || returnParams.skipDeploys == "false") {
        returnParams.skipDeploys = null
    }
    
  if(returnParams.skipDeploys == null) {
        if(fileExists('jenkins.properties')) {
            properties = readProperties file:'jenkins.properties'
            returnParams.skipDeploys = "${properties['skipDeploys']}"
            if (returnParams.skipDeploys == "null" || returnParams.skipDeploys == "false") {
                returnParams.skipDeploys = null
            }
        }
    }

    // Provide option to skip DevOps process deployments
    returnParams.skipDevOps = userParms.get('skipDevOps', null)

    // Provide option to override the default ansible template/schema name used in the Jenkins pipeline
    returnParams.templateName = userParms.get('templateName', null)
    returnParams.targetschemaName = userParms.get('targetschemaName', null)

    // Now collate any additional information required. If this is develop then map in the information
    if(env.BRANCH_NAME.toLowerCase() == 'develop') {
        // Read in all the string values and convert to lists, setting defaults if required
        returnParams.productionBranches = convertStringListToListObject(userParms.get('productionBranches', 'master'))
        // Make sure develop is not in the production branch list
        if(returnParams.productionBranches.contains('develop')){
            println "develop cannot be a production branch, removing"
            returnParams.productionBranches.minus('develop')
        }
        returnParams.protectedBranches = convertStringListToListObject(userParms.get('protectedBranches', 'release'))
        // Make sure develop is in the protected branch list
        if(!returnParams.protectedBranches.contains('develop')){
            println "develop must be a protected branch, adding"
            returnParams.protectedBranches.add('develop')
        }
        returnParams.devTemplates = convertStringListToListObject(userParms.get('devTemplates', ''))
    } else {
        // Need to load information from the develop branch
        returnParams = loadDevelopInformation(returnParams)
    }
    
    //allow override of the binary file that will be uploaded, otherwise use the componentname.packaging.
    // artefact is then used through the build process to define the target file for creation and upload.
    returnParams.artefact = userParms.get('artefact', returnParams.componentName + '.' + returnParams.packaging)
    
    //Set the GDC appscan ID
    returnParams.appscanID = userParms.get('appscanID', 'eb209e67-fec7-4def-b5bc-7e03ee0fb251')

    //Based on the type of build there are certain parameters that need to be set and / or overridden
    //Need to get the version for the build.
    // Version is the version specified by the source code
    returnParams.version = null
    // Build version is the version that is used in the build tooling to ensure uniqueness
    returnParams.buildVersion = null
    returnParams.pomDefined = null

    // Set custom nexus upload command, if one exists
    returnParams.uploadCommand = userParms.get('uploadCommand', null)

    // Set variable to enable gitleaks scan
    returnParams.secretsScan = userParms.get('secretsScan', 'true')
    // Set variable to assign status to send gitleaks report. 
    //The variable set to null initially, the value will be set in gitleaks.groovy
    returnParams.sendreport = null

    // Read in the nexus inlcusion and exclusion rules
    if(fileExists('jenkins.properties')) {
        nexusProperties = readProperties file:'jenkins.properties'
        returnParams.nexusInclude = "${nexusProperties['nexusInclude']}"
        returnParams.nexusExclude = "${nexusProperties['nexusExclude']}"
        returnParams.notify_to = "${nexusProperties['notify_to']}"  //read emailnotification param
  returnParams.jira_jar_url = "${nexusProperties['jira_jar_url']}" //read jar artifact url from nexus
  returnParams.jar_name = "${nexusProperties['jar_name']}"    //read jar name
        returnParams.teams_webhook_url = "${nexusProperties['teams_webhook_url']}" //read teams webhookurl
    }
    // Set null as a value rather than as a string which is returned from the properties call above when not set.
    if (returnParams.nexusInclude == "null") {
        returnParams.nexusInclude = null
    }
    if (returnParams.nexusExclude == "null") {
        returnParams.nexusExclude = null
    }

    if (returnParams.notify_to == "null") {
        returnParams.notify_to = null
    }

    if (returnParams.teams_webhook_url == "null") {
        returnParams.teams_webhook_url = null
    }  

    // If the values are no longer null then the file contained information so format correctly into array
    if (returnParams.nexusInclude != null ) {
        returnParams.nexusInclude = returnParams.nexusInclude.trim().replaceAll("'","").replaceAll(",\$", "")
        returnParams.nexusInclude = convertStringListToListObject(returnParams.nexusInclude)
    }
    if (returnParams.nexusExclude != null ) {
        returnParams.nexusExclude = returnParams.nexusExclude.trim().replaceAll("'","").replaceAll(",\$", "")
        returnParams.nexusExclude = convertStringListToListObject(returnParams.nexusExclude)
    }

    returnParams.citsRepo = userParms.get('citsRepo')
    returnParams.projectLocation = userParms.get('projectLocation')
    returnParams.releaseName = userParms.get('releaseName')
    returnParams.testSet = userParms.get('testSet')
    returnParams.reportsCommit = userParms.get('reportsCommit')
    returnParams.citsBranch = userParms.get('citsBranch')

    switch(returnParams.type) {
        case "maven":
            //If this is maven then set the maven commands
            // Version
            // 1 - Look for a pom.xml, if it exists use values from there
            // 2 - Look for a jenkins.properties file, if it exists there then use that
            // Set the pom filename
            pom_filename = "pom.xml"
            // Set the build option, which is used to pass in the correct version information to the build command
            build_option = " -D"
            if(userParms.buildDirectory) {
                pom_filename = userParms.buildDirectory + "/" + pom_filename
            }
            if(fileExists(pom_filename)) {
                println "Using version from pom.xml located at $pom_filename"
                pom_model = readMavenPom file: pom_filename
                returnParams.version = pom_model.getVersion()
                // Set pomDefined flag to say defined from pom as version
                returnParams.pomDefined = 'version'
                // Cater for the revision format instead of version
                if(returnParams.version.equals("\${revision}")) {
                    // reset pomDefined flag to say set by revision
                    returnParams.pomDefined = 'revision'
                    returnParams.version = pom_model.properties['revision']
                }
                // Set the build version
                returnParams.buildVersion = setBuildVersion(returnParams)
                // Set the maven global version if not using revision otherwise the linking does not work
                if(returnParams.pomDefined == 'version') {
                    println("Using legacy version definition, attempting to set CI build version.")
                    println("Please consider using new revision parameter in pom.xml")
                    version_command = "mvn versions:set -DnewVersion=${returnParams.buildVersion}"
                    if (returnParams.buildDirectory != null) {
                        dir(returnParams.buildDirectory){
                            executeShellCommand(version_command)
                        }
                    } else {
                        executeShellCommand(version_command)
                    }
                }
                // Set the version build option
                build_option = build_option + returnParams.pomDefined + '="' + returnParams.buildVersion + '"'
            } else {
                println "No pom.xml, looking for jenkins.properties"
                if(fileExists('jenkins.properties')) {
                    properties = readProperties file:'jenkins.properties'
                    returnParams.version = "${properties['version']}"
                    // Set the version build option
                    returnParams.buildVersion = setBuildVersion(returnParams)
                    build_option = build_option + 'version="' + returnParams.buildVersion + '"'
                }
                else {
                    println("No pom.xml or jenkins.properties found.")
                }
            }
            // Configure the build command
            returnParams.buildCommand = userParms.get('buildCommand', 'mvn -B clean package -DskipTests')

            // Add the versin option to the build command to ensure the correct version information is used
      returnParams.testAutoMvnProject = userParms.get('testAutoMvnProject', 'false')
      if(returnParams.testAutoMvnProject == 'true') {
    returnParams.buildCommand = returnParams.buildCommand
      }
      else {
    returnParams.buildCommand = returnParams.buildCommand + build_option
      }

            returnParams.testCommand = userParms.get('testCommand', 'mvn test')
            // Cater for java builds that are earlier than 1.8, sonar needs to use 1.8
            // TODO - look for a better solution to this      
            if(jdk == default_java_version) {
            //##Ilaiya: the above condition is no more valid as most of the projects are started using higher than 1.8 now.so need to update.
                //returnParams.sonarCommand = "export JAVA_HOME=/usr/lib/jvm/jre-1.8.0-openjdk; mvn sonar:sonar"
     returnParams.sonarCommand = userParms.get('sonarCommand', 'export JAVA_HOME=/opt/ci/jenkins/tools/hudson.model.JDK/java_1.17; mvn sonar:sonar')
        
            }
            else {
     returnParams.sonarCommand = userParms.get('sonarCommand', 'mvn sonar:sonar')        
                
            }
      
            returnParams.suffixBuildNumberForProdBranch = userParms.get('suffixBuildNumberForProdBranch', 'false')
            returnParams.overrideBranchInNexusVersion = userParms.get('overrideBranchInNexusVersion', 'false')
        break
        case "gradle":
            // Version
            // 1 - Look for a build.gradle, if it exists use values from there
            // 2 - Look for a jenkins.properties file, if it exists there then use that
            gradle_filename = "build.gradle"
            if(userParms.buildDirectory) {
                gradle_filename = userParms.buildDirectory + "/" + gradle_filename
            }
            if(fileExists(gradle_filename)) {
                returnParams.version = executeShellCommand("gradle properties -q | grep \"^version:\" | awk '{print \$2}'", 'true')
                returnParams.buildVersion = setBuildVersion(returnParams)
            } else {
                println "No build.gradle, looking for jenkins.properties"
                if(fileExists('jenkins.properties')) {
                    properties = readProperties file:'jenkins.properties'
                    returnParams.version = "${properties['version']}"
                    returnParams.buildVersion = setBuildVersion(returnParams)
                }
                else {
                    println("No build.gradle or jenkins.properties found.")
                }
            }
            // Set the gradle buildVersion as same as version, buildVersion is applicable only for maven currently
            returnParams.buildVersion = returnParams.version
            // Set the gradle commands. The -g ${JENKINS_HOME}/gradle_home ensures the 
            // gradle home and cache directory is located under the agent installation folder
            returnParams.buildCommand = userParms.get('buildCommand', 'gradle clean assemble -g ${JENKINS_HOME}/gradle_home')
            returnParams.testCommand = userParms.get('testCommand', 'gradle test -g ${JENKINS_HOME}/gradle_home')
            returnParams.sonarCommand = 'gradle sonarqube -g ${JENKINS_HOME}/gradle_home'
        break
        case "dotnet":
            if(fileExists('jenkins.properties')) {
                properties = readProperties file:'jenkins.properties'
                returnParams.version = "${properties['version']}"
                returnParams.buildVersion = setBuildVersion(returnParams)
            }
            returnParams.buildCommand = userParms.get('buildCommand', 'publish --no-restore -c Release')
            returnParams.testCommand = userParms.get('testCommand', 'dotnet test --no-restore -c Release --collect \"Code Coverage\" --logger trx --results-directory TestResults')
            // The file to analyze is specified in the convertResults function call which finds the unique .coverage file
      if (userParms.code_coverage_tool == 'VS2022' ){
                       returnParams.conversion = "\"${env.CODE_COVERAGE_NEW}\" analyze /output:TestResults\\DotnetCoverage.coveragexml"}
            else  {
                       returnParams.conversion = "\"${env.CODE_COVERAGE}\" analyze /output:TestResults\\DotnetCoverage.coveragexml"
             }
        break
        case "dotnetNuget":
            if(fileExists('jenkins.properties')) {
                properties = readProperties file:'jenkins.properties'
                returnParams.version = "${properties['version']}"
                returnParams.buildVersion = setBuildVersion(returnParams)
            }
            if(env.BRANCH_NAME == 'master') {
                returnParams.buildCommand = userParms.get('buildCommand', 'pack --no-restore -c Release')
            }
            else {
                returnParams.buildCommand = userParms.get('buildCommand', "pack --no-restore -c Release --version-suffix \"${env.BRANCH_NAME}-${env.BUILD_NUMBER}\"")
            }
            // Default the build tool to dotnet
            returnParams.buildTool = userParms.get('buildTool', 'dotnet')
            returnParams.testCommand = userParms.get('testCommand', 'dotnet test --no-restore -c Release --collect \"DotnetCodeCoverage\" --logger trx --results-directory TestResults')
            returnParams.conversion = "\"${env.CODE_COVERAGE}\" analyze /output:TestResults\\DotnetCoverage.coveragexml TestResults\\DotnetCoverage.coverage"
        break
        case "msbuild":
            if(fileExists('jenkins.properties')) {
                properties = readProperties file:'jenkins.properties'
                returnParams.version = "${properties['version']}"
                returnParams.buildVersion = setBuildVersion(returnParams)
            }
            returnParams.buildCommand = userParms.get('buildCommand', '-t:Clean,Build -p:Configuration=Release')
            returnParams.testCommand = userParms.get('testCommand', "\"${env.VSTEST_CONSOLE}\" /Logger:trx /Enablecodecoverage /ResultsDirectory:TestResults")
            // The file to analyze is specified in the convertResults function call which finds the unique .coverage file
            returnParams.conversion = "\"${env.CODE_COVERAGE}\" analyze /output:TestResults\\VisualStudio.coveragexml"
            returnParams.unitTestFiles = formatUnitTestFiles(userParms.get('unitTestFiles', null), returnParams.componentName)
        break
        case "biztalk":
            if(fileExists('jenkins.properties')) {
                properties = readProperties file:'jenkins.properties'
                returnParams.version = "${properties['version']}"
                returnParams.buildVersion = setBuildVersion(returnParams)
            }
            returnParams.buildCommand = userParms.get('buildCommand', '-t:Clean,Build -p:Configuration=Release')
            returnParams.packagecommand = userParms.get('packagecommand', '-t:Installer -p:Configuration=Release')
            returnParams.btdfpath = userParms.get('btdfpath', null)
            returnParams.testCommand = userParms.get('testCommand', "\"${env.VSTEST_CONSOLE}\" /Logger:trx /Enablecodecoverage /ResultsDirectory:TestResults")
            // The file to analyze is specified in the convertResults function call which finds the unique .coverage file
            returnParams.conversion = "\"${env.CODE_COVERAGE}\" analyze /output:TestResults\\VisualStudio.coveragexml"
            returnParams.unitTestFiles = formatUnitTestFiles(userParms.get('unitTestFiles', null), returnParams.componentName)
        break
        case "node":
            if(fileExists('jenkins.properties')) {
                properties = readProperties file:'jenkins.properties'
                returnParams.version = "${properties['version']}"
                returnParams.buildVersion = setBuildVersion(returnParams)
            }
            returnParams.buildCommand = userParms.get('buildCommand', 'node --max_old_space_size=8192 ./node_modules/.bin/ng build --prod=true --aot=true --vendorChunk=true --commonChunk=true --deleteOutputPath=true --buildOptimizer=true')
            returnParams.testCommand = userParms.get('testCommand', null)
            returnParams.folder = userParms.get('folder', 'dist')
        break
        case "xpression":
            if(fileExists('jenkins.properties')) {
                properties = readProperties file:'jenkins.properties'
                returnParams.version = "${properties['version']}"
                returnParams.buildVersion = setBuildVersion(returnParams)
                returnParams.folder = "${properties['folder']}"
            }
            // Set the template to the Ansible Tower template to be used
            returnParams.template = userParms.get('template', null)
            if(returnParams.template == null) {
                error("No template parameter provided in Jenkinsfile so cannot generate package. Please provide template in Jenkinsfile with template = \"<template_name>\"")
            }
        break
        case "anh":
            if(fileExists('jenkins.properties')) {
                properties = readProperties file:'jenkins.properties'
                returnParams.version = "${properties['USPackageVersion']}"
                returnParams.US_Package_Version = "${properties['USPackageVersion']}"
                returnParams.Current_Prod_Version = "${properties['CurrentProdVersion']}"
                returnParams.buildVersion = setBuildVersion(returnParams)
            }
        break
        case "onlyDeploy":
            if(fileExists('jenkins.properties')) {
                properties = readProperties file:'jenkins.properties'
                returnParams.artefact_url = "${properties['artefact_url']}"
                artefact_url_split = returnParams.artefact_url.split('/');
                returnParams.version = artefact_url_split.last()
                returnParams.buildVersion = artefact_url_split.last()
            }
        break
        case "ssis":
            if(fileExists('jenkins.properties')) {
                properties = readProperties file:'jenkins.properties'
                returnParams.version = "${properties['version']}"
                returnParams.buildVersion = setBuildVersion(returnParams)

                dtprojFileList = []

                // If we have a single dtprojFile configured, then we add that to the list
                dtprojFile=userParms.get('dtprojFile', null)
                projectConfiguration=userParms.get('projectConfiguration', null)
                if (dtprojFile != null) {
                    dtprojFileList << [dtprojFile: dtprojFile, projectConfiguration: projectConfiguration]
                }

                // Get dtprojFiles and add entry to the list
                dtprojFiles=userParms.get('dtprojFiles', null)
                if (dtprojFiles != null) {
                    for (entry in dtprojFiles) {
                        dtprojFileList << [dtprojFile: entry.dtprojFile, projectConfiguration: entry.projectConfiguration]
                    }
                }
                returnParams.dtprojFileList = dtprojFileList
            }
        break
        default:
            if(fileExists('jenkins.properties')) {
                properties = readProperties file:'jenkins.properties'
                returnParams.version = "${properties['version']}"
                returnParams.buildVersion = setBuildVersion(returnParams)
            }
            returnParams.testCommand = userParms.get('testCommand', null)
        break
    }

    // If we have no version then we cannot proceed with the build so error here
    if(returnParams.version == null  || returnParams.version == "null"  || returnParams.buildVersion == null) {
        error("Set pipeline parameters cannot find sufficient information for the version, please investigate and resolve.")
    }
  // If jenkins file have alphabet in build version
  //switch (returnParams.version) {
        //case ~/.*[a-z].*/: error "No alphabet allowed in Jenkins version, please investigate and resolve"; break;
        //default: println "Proceeding to further steps"
    //}
    return returnParams
}

def getGitRepoDetails() {
    def gitRepoDetails = [:]
    echo "before git config"
    command = 'git config --get remote.origin.url'
    echo "Git config Commandd: $command"
    gitName = executeShellCommand(command, 'true')
    echo "After Git Config"
    gitName = gitName.split('/')[1]
    gitName = gitName.split('\\.')
    //Set the project name, used in nexus
    projectName = gitName[0]
    //Set the component name, used in nexus
    componentName = gitName[1]
    //Set the repo name, used for sonar
    gitRepoDetails.repoName = "$projectName"
    if (componentName != "git") {
        gitRepoDetails.repoName = gitRepoDetails.repoName + ".$componentName"
    }
    gitRepoDetails.projectName = projectName
    if(gitName[1]) {
        gitRepoDetails.componentName = componentName
    }
    nexusPrefix = projectName.split('_')
    //Set the nexus prefix for the repository to use
    gitRepoDetails.nexusPrefix = nexusPrefix[0]
    //Return the values to be used
    return gitRepoDetails
}

//Adding below script to find commit user mail id
def getAuthorEmail(GIT_COMMIT) {
    def author_email
    if (isUnix()) {
        author_command = "git show -s --format=\"%ae\" ${GIT_COMMIT}"
  author_email = executeShellCommand(author_command, 'true')
    } else {
  author_command = "git show -s --format=\"%%ae\" ${GIT_COMMIT}"
        author_email_raw = executeShellCommand(author_command, 'true')
        author_email = author_email_raw.split("\r?\n").find { it.contains("@") }?.trim()  
    }
    echo "Author Email : ${author_email}"
    return author_email
}

def getBuildDirectory(directory) {
    new_directory = "${WORKSPACE}"
    if(directory != null){
        // We have a build directory to use so add that to the workspace
        // format correctly
        if(isUnix()) {
            new_directory = "${WORKSPACE}/" + directory
        } else {
            new_directory = "${WORKSPACE}\\" + directory
        }
    }

    return new_directory
}

def formatUnitTestFiles(unitTestFiles, componentName) {
    formatted = null

    // Prepend the unit test files with the target location
    if(unitTestFiles != null) {
        formatted = ''
        files = unitTestFiles.split(',')
        files.each { file ->
            formatted = formatted + componentName + "\\" + file.trim() + " "
        }
        formatted = formatted.trim()
    }

    return formatted

}

// Load build information from the develop branch
def loadDevelopInformation(Map params) {
    // Remove current Jenkinsfile as not develop branch
    if(fileExists("Jenkinsfile")) {
        if(isUnix()) {
            purgeCommand = "rm -f Jenkinsfile"
        } else {
            purgeCommand = "del /F /Q Jenkinsfile"
        }
        // Run the command
        executeShellCommand(purgeCommand)
    }

    // Checkout the Jenkinsfile from the develop branch, ignoring errors
    if(isUnix()) {
        sshagent(['15de0089-c9ab-4e83-9a16-7dee893bf7d0']) {
            // Fetch the develop metadata
            executeShellCommand("git fetch --no-tags --append --depth=1 origin develop:refs/remotes/origin/develop", 'false', 'true')
            // Checkout the single file
            executeShellCommand("git checkout origin/develop -- Jenkinsfile", 'false', 'true')
        }
    } else {
        // Get the key required to connect to the git server (deleted when build completes)
        withCredentials([sshUserPrivateKey(credentialsId: "15de0089-c9ab-4e83-9a16-7dee893bf7d0", keyFileVariable: 'keyfile')]) {
            // Set the ssh key to be used to authenticate
            executeShellCommand('git config core.sshCommand "ssh -i ${keyfile}"')
            executeShellCommand("git fetch --no-tags --append --depth=1 origin develop:refs/remotes/origin/develop", 'false', 'true')
            executeShellCommand("git checkout origin/develop -- Jenkinsfile", 'false', 'true')
            // Remove the key otherwise all further builds will fail with name not found and auth errors
            executeShellCommand('git config --unset-all core.sshCommand')
        }
    }

    // Set some defaults that can be overridden below
    params.productionBranches = ['master']
    params.protectedBranches = ['develop', 'release']
    params.devTemplates = []
    
    // Now we should have the develop version of the Jenkinsfile
    if(fileExists("Jenkinsfile")) {
        // Read in the Jenkinsfile from the develop branch
        def fileContents = readFile 'Jenkinsfile'

        // Iterate over line by line for the branch and template definitions
        def lines = findLines(fileContents)

        if(lines.productionBranches != null) {
            def productionBranches = processValues(lines.productionBranches)
            if(productionBranches.isEmpty()){
                echo "Production branches definition is incorrect, or returned an empty list."
                echo "Should read: productionBranches = 'branch1, branch2'"
                echo "Found: $lines.productionBranches"
            } else {
                params.productionBranches = productionBranches
                // Make sure develop is not in the production branch list
                if(params.productionBranches.contains('develop')){
                    println "develop cannot be a production branch, removing"
                    params.productionBranches.minus('develop')
                }
            }
        }

        if(lines.protectedBranches != null) {
            def protectedBranches = processValues(lines.protectedBranches)
            if(protectedBranches.isEmpty()){
                echo "Protected branches definition is incorrect, or returned an empty list."
                echo "Should read: protectedBranches = 'branch1, branch2'"
                echo "Found: $lines.protectedBranches"
            } else {
                params.protectedBranches = protectedBranches
                // Make sure develop is in the protected branch list
                if(!params.protectedBranches.contains('develop')){
                    println "develop must be a protected branch, adding"
                    params.protectedBranches.add('develop')
                }
            }
        }

        if(lines.devTemplates != null) {
            def devTemplates = processValues(lines.devTemplates)
            if(devTemplates.isEmpty()){
                echo "Development templates definition is incorrect, or returned an empty list."
                echo "Should read: devTemplates = 'template1, template2'"
                echo "Found: $lines.devTemplates"
            } else {
                params.devTemplates = devTemplates
            }
        }
    
        if(lines.suffixBuildNumberForProdBranch != null) {
            def suffixBuildNumberForProdBranch = processValues(lines.suffixBuildNumberForProdBranch)
            if(suffixBuildNumberForProdBranch.isEmpty()){
                echo "suffix Build Number For Prod Branch definition is incorrect, or returned an empty list."
                echo "Should read: suffixBuildNumberForProdBranch = 'true'"
                echo "Found: $lines.suffixBuildNumberForProdBranch"
            } else {
                params.suffixBuildNumberForProdBranch = suffixBuildNumberForProdBranch
            }
        }
    
        if(lines.overrideBranchInNexusVersion != null) {
            def overrideBranchInNexusVersion = processValues(lines.overrideBranchInNexusVersion)
            if(overrideBranchInNexusVersion.isEmpty()){
                echo "Override branch in Nexus version definition is incorrect, or returned an empty list."
                echo "Should read: overrideBranchInNexusVersion = 'true'"
                echo "Found: $lines.overrideBranchInNexusVersion"
            } else {
                params.overrideBranchInNexusVersion = overrideBranchInNexusVersion
            }
        }

        // Filter the deployments that were loaded in before coming into this function;
        // If this is production or protected branch then the deployments that match this branch name can be run as is, so nothing is required
        // If this is not a production or protected branch then the deployments can be run if ALL of them use a devTemplate.
        // If any do not then all deployments are rejected to prevent an invalid deployment topology advancing to the next stage.
        if(!params.productionBranches.contains(env.BRANCH_NAME.toLowerCase()) && !params.protectedBranches.contains(env.BRANCH_NAME.toLowerCase())) {
            // This is not a trusted branch and so each deployment must use a template that is part of the devTemplates or it will be rejected
            def deploymentsPermitted = true
            params.deploy.each { item ->
                if(item.branch == env.BRANCH_NAME.toLowerCase()) {
                    // Deployment for this branch
                    if(!params.devTemplates.contains(item.template)) {
                        // The template being requested is not a devTemplate and this branch is not protected and so this cannot be permitted
                        println "The deployment template requested, $item.template, is not permitted to be used as a devTemplate"
                        println "This will need adding in the develop Jenkinsfile first, the addition will require DevOps approval"
                        deploymentsPermitted = false
                    }
                }
            }
            // If the deployments are not permitted then remove them
            if(!deploymentsPermitted) {
                println "Invalid deployments discovered, no deployments will be permitted for this build"
                params.deploy = null
            }
        }
    } else {
        println "Unable to find Jenkinsfile in develop branch, using defaults, please investigate"
        // Ensure no deployments can occur as this is in a failure state where there could be no peer review
        params.deploy = null
    }

    return params
}

def processValues(String line){
    def valueList = []
    // Split line into two parts, before and after the = sign
    def lineParts = line.split('=')
    if(lineParts.length > 1){
        // The values are after the = sign, trim whitespace
        def values = lineParts[1].trim().replaceAll("'","").replaceAll(",\$", "")
        // Split the values into each item, removing whitespace
        valueList = convertStringListToListObject(values)
    }
    return valueList
}

// Split on the comma and then remove whitespace from each element in the resultant list
def convertStringListToListObject(String stringList) {
    def stringItems = stringList.split(',')
    def returnList = []
    for (i in 0..<stringItems.length) {
        returnList.add(stringItems[i].trim())
    }
    return returnList
}

// Disable the Jenkins CPS to allow correct iteration
@NonCPS
def findLines(String contents) {
    def returnLines = [:]
    returnLines.productionBranches = null
    returnLines.protectedBranches = null
    returnLines.devTemplates = null
    returnLines.suffixBuildNumberForProdBranch = null
    returnLines.overrideBranchInNexusVersion = null
    contents.eachLine { line,count ->
        if(line.contains('productionBranches')) {
            echo "Reading in productionBranches " + line
            returnLines.productionBranches = line
        } else if(line.contains('protectedBranches')) {
            echo "Reading in protectedBranches " + line
            returnLines.protectedBranches = line
        } else if (line.contains('devTemplates')) {
            echo "Reading in devTemplates " + line
            returnLines.devTemplates = line
        } else if (line.contains('suffixBuildNumberForProdBranch')) {
            echo "Reading in suffixBuildNumberForProdBranch " + line
            returnLines.suffixBuildNumberForProdBranch = line
        } else if (line.contains('overrideBranchInNexusVersion')) {
            echo "Reading in overrideBranchInNexusVersion " + line
            returnLines.overrideBranchInNexusVersion = line
        }
    }
    return returnLines
}
