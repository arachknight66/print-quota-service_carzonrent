pipeline {
    agent any

    tools {
        jdk 'jdk21'
        maven 'maven3'
    }

    environment {
        APP_DIR = '/opt/print-quota'
        TARGET_VM = 'centos-user@centos-vm-ip' // Deploy VM Address
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Verify') {
            steps {
                // Runs compilation, tests, checkstyle, PMD, SpotBugs, and JaCoCo coverage
                sh 'mvn clean verify'
            }
        }

        stage('Deploy') {
            steps {
                // Uses Jenkins credentials plugin for SSH credentials
                sshagent(['centos-vm-ssh-credentials']) {
                    // Create app directory if not exists
                    sh "ssh ${TARGET_VM} 'sudo mkdir -p ${APP_DIR} && sudo chown -R centos-user:centos-user ${APP_DIR}'"
                    
                    // Copy executable jar from print-quota-core module
                    sh "scp print-quota-core/target/print-quota-core-1.0.0.jar ${TARGET_VM}:${APP_DIR}/print-quota-service.jar"
                    
                    // Copy systemd service file
                    sh "scp deployment/print-quota.service ${TARGET_VM}:/tmp/print-quota.service"
                    sh "ssh ${TARGET_VM} 'sudo mv /tmp/print-quota.service /etc/systemd/system/print-quota.service && sudo systemctl daemon-reload'"
                    
                    // Restart service
                    sh "ssh ${TARGET_VM} 'sudo systemctl restart print-quota.service && sudo systemctl enable print-quota.service'"
                }
            }
        }
    }
    
    post {
        success {
            echo "Print Quota Management System build and verification completed successfully."
        }
        failure {
            echo "Pipeline failed. Check build logs."
        }
    }
}
