def call(Map arguments=[:]) {
    pipeline {

      environment{
          registry = "willbender"
          registryCredential = 'dockerhub'
      }
      
      agent none
      stages {
          //Build stage to build the app
          stage('Build') { 
              agent  {
                  docker {
                      image 'node:7.8.0' 
                      args '-p 3000:3000'
                      reuseNode true
                  }
              }
              steps {
                  sh 'npm install' 
              }
          }
          //Test stage to run tests for the app
          stage('Test') { 
              agent  {
                  docker {
                      image 'node:7.8.0' 
                      args '-p 3000:3000'
                      reuseNode true
                  }
              }
              steps {
                  sh 'npm test' 
              }
          }
          //Build stage to build the docker images
          stage('Docker Build') { 
              agent  any
              steps {
                  script{
                      def image = docker.build registry + "/node${env.BRANCH_NAME}:1.0"
                      docker.withRegistry('', registryCredential){
                          image.push()
                      }
                  }
              }
          }
          //Deploy stage to run the docker images
          stage('Deploy') { 
              agent  any
              steps {
                  build job: 'CD_deploy', wait: true, parameters: [string(name: 'IMAGE_TAG', value: '1.0'), string(name: 'TARGET_ENVIRONMENT', value: env.BRANCH_NAME)]
              }
          }
      }
  }
}
