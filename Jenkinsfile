pipeline {
    agent any 
    stages {
        stage('Example Build') {
            steps {
                echo 'Hello, Maven'
                sh 'pwd'
            }
        }
        stage('Example Test') {
            //agent { docker 'openjdk:8-jre' } 
            steps {
                echo 'Hello, JDK'
                sh 'java -version'
            }
        }
        stage('Example Deploy') {
            //agent { docker 'openjdk:8-jre' } 
            steps {
                echo 'Hello, Deploy'
                sh 'java -version'
            }
        }
    }
}
