pipeline {
    agent any

    environment {
        AWS_REGION = 'eu-west-1'
        EB_APPLICATION = 'biasharahub-backend'
        EB_ENVIRONMENT = 'biasharahub-uat'
        S3_BUCKET = 'elasticbeanstalk-eu-west-1-487123916339'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }

        // Test stage uses the UAT test DB and Redis. In Jenkins job config set:
        // DB_URL, DB_USERNAME, DB_PASSWORD (UAT PostgreSQL), REDIS_HOST, REDIS_PORT (UAT Redis).
        stage('Test') {
            steps {
                sh 'mvn test -Dspring.profiles.active=test'
            }
        }

        stage('Package') {
            steps {
                sh '''
                    cp target/biasharahub-backend-1.0.0-SNAPSHOT.jar .
                    zip -r deploy.zip biasharahub-backend-1.0.0-SNAPSHOT.jar
                '''
            }
        }

        stage('Deploy to Elastic Beanstalk') {
            steps {
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-credentials']]) {
                    sh '''
                        VERSION_LABEL="build-${BUILD_NUMBER}-$(date +%Y%m%d%H%M%S)"
                        aws s3 cp deploy.zip s3://${S3_BUCKET}/deploy-${VERSION_LABEL}.zip --region ${AWS_REGION}
                        aws elasticbeanstalk create-application-version --application-name ${EB_APPLICATION} --version-label ${VERSION_LABEL} --source-bundle S3Bucket=${S3_BUCKET},S3Key=deploy-${VERSION_LABEL}.zip --region ${AWS_REGION}
                        aws elasticbeanstalk update-environment --environment-name ${EB_ENVIRONMENT} --version-label ${VERSION_LABEL} --region ${AWS_REGION}
                    '''
                }
            }
        }
    }

    post {
        failure { echo 'Pipeline failed!' }
        success { echo 'Pipeline succeeded!' }
    }
}
