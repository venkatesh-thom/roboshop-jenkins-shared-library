def call(Map configMap){
    pipeline {
    // These are pre-build sections
        agent {
            node {
                label 'AGENT-1'
            }
        }
        tools {
        // This must match the name you set in Step 1
        maven 'maven' 
        }
        environment {
            COURSE = "Jenkins"
            appVersion = ""
            ACC_ID = "441700732169"
            PROJECT = configMap.get("project")
            COMPONENT = configMap.get("component")
            SUBDIR = "robo-component-manifests/${COMPONENT}-ci"
        }
        options {
            timeout(time: 10, unit: 'MINUTES') 
            disableConcurrentBuilds()
        }
        // This is build section
        stages {
            stage('Read Version') {
                steps {
                    script{
                        dir("${env.SUBDIR}") {
                            def pom = readMavenPom file: 'pom.xml'
                            appVersion = pom.version
                            echo "app version: ${appVersion}"  
                        }    
                    }
                }
            }
            stage('Install Dependencies') {
                steps {
                    script{
                        dir("${env.SUBDIR}") {
                            sh """
                                mvn clean package
                            """
                        }
                    }
                }
            }
            stage('Unit Test') {
                steps {
                    script{
                        dir("${env.SUBDIR}") {
                            sh """
                                echo test
                            """
                        }    
                    }
                }
            }
            //Here you need to select scanner tool and send the analysis to server
            // stage('Sonar Scan'){
            //     environment {
            //         def scannerHome = tool 'sonar-8.0'
            //     }
            //     steps {
            //         script{
            //             withSonarQubeEnv('sonar-server') {
            //                 dir("${env.SUBDIR}") {
            //                     sh """
            //                     "${scannerHome}/bin/sonar-scanner"
            //                     """
            //                 }    
            //             }
            //         }
            //     }
            // }
            // stage('Quality Gate') {
            //     steps {
            //         timeout(time: 1, unit: 'HOURS') {
            //             // Wait for the quality gate status
            //             // abortPipeline: true will fail the Jenkins job if the quality gate is 'FAILED'
            //             waitForQualityGate abortPipeline: true 
            //         }
            //     }
            // }
            // stage('Dependabot Security Gate') {
            //     environment {
            //         GITHUB_OWNER = 'sathishdevops38'
            //         GITHUB_REPO  = 'roboshop-components-ci-cd'
            //         GITHUB_API   = 'https://api.github.com'
            //         // This creates a shell variable named github_auth
            //         github_auth  = credentials('github_auth') 
            //     }

            //     steps {
            //         script {
            //             dir("${env.SUBDIR}") {
            //                 sh """
            //                     echo "Fetching Dependabot alerts for repo: ${env.GITHUB_OWNER}/${env.GITHUB_REPO}..."

            //                     # 1. Fetch alerts (Using double quotes allows ${github_auth} to be injected)
            //                     response=\$(curl -s \
            //                         -H "Authorization: token ${github_auth}" \
            //                         -H "Accept: application/vnd.github+json" \
            //                         "${env.GITHUB_API}/repos/${env.GITHUB_OWNER}/${env.GITHUB_REPO}/dependabot/alerts?per_page=100&state=open")

            //                     # 2. Check for API errors (Bad credentials / 404)
            //                     if echo "\$response" | grep -q "message"; then
            //                         echo "❌ API Error Detected:"
            //                         echo "\$response" | jq -r .message
            //                         exit 1
            //                     fi

            //                     # 3. Filter by Severity AND Subfolder (${COMPONENT})
            //                     # Use --arg to pass the folder name into jq safely
            //                     high_critical_open_count=\$(echo "\$response" | jq --arg SUBFOLDER "${COMPONENT}-ci/" '[.[] 
            //                         | select(
            //                             .state == "open"
            //                             and (.security_advisory.severity == "high" or .security_advisory.severity == "critical")
            //                             and (.dependency.manifest_path | contains(\$SUBFOLDER))
            //                         )
            //                     ] | length')

            //                     echo "Open HIGH/CRITICAL alerts in ${COMPONENT}-ci/: \$high_critical_open_count"

            //                     # 4. Conditional Logic
            //                     if [ "\$high_critical_open_count" -gt 0 ]; then
            //                         echo "❌ Blocking pipeline due to OPEN HIGH/CRITICAL Dependabot alerts in ${COMPONENT}-ci/"
            //                         echo "Affected dependencies:"
                                    
            //                         echo "\$response" | jq --arg SUBFOLDER "${COMPONENT}-ci/" '.[] 
            //                         | select(.state=="open" 
            //                             and (.security_advisory.severity=="high" or .security_advisory.severity=="critical")
            //                             and (.dependency.manifest_path | contains(\$SUBFOLDER))
            //                         )
            //                         | {
            //                             package: .dependency.package.name, 
            //                             severity: .security_advisory.severity, 
            //                             manifest: .dependency.manifest_path,
            //                             summary: .security_advisory.summary
            //                         }'
            //                         exit 1
            //                     else
            //                         echo "✅ No OPEN HIGH/CRITICAL Dependabot alerts found in ${COMPONENT}-ci/"
            //                     fi
            //                 """
            //             }    
            //         }
            // }
            // }
            stage('Build Image') {
                steps {
                    script{
                        withAWS(region:'us-west-2',credentials:'aws-creds') {
                            dir("${env.SUBDIR}") {
                                sh """
                                    aws ecr get-login-password --region us-west-2 | docker login --username AWS --password-stdin ${ACC_ID}.dkr.ecr.us-west-2.amazonaws.com
                                    docker build -t ${ACC_ID}.dkr.ecr.us-west-2.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion} .
                                    docker images
                                    docker push ${ACC_ID}.dkr.ecr.us-west-2.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion}
                                """
                            }    
                        }
                    }
                }
            }
            // stage('Trivy Scan'){
            //     steps {
            //         script{
            //             sh """
            //                 trivy image \
            //                 --scanners vuln \
            //                 --severity HIGH,CRITICAL,MEDIUM \
            //                 --pkg-types os \
            //                 --exit-code 1 \
            //                 --format table \
            //                 ${ACC_ID}.dkr.ecr.us-west-2.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion}
            //             """
            //         }
            //     }
            // }
            // stage('Trigger DEV Deploy') {
            //     steps {
            //         script {
            //             build job: "../${COMPONENT}-deploy",
            //                 wait: false, // Wait for completion
            //                 propagate: false, // Propagate status
            //                 parameters: [
            //                     string(name: 'appVersion', value: "${appVersion}"),
            //                     string(name: 'deploy_to', value: "dev")
            //                 ]
            //         }
            //     }
            // }
        }
        post{
            always{
                echo 'I will always say Hello again!'
                cleanWs()
            }
            success {
                echo 'I will run if success'
            }
            failure {
                echo 'I will run if failure'
            }
            aborted {
                echo 'pipeline is aborted'
            }
        }

    } 
} 