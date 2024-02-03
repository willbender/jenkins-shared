def call(Map arguments=[:]) {
    pipeline {
        //Environment variables for the docker registry
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
                        reuseNode true
                    }
                }
                steps {
                    sh 'npm test' 
                }
            }
            //Scan docker image vulnerabilities
            stage('Dockerfile Linting'){
                agent any
                steps{
                    script{
                        def vulnerabilities = sh(script: "docker run --rm -i hadolint/hadolint < Dockerfile", returnStdout: true).trim()
                        echo "Docker Linting Report:\n${vulnerabilities}"
                    }
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
            //Scan docker image vulnerabilities
            stage('Scan Docker Image for Vulnerabilities'){
                agent any
                steps{
                    script{
                        sh 'docker run -v /var/run/docker.sock:/var/run/docker.sock -v library-cache:/root/.cache/ aquasec/trivy:0.49.0 image --format template --template "@contrib/html.tpl" -o vulnerability-report.html --exit-code 0 --severity HIGH,MEDIUM,LOW --no-progress ${registry}/node${env.BRANCH_NAME}:1.0'
                        archiveArtifacts artifacts: 'vulnerability-report.html'
                    }
                }
            }
        }
    }
}
