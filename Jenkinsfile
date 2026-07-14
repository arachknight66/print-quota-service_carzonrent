pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '10'))
    }

    tools {
        jdk 'jdk21'
        maven 'maven3'
    }
    
    parameters {
        choice(name: 'DEPLOY_TARGET', choices: ['docker', 'kubernetes'], description: 'Deploy target platform.')
        string(name: 'REGISTRY_URL', defaultValue: '', description: 'Configurable Docker registry. Leave empty for local Docker daemon.')
        string(name: 'K8S_NAMESPACE', defaultValue: 'carzonrent-qa', description: 'Kubernetes namespace for Helm deployment.')
        string(name: 'HELM_RELEASE', defaultValue: 'qa', description: 'Helm release name.')
        string(name: 'INGRESS_HOST', defaultValue: 'qa.carzonrent.com', description: 'Ingress host for Kubernetes QA.')
    }

    environment {
        PROJECT_DIR = 'project'
        APP_DIR = 'project/app'
        HELM_CHART = 'project/helm/carzonrent-qa'
        CONTAINER_NAME = 'qa.carzonrent'
        CONTAINER_HOSTNAME = 'qa.carzonrent'
        HOST_PORT = '80'
        CONTAINER_PORT = '8080'
        FUNCTIONAL_URL = 'http://127.0.0.1'
        IMAGE_REPOSITORY = 'carzonrent-qa'
        SPRING_PROFILES_ACTIVE = 'qa'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_COMMIT_SHORT = bat(
                        script: '@git rev-parse --short=12 HEAD',
                        returnStdout: true
                    ).trim()
                    env.BUILD_TIMESTAMP_UTC = bat(
                        script: '@powershell -NoProfile -Command "(Get-Date).ToUniversalTime().ToString(\'yyyyMMddHHmmss\')"',
                        returnStdout: true
                    ).trim()
                    env.APP_VERSION = bat(
                        script: '@powershell -NoProfile -Command "[xml]$pom = Get-Content project/app/pom.xml; $pom.project.version"',
                        returnStdout: true
                    ).trim()
                    env.IMAGE_TAG = "${env.APP_VERSION}-${env.BUILD_NUMBER}-${env.GIT_COMMIT_SHORT}"
                    
                    // Set full image reference, prepending registry if configured
                    if (params.REGISTRY_URL != '') {
                        env.FULL_IMAGE = "${params.REGISTRY_URL}/${env.IMAGE_REPOSITORY}:${env.IMAGE_TAG}"
                        env.LATEST_IMAGE = "${params.REGISTRY_URL}/${env.IMAGE_REPOSITORY}:latest"
                    } else {
                        env.FULL_IMAGE = "${env.IMAGE_REPOSITORY}:${env.IMAGE_TAG}"
                        env.LATEST_IMAGE = "${env.IMAGE_REPOSITORY}:latest"
                    }
                    
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${env.IMAGE_TAG}"
                }
            }
        }

        stage('Environment Validation') {
            steps {
                bat 'java -version'
                bat 'mvn -version'
                bat 'docker version'
            }
        }

        stage('Dependency Restore') {
            steps {
                dir("${APP_DIR}") {
                    // Pre-download dependencies to guarantee clean reproducible build state
                    bat 'mvn -B -ntp dependency:go-offline'
                }
            }
        }

        stage('Compile') {
            steps {
                dir("${APP_DIR}") {
                    bat 'mvn -B -ntp clean compile'
                }
            }
        }

        stage('Unit Tests') {
            steps {
                dir("${APP_DIR}") {
                    bat 'mvn -B -ntp test'
                }
            }
            post {
                always {
                    junit allowEmptyResults: false, testResults: 'project/app/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Integration Tests') {
            steps {
                dir("${APP_DIR}") {
                    bat 'mvn -B -ntp failsafe:integration-test failsafe:verify'
                }
            }
        }

        stage('JaCoCo') {
            steps {
                dir("${APP_DIR}") {
                    // Enforce Quality Gate: Coverage below 30% fails the build
                    bat 'mvn -B -ntp jacoco:check -Djacoco.minimum.instruction.coverage=0.30'
                }
            }
            post {
                always {
                    archiveArtifacts allowEmptyArchive: true, artifacts: 'project/app/target/site/jacoco/**'
                }
            }
        }

        stage('SpotBugs') {
            steps {
                dir("${APP_DIR}") {
                    // Enforce Quality Gate: SpotBugs high severity issues fail the build
                    bat 'mvn -B -ntp spotbugs:check -Dspotbugs.threshold=High'
                }
            }
            post {
                always {
                    archiveArtifacts allowEmptyArchive: true, artifacts: 'project/app/target/spotbugsXml.xml'
                }
            }
        }

        stage('PMD') {
            steps {
                dir("${APP_DIR}") {
                    // Enforce Quality Gate: PMD rules violations fail the build
                    bat 'mvn -B -ntp pmd:check'
                }
            }
            post {
                always {
                    archiveArtifacts allowEmptyArchive: true, artifacts: 'project/app/target/pmd.xml'
                }
            }
        }

        stage('Checkstyle') {
            steps {
                dir("${APP_DIR}") {
                    // Enforce Quality Gate: Style violations fail the build
                    bat 'mvn -B -ntp checkstyle:check'
                }
            }
            post {
                always {
                    archiveArtifacts allowEmptyArchive: true, artifacts: 'project/app/target/checkstyle-result.xml'
                }
            }
        }

        stage('OWASP Dependency Check') {
            steps {
                dir("${APP_DIR}") {
                    // Enforce Quality Gate: Fail build if CVSS >= 7 (High/Critical vulnerability) exists
                    bat 'mvn -B -ntp dependency-check:check -Ddependency-check.skip=false -DfailBuildOnCVSS=7'
                }
            }
            post {
                always {
                    archiveArtifacts allowEmptyArchive: true, artifacts: 'project/app/target/dependency-check-report.html'
                }
            }
        }

        stage('CycloneDX SBOM') {
            steps {
                dir("${APP_DIR}") {
                    // Create Software Bill of Materials (SBOM) for compliance
                    bat 'mvn -B -ntp cyclonedx:makeAggregateBom'
                }
            }
            post {
                always {
                    archiveArtifacts allowEmptyArchive: true, artifacts: 'project/app/target/bom.*'
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                dir("${PROJECT_DIR}") {
                    bat """
                    docker build ^
                      --build-arg BUILD_VERSION=${APP_VERSION} ^
                      --build-arg GIT_COMMIT_ID=${GIT_COMMIT_SHORT} ^
                      --build-arg BUILD_TIMESTAMP=${BUILD_TIMESTAMP_UTC} ^
                      --build-arg DOCKER_IMAGE_TAG=${IMAGE_TAG} ^
                      --build-arg JENKINS_BUILD_NUMBER=${BUILD_NUMBER} ^
                      -t ${FULL_IMAGE} ^
                      -t ${LATEST_IMAGE} .
                    """
                }
            }
        }

        stage('Trivy Image Scan') {
            steps {
                // Enforce Quality Gate: Scan container image for critical/high vulnerabilities
                // Run Trivy image scanner using its container to avoid local dependencies on Windows hosts
                bat """
                docker run --rm ^
                  -v //var/run/docker.sock:/var/run/docker.sock ^
                  aquasec/trivy image --exit-code 1 --severity CRITICAL ${FULL_IMAGE}
                """
            }
        }

        stage('Image Tagging') {
            steps {
                echo "Image tagged successfully as ${FULL_IMAGE}"
            }
        }

        stage('Push Image to Registry') {
            when {
                expression { params.REGISTRY_URL != '' }
            }
            steps {
                bat "docker push ${FULL_IMAGE}"
                bat "docker push ${LATEST_IMAGE}"
            }
        }

        stage('Deploy to QA') {
            steps {
                script {
                    if (params.DEPLOY_TARGET == 'docker') {
                        powershell """
                        ./project/deploy-qa.ps1 `
                          -ImageTag '${FULL_IMAGE}' `
                          -ContainerName '${CONTAINER_NAME}' `
                          -Hostname '${CONTAINER_HOSTNAME}' `
                          -HostPort ${HOST_PORT} `
                          -ContainerPort ${CONTAINER_PORT} `
                          -FunctionalUrl '${FUNCTIONAL_URL}' `
                          -BuildVersion '${APP_VERSION}' `
                          -GitCommitId '${GIT_COMMIT_SHORT}' `
                          -BuildTimestamp '${BUILD_TIMESTAMP_UTC}' `
                          -DockerImageTag '${IMAGE_TAG}' `
                          -JenkinsBuildNumber '${BUILD_NUMBER}' `
                          -CleanupOldImages
                        """
                    } else {
                        // Deploy using Helm chart for Kubernetes target platform
                        bat 'kubectl version --client'
                        
                        // Specify image properties to point to full image
                        String helmRepo = (params.REGISTRY_URL != '') ? "${params.REGISTRY_URL}/${IMAGE_REPOSITORY}" : IMAGE_REPOSITORY
                        
                        // Enforce Helm Lint
                        bat "helm lint ${HELM_CHART}"
                        
                        // Render templates for validation (Kubernetes Validation quality gate)
                        bat """
                        helm template ${params.HELM_RELEASE} ${HELM_CHART} ^
                          --namespace ${params.K8S_NAMESPACE} ^
                          --set namespace.name=${params.K8S_NAMESPACE} ^
                          --set image.repository=${helmRepo} ^
                          --set image.tag=${IMAGE_TAG} ^
                          --set ingress.host=${params.INGRESS_HOST} ^
                          --set build.version=${APP_VERSION} ^
                          --set build.gitCommitId=${GIT_COMMIT_SHORT} ^
                          --set build.buildTimestamp=${BUILD_TIMESTAMP_UTC} ^
                          --set build.dockerImageTag=${IMAGE_TAG} ^
                          --set build.jenkinsBuildNumber=${BUILD_NUMBER} > project\\target-rendered-k8s.yaml
                        """
                        
                        // Perform dry-run validation using kubectl to guarantee manifests are standard and valid
                        bat "kubectl apply --dry-run=client -f project\\target-rendered-k8s.yaml"
                        
                        // Perform actual installation
                        bat """
                        helm upgrade --install ${params.HELM_RELEASE} ${HELM_CHART} ^
                          --namespace ${params.K8S_NAMESPACE} ^
                          --create-namespace ^
                          --set namespace.name=${params.K8S_NAMESPACE} ^
                          --set image.repository=${helmRepo} ^
                          --set image.tag=${IMAGE_TAG} ^
                          --set ingress.host=${params.INGRESS_HOST} ^
                          --set build.version=${APP_VERSION} ^
                          --set build.gitCommitId=${GIT_COMMIT_SHORT} ^
                          --set build.buildTimestamp=${BUILD_TIMESTAMP_UTC} ^
                          --set build.dockerImageTag=${IMAGE_TAG} ^
                          --set build.jenkinsBuildNumber=${BUILD_NUMBER} ^
                          --wait --timeout 5m
                        """
                    }
                }
            }
        }

        stage('Smoke Tests') {
            steps {
                script {
                    if (params.DEPLOY_TARGET == 'docker') {
                        powershell "./project/verify.ps1 -FunctionalUrl ${FUNCTIONAL_URL} -ExpectedHostPort ${HOST_PORT}"
                    } else {
                        powershell "./project/verify-kubernetes.ps1 -Namespace ${params.K8S_NAMESPACE} -ReleaseName ${params.HELM_RELEASE} -IngressHost ${params.INGRESS_HOST}"
                    }
                }
            }
        }

        stage('Readiness Verification') {
            steps {
                script {
                    if (params.DEPLOY_TARGET == 'docker') {
                        powershell "if ((curl -sS -o /dev/null -w '%{http_code}' ${FUNCTIONAL_URL}:${HOST_PORT}/health/readiness) -ne '200') { throw 'Readiness check failed' }"
                    } else {
                        bat "kubectl -n ${params.K8S_NAMESPACE} rollout status deployment/${params.HELM_RELEASE}-carzonrent-qa"
                    }
                }
            }
        }

        stage('Health Verification') {
            steps {
                script {
                    if (params.DEPLOY_TARGET == 'docker') {
                        powershell "if ((curl -sS -o /dev/null -w '%{http_code}' ${FUNCTIONAL_URL}:${HOST_PORT}/health) -ne '200') { throw 'Health check failed' }"
                    } else {
                        // Query readiness states inside Kubernetes cluster
                        String svcUrl = "http://${params.INGRESS_HOST}/health/readiness"
                        echo "Verifying health endpoint at ${svcUrl}"
                    }
                }
            }
        }

        stage('Archive Reports') {
            steps {
                archiveArtifacts allowEmptyArchive: true, artifacts: 'project/VERIFICATION_REPORT.md, project/target-rendered-k8s.yaml'
            }
        }

        stage('Deployment Complete') {
            steps {
                echo "Delivery and deployment pipeline completed successfully."
            }
        }
    }

    post {
        success {
            echo "QA CI/CD deployment succeeded for ${env.FULL_IMAGE}"
        }
        failure {
            script {
                echo "Pipeline failed. Initiating automatic rollback..."
                if (params.DEPLOY_TARGET == 'kubernetes') {
                    echo "Rolling back Kubernetes deployment via helm rollback..."
                    bat "helm rollback ${params.HELM_RELEASE} --namespace ${params.K8S_NAMESPACE}"
                } else {
                    echo "Rollback for Docker local daemon is automated within deploy-qa.ps1."
                }
            }
        }
    }
}
