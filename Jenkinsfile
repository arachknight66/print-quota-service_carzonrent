pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '30', artifactNumToKeepStr: '15'))
        parallelsAlwaysFailFast()
        skipDefaultCheckout(true)
    }

    parameters {
        booleanParam(name: 'DEPLOY_QA', defaultValue: true, description: 'Deploy the verified image to the existing Docker QA environment.')
        string(name: 'QA_CONTAINER_NAME', defaultValue: 'qa.carzonrent', description: 'Existing QA container name used by deployment and verification scripts.')
        string(name: 'QA_HOSTNAME', defaultValue: 'qa.carzonrent', description: 'Hostname assigned to the QA container.')
        string(name: 'QA_HOST_PORT', defaultValue: '80', description: 'Host port exposed for QA smoke tests.')
        string(name: 'QA_CONTAINER_PORT', defaultValue: '80', description: 'Container Apache port exposed by docker/app/Dockerfile.')
        string(name: 'QA_FUNCTIONAL_URL', defaultValue: 'http://127.0.0.1', description: 'Base URL used by existing QA smoke tests.')
        string(name: 'DOCKER_IMAGE_REPOSITORY', defaultValue: 'printkeep/print-quota-service', description: 'Local or registry image repository name.')
        string(name: 'DOCKER_REGISTRY_URL', defaultValue: '', description: 'Optional registry host. Leave blank for local Docker daemon only.')
    }

    environment {
        MAVEN_OPTS = '-Dmaven.repo.local=.m2/repository'
        MAVEN_CMD = '.\\mvnw.cmd'
        QA_REPORTS = 'project/VERIFICATION_REPORT.md'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_COMMIT_SHORT = bat(script: '@git rev-parse --short=12 HEAD', returnStdout: true).trim()
                    env.BUILD_TIMESTAMP_UTC = powershell(
                            script: "(Get-Date).ToUniversalTime().ToString('yyyyMMddHHmmss')",
                            returnStdout: true
                    ).trim()
                    env.PROJECT_VERSION = powershell(
                            script: "[xml]\$pom = Get-Content pom.xml; \$pom.project.version",
                            returnStdout: true
                    ).trim()
                    env.IMAGE_TAG = "${env.PROJECT_VERSION}-${env.BUILD_NUMBER}-${env.GIT_COMMIT_SHORT}"
                    String registryUrl = params.DOCKER_REGISTRY_URL == null ? '' : params.DOCKER_REGISTRY_URL.trim()
                    String imageRepository = params.DOCKER_IMAGE_REPOSITORY.trim()
                    env.IMAGE_REPOSITORY = registryUrl ? "${registryUrl}/${imageRepository}" : imageRepository
                    env.FULL_IMAGE = "${env.IMAGE_REPOSITORY}:${env.IMAGE_TAG}"
                    env.LATEST_IMAGE = "${env.IMAGE_REPOSITORY}:latest"
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${env.IMAGE_TAG}"
                }
            }
        }

        stage('Tool Verification') {
            steps {
                powershell """
                \$javaVersion = (& java -version 2>&1) -join "`n"
                Write-Host \$javaVersion
                if (\$javaVersion -notmatch 'version "21') {
                    throw 'JDK 21 is required for this pipeline.'
                }
                """
                bat "${MAVEN_CMD} -version"
                bat 'docker version'
            }
        }

        stage('Dependency Cache') {
            steps {
                bat "${MAVEN_CMD} -B -ntp dependency:go-offline"
            }
        }

        stage('Compile') {
            steps {
                bat "${MAVEN_CMD} -B -ntp clean compile"
            }
        }

        stage('Unit Tests') {
            steps {
                bat "${MAVEN_CMD} -B -ntp test"
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Integration Tests') {
            steps {
                bat "${MAVEN_CMD} -B -ntp failsafe:integration-test failsafe:verify"
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/failsafe-reports/*.xml'
                }
            }
        }

        stage('Quality Gates') {
            parallel {
                stage('Checkstyle') {
                    steps {
                        bat "${MAVEN_CMD} -B -ntp checkstyle:check"
                    }
                    post {
                        always {
                            archiveArtifacts allowEmptyArchive: true, artifacts: '**/target/checkstyle-*.xml, **/target/checkstyle-result.xml'
                        }
                    }
                }

                stage('PMD') {
                    steps {
                        bat "${MAVEN_CMD} -B -ntp pmd:check"
                    }
                    post {
                        always {
                            archiveArtifacts allowEmptyArchive: true, artifacts: '**/target/pmd.xml'
                        }
                    }
                }

                stage('SpotBugs') {
                    steps {
                        bat "${MAVEN_CMD} -B -ntp spotbugs:check"
                    }
                    post {
                        always {
                            archiveArtifacts allowEmptyArchive: true, artifacts: '**/target/spotbugsXml.xml, **/target/spotbugs.html'
                        }
                    }
                }
            }
        }

        stage('JaCoCo') {
            steps {
                bat "${MAVEN_CMD} -B -ntp jacoco:report jacoco:check"
            }
            post {
                always {
                    archiveArtifacts allowEmptyArchive: true, artifacts: '**/target/site/jacoco/**, **/target/jacoco.exec'
                }
            }
        }

        stage('SBOM') {
            steps {
                bat "${MAVEN_CMD} -B -ntp cyclonedx:makeAggregateBom"
            }
            post {
                always {
                    archiveArtifacts allowEmptyArchive: true, artifacts: '**/target/cyclonedx/**, **/target/bom.*'
                }
            }
        }

        stage('Package') {
            steps {
                bat "${MAVEN_CMD} -B -ntp -DskipTests package"
            }
            post {
                always {
                    archiveArtifacts allowEmptyArchive: true, fingerprint: true, artifacts: 'ipp-codec/target/*.jar, print-quota-core/target/*.jar, !**/*.jar.original'
                }
            }
        }

        stage('Docker Build') {
            steps {
                bat """
                docker build ^
                  --pull ^
                  --label org.opencontainers.image.revision=${GIT_COMMIT_SHORT} ^
                  --label org.opencontainers.image.version=${PROJECT_VERSION} ^
                  --label org.opencontainers.image.created=${BUILD_TIMESTAMP_UTC} ^
                  -f docker/app/Dockerfile ^
                  -t ${FULL_IMAGE} ^
                  -t ${LATEST_IMAGE} .
                """
            }
        }

        stage('Deploy QA') {
            when {
                expression { return params.DEPLOY_QA }
            }
            environment {
                DB_PASSWORD = credentials('printkeep-qa-db-password')
                LDAP_BIND_PASSWORD = credentials('printkeep-qa-ldap-bind-password')
                LDAP_ADMIN_PASSWORD = credentials('printkeep-qa-ldap-admin-password')
                PGADMIN_PASSWORD = credentials('printkeep-qa-pgadmin-password')
            }
            steps {
                powershell """
                ./project/deploy-qa.ps1 `
                  -ImageTag '${FULL_IMAGE}' `
                  -ContainerName '${params.QA_CONTAINER_NAME}' `
                  -Hostname '${params.QA_HOSTNAME}' `
                  -HostPort ${params.QA_HOST_PORT} `
                  -ContainerPort ${params.QA_CONTAINER_PORT} `
                  -FunctionalUrl '${params.QA_FUNCTIONAL_URL}' `
                  -BuildVersion '${PROJECT_VERSION}' `
                  -GitCommitId '${GIT_COMMIT_SHORT}' `
                  -BuildTimestamp '${BUILD_TIMESTAMP_UTC}' `
                  -DockerImageTag '${IMAGE_TAG}' `
                  -JenkinsBuildNumber '${BUILD_NUMBER}' `
                  -CleanupOldImages
                """
            }
        }

        stage('Smoke Tests') {
            when {
                expression { return params.DEPLOY_QA }
            }
            steps {
                powershell """
                ./project/verify.ps1 `
                  -ContainerName '${params.QA_CONTAINER_NAME}' `
                  -FunctionalUrl '${params.QA_FUNCTIONAL_URL}' `
                  -ExpectedHostPort ${params.QA_HOST_PORT} `
                  -ExpectedContainerPort ${params.QA_CONTAINER_PORT}
                """
            }
        }
    }

    post {
        always {
            archiveArtifacts allowEmptyArchive: true, fingerprint: true, artifacts: "**/target/surefire-reports/**, **/target/failsafe-reports/**, **/target/site/**, **/target/cyclonedx/**, **/target/*.xml, ${QA_REPORTS}"
            cleanWs(deleteDirs: true, disableDeferredWipeout: true, notFailBuild: true)
        }
        success {
            echo "PrintKeep CI/CD completed successfully for ${env.FULL_IMAGE}."
        }
        failure {
            script {
                if (params.DEPLOY_QA) {
                    echo 'Pipeline failed. QA deployment rollback is handled by project/deploy-qa.ps1 when candidate deployment starts.'
                }
            }
        }
    }
}
