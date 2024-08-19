javaPipeline {
    componentName = 'UI'
    jdk = 'java_1.11'
    nexusPrefix = 'DevOps'
    packaging = 'war'
    productionBranches = 'prodbranches, master'
    protectedBranches = 'release, develop-22, release-22, develop, prodbranches'
    //devTemplates = 'feature-onlyDeployPipeline, feature-jira-projectkey_issue, Windows.ConnectionTest.DEV.GLOBAL'
    suffixBuildNumberForProdBranch = 'true'
    overrideBranchInNexusVersion = 'true'
    //sonarQualityGateCheck = 'true'
    deploy = [
        [
            branch: 'develop',
            type: 'auto',
            template: 'Windows.ConnectionTest.DEV.GLOBAL',
            environment: 'Dev',
        ],
        [
            branch: 'develop',
            type: 'jira',
            template: 'Windows.ConnectionTest.DEV.GLOBAL',
            environment: 'Dev',
            project: 'DEPT',
            id: 'APAC' 
        ],
        [
            branch: 'release',
            type: 'jira',
            template: 'Windows.ConnectionTest.DEV.GLOBAL',
            environment: 'SIT',
            project: 'DEPT',
            id: 'APAC' 
        ],
        [
            branch: 'release',
            type: 'jira',
            template: 'Windows.ConnectionTest.DEV.GLOBAL',
            environment: 'UAT',
            project: 'DEPT',
            id: 'APAC'
        ],
        [
            branch: 'release',
            type: 'jira',
            template: 'Windows.ConnectionTest.DEV.GLOBAL',
            environment: 'UAT',
            project: 'DEPT',
            id: 'EMEA'
        ],
        [
            branch: 'master',
            type: 'jira',
            template: 'Windows.ConnectionTest.DEV.GLOBAL',
            environment: 'SIT',
            project: 'DEPT'
        ],
        [
            branch: 'master',
            type: 'jira',
            template: 'Windows.ConnectionTest.DEV.GLOBAL',
            environment: 'UAT',
            project: 'DEPT'
        ]    
        
    ]
}
