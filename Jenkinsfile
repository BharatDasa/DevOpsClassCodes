pipeline {
    agent any
  tools {
    jdk 'myjava'
    maven 'Mymaven'
  }
  stages {
      stage('Clonerepo') {
         steps {
            git 'https://github.com/bhasker-manikyala/DevOpsClassCodes.git'
         }
      }
      stage('Compile') {
         steps {
            sh 'mvn compile'
         }
      }
      stage('CodeReview') {
         steps {
            sh 'mvn pmd:pmd'
         }
          post {
              success {
                  recordIssues sourceCodeRetention: 'LAST_BUILD', tools: [pmdParser(pattern: 'target/pmd.xml')]
              }
          }
      }
      stage('UnitTesting') {
         steps {
            sh 'mvn test'
         }
      }

  }
}
