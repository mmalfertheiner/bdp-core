pipeline {
    agent {
        dockerfile {
            filename 'docker/dockerfile-java'
            additionalBuildArgs '--build-arg JENKINS_USER_ID=`id -u jenkins` --build-arg JENKINS_GROUP_ID=`id -g jenkins`'
        }
    }

    environment {
        TESTSERVER_TOMCAT_ENDPOINT = credentials('testserver-tomcat8-url')
        TESTSERVER_TOMCAT_CREDENTIALS = credentials('testserver-tomcat8-credentials')

        BDP_DATABASE_SCHEMA = "intimev2"
        BDP_DATABASE_HOST = "test-pg-bdp.co90ybcr8iim.eu-west-1.rds.amazonaws.com"
        BDP_DATABASE_PORT = "5432"
        BDP_DATABASE_NAME = "bdp"
        BDP_DATABASE_READ_USER = "bdp_readonly"
        BDP_DATABASE_READ_PASSWORD = credentials('bdp-core-test-database-read-password')
        BDP_DATABASE_WRITE_USER = "bdp"
        BDP_DATABASE_WRITE_PASSWORD = credentials('bdp-core-test-database-write-password')
        BDP_READER_JWT_SECRET = credentials('bdp-core-test-reader-jwt-secret')
    }
    parameters{
        string(name:'bdp_version',defaultValue:'x.y.z',description:'version of dependencies to use in test deployment(must be released)');
        choice(name:'bdp_type',choices:['snapshot','release'],description:'use production ready releases or snapshots')
    }
    stages {
        stage('Configure') {
            steps {
                sh 'sed -i -e "s/<\\/settings>$//g\" ~/.m2/settings.xml'
                sh 'echo "    <servers>" >> ~/.m2/settings.xml'
                sh 'echo "        ${TESTSERVER_TOMCAT_CREDENTIALS}" >> ~/.m2/settings.xml'
                sh 'echo "    </servers>" >> ~/.m2/settings.xml'
                sh 'echo "</settings>" >> ~/.m2/settings.xml'

                // Configure DAL - API v1
                sh 'cp dal/src/main/resources/META-INF/persistence.xml.dist dal/src/main/resources/META-INF/persistence.xml'
                sh '''xmlstarlet ed -L -u "//_:persistence-unit/_:properties/_:property[@name='hibernate.default_schema']/@value" -v ${BDP_DATABASE_SCHEMA} dal/src/main/resources/META-INF/persistence.xml'''
                sh '''xmlstarlet ed -L -u "//_:persistence-unit/_:properties/_:property[@name='hibernate.hikari.dataSource.serverName']/@value" -v ${BDP_DATABASE_HOST} dal/src/main/resources/META-INF/persistence.xml'''
                sh '''xmlstarlet ed -L -u "//_:persistence-unit/_:properties/_:property[@name='hibernate.hikari.dataSource.portNumber']/@value" -v ${BDP_DATABASE_PORT} dal/src/main/resources/META-INF/persistence.xml'''
                sh '''xmlstarlet ed -L -u "//_:persistence-unit/_:properties/_:property[@name='hibernate.hikari.dataSource.databaseName']/@value" -v ${BDP_DATABASE_NAME} dal/src/main/resources/META-INF/persistence.xml'''
                sh '''xmlstarlet ed -L -u "//_:persistence-unit[@name='jpa-persistence']/_:properties/_:property[@name='hibernate.hikari.dataSource.user']/@value" -v ${BDP_DATABASE_READ_USER} dal/src/main/resources/META-INF/persistence.xml'''
                sh '''xmlstarlet ed -L -u "//_:persistence-unit[@name='jpa-persistence']/_:properties/_:property[@name='hibernate.hikari.dataSource.password']/@value" -v ${BDP_DATABASE_READ_PASSWORD} dal/src/main/resources/META-INF/persistence.xml'''
                sh '''xmlstarlet ed -L -u "//_:persistence-unit[@name='jpa-persistence-write']/_:properties/_:property[@name='hibernate.hikari.dataSource.user']/@value" -v ${BDP_DATABASE_WRITE_USER} dal/src/main/resources/META-INF/persistence.xml'''
                sh '''xmlstarlet ed -L -u "//_:persistence-unit[@name='jpa-persistence-write']/_:properties/_:property[@name='hibernate.hikari.dataSource.password']/@value" -v ${BDP_DATABASE_WRITE_PASSWORD} dal/src/main/resources/META-INF/persistence.xml'''

                sh 'sed -i -e "s%\\(jwt.secret\\s*=\\).*\\$%\\1${BDP_READER_JWT_SECRET}%" reader/src/main/resources/META-INF/spring/application.properties'

                sh 'sed -i -e "s%\\(log4j.rootLogger\\s*=\\).*\\$%\\1DEBUG,R%" reader/src/main/resources/log4j.properties'
                sh 'sed -i -e "s%\\(log4j.rootLogger\\s*=\\).*\\$%\\1DEBUG,R%" writer/src/main/resources/log4j.properties'
                sh 'sed -i -e "s%\\(log4j.rootLogger\\s*=\\).*\\$%\\1DEBUG,R%" dal/src/main/resources/log4j.properties'
                sh "./quickrelease.sh '${params.bdp_type}' '${params.bdp_version}'"
            }
        }
        stage('Install') {
            steps {
                sh 'cd dal && mvn -B -U clean test install'
            }
        }
        stage('Build - Reader') {
            steps {
                sh 'cd reader && mvn -B -U clean test package'
            }
        }
        stage('Build - Writer') {
            steps {
                sh 'cd writer && mvn -B -U clean test package'
            }
        }
        stage('Deploy - Reader') {
            steps {
                sh 'cd reader && mvn -B -DskipTests tomcat:redeploy -Dmaven.tomcat.url=${TESTSERVER_TOMCAT_ENDPOINT} -Dmaven.tomcat.server=testServer'
            }
        }
        stage('Deploy - Writer') {
            steps {
                sh 'cd writer && mvn -B -DskipTests tomcat:redeploy -Dmaven.tomcat.url=${TESTSERVER_TOMCAT_ENDPOINT} -Dmaven.tomcat.server=testServer'
            }
        }
    }
}
