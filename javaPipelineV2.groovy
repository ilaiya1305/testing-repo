def call(body = false) {

    // evaluate the body block, and collect configuration into the object
    def customParams= [:]
    if(body) {
        body.resolveStrategy = Closure.DELEGATE_FIRST
        body.delegate = customParams
        body()
    }
    //Set the agent so we can delegate the task before calling functions
    //to set the remaining
    //Set some values, falling back to defaults if needed
    customParams.type = customParams.get('type', 'maven')
    def agentLabel = customParams.type + '&&java'

    

    pipeline {    
        agent { 
            label "${agentLabel}"
        }

        options {
            buildDiscarder(logRotator(numToKeepStr: '20'))
        }

        environment {
            def NEXUS_CREDENTIALS = credentials('f20cc06a-e94a-470a-8979-bf2f9cfd6552')
            def globalParams = setGlobalParams(customParams)
            def jdk = "${globalParams.jdk}"
            def maven = "${globalParams.maven}"
            def keepbuilds = "${globalParams.keepbuilds}"
            def BranchName = env.BRANCH_NAME.toLowerCase()
        }
    
        tools {
            maven "${maven}"
            gradle 'gradle'
            nodejs 'nodejs1810'
            jdk "${jdk}"
        }
        
        stages {    
        
            stage('Clean the workspace') {
                steps {
                    deleteDir()
                }
            }

            stage('Checkout') {
                steps {
                    script {
                        gitData = checkout scm
                        echo "Git data is ${gitData}"
                        warnError('WARNING: Non-permissible files committed in this repo branch..') {
                            executeShellCommand("python /opt/ci/jenkins/tools/list-non-permissible-files-UNIX.py ${WORKSPACE}")
                        }
                    }
                }
            }

            stage('Configuring Pipeline') {
                steps {
                    script {
                        pipelineParams = setPipelineParams(customParams)
                    }
                    outputPipelineParameters(pipelineParams)
                }
            }

            stage('Secrets scanning') {
                when { expression { pipelineParams.secretsScan == 'true' } }
                steps {
                    script {
                        gitleaks(pipelineParams)
                    }
                }
            }
            
            stage('Build') {
                steps {
                    dir(pipelineParams.buildDirectory){
                        executeShellCommand(pipelineParams.buildCommand)
                    }
                }
            }

            //Tests can be disabled by setting the testCommand to null
            stage('Unit test') {
                when { expression { pipelineParams.testCommand != null } }
                steps {
                    dir(pipelineParams.buildDirectory){
                        executeShellCommand(pipelineParams.testCommand)
                    }
                }
            }

            stage('SBOM Report-OWASP-dependency-check') {
                when { expression { pipelineParams.sbomdpcheckScan == 'true' }}
                steps {
                    dir(pipelineParams.buildDirectory) {
                        script {
                            // Catch errors to prevent the build from failing
                            catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                                // Setting JAVA HOME with JDK17, by default it picks JDK8 version, export option also not working, hence went with withEnv option.
                                withEnv(["JAVA_HOME=/opt/ci/jenkins/tools/hudson.model.JDK/java_1.17", "PATH=$JAVA_HOME/bin:$PATH"]) {
                                    
                                    // Run Dependency-Check
                                    dependencyCheck additionalArguments: '''
                                        --noupdate 
                                        --disableAssembly 
                                        --disableCentral 
                                        --disableRetireJS 
                                        --disableNodeAudit 
                                        --disableOssIndex 
                                        --prettyPrint 
                                        --format HTML
                                        --format XML
                                        --format JSON 
                                        --scan . 
                                        --exclude **/*.zip
                                        --log dependency-check.log
                                    ''', odcInstallation: 'owsap-dc'
                                    
                                    // Sleep for 3 seconds
                                    sleep(time: 3, unit: 'SECONDS')
                                    
                                    // Publish the Dependency-Check report
                                    dependencyCheckPublisher pattern: "**/dependency-check-report.xml"
                                }
                            }
                        }
                    }
                }
            }             

            stage('Set Quality Gate') {
                when { expression { pipelineParams.sonarQualityGateCheck == 'true' }
                      anyOf
                      {
                       branch 'develop*'
                       branch 'master*'
                      }   
                     }
                steps {
                    script {
                        withCredentials([usernamePassword(credentialsId: 'f20cc06a-e94a-470a-8979-bf2f9cfd6552', usernameVariable: 'USERID', passwordVariable: 'PASSWD')]) {
                            def qg_uri = env.SONAR_PRD_URL + "api/qualitygates/select?projectKey=${pipelineParams.repoName}.${env.BRANCH_NAME}&"
                            set_qg = "curl -u $USERID:$PASSWD -X POST  \"" + qg_uri + "\"gateId=AYO4BfLndeVjV-Cv6ktj"
                            executeShellCommand(set_qg)
                        }
                    }
                }
            }
            
            stage('SONAR Analyze') {
                when {
                    anyOf
                    {
                        branch 'develop*'
                        branch 'release*'
                        branch 'master*'
                        branch 'sonar*'
                    }
                }
                steps {
                     dir(pipelineParams.buildDirectory){
                         withSonarQubeEnv('sonar') {
                              executeShellCommand(pipelineParams.sonarCommand + " -Dsonar.projectKey=${pipelineParams.repoName}.${env.BRANCH_NAME}")
                        }
                    }
                }
            }  

            stage('SONAR Quality Gate Check') {
                when {
                        expression { pipelineParams.sonarQualityGateCheck == 'true' }
                      anyOf
                      {
                       branch 'develop*'
                       branch 'master*'
                      }  
                }                  
                steps {
                    script {
                        timeout(time: 30, unit: 'MINUTES') { 
                           def qg = waitForQualityGate()
                           if (qg.status != 'OK') {
                             error "Pipeline aborted due to SONARQUBE quality gate failure: ${qg.status}"
                           }
                        }
                    }     
                }
            }           
            
            //Appscan is only run when the branch name has appscan within it
            stage('IBM Static Code Analysis') {
                when { branch 'appscan*' }
                steps {
                    appscan application: "${pipelineParams.appscanID}", credentials: 'Appscan', failBuildNonCompliance: false, name: "${pipelineParams.repoName}.${env.BRANCH_NAME}", scanner: static_analyzer(hasOptions: false, target: "${WORKSPACE}"), type: 'Static Analyzer', wait: true
                }
            }

            stage('Upload to binary management platform') {
                when {
                    expression { pipelineParams.artefact != null }
                }
                steps {
                    script {
                        deploy_values = nexusUpload(pipelineParams, env.BRANCH_NAME, env.NEXUS_SERVER_URL, env.BUILD_NUMBER)
                    }
                }
            }

            // Only run a deployment if we have a binary to upload to nexus and we have a deployment entry
            stage('Deploy to target environments') {
                when { 
                    allOf {
                        expression { pipelineParams.artefact != null }
                        expression { pipelineParams.deploy != null }
                        expression { deploy_values != null }
                        expression { pipelineParams.skipDeploys == null }
                    }
                }
                steps {
                   deploy(pipelineParams, env.BRANCH_NAME, gitData, deploy_values)
                }
            }
        }

        //will run if email or teams notification is enabled   
        post {  
              always {  
                script {
                      notify(pipelineParams)
              }
              }
        }
    }
}
