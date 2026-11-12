pipeline {
    agent any

    // Los nombres deben coincidir con Manage Jenkins -> Tools.
    // JDK 17: el mismo que desarrollo y producción (Dockerfile); Lombok 1.18.26 no compila con JDK 21.
    tools {
        jdk 'jdk17'
    }

    environment {
        // Flutter no viene en la imagen de Jenkins: se descarga una vez en el volumen jenkins_home (misma versión que en desarrollo)
        FLUTTER_VERSION = '3.47.6'
        FLUTTER_HOME = "/var/jenkins_home/flutter-${FLUTTER_VERSION}"
        SEMGREP = '/var/jenkins_home/semgrep-venv/bin/semgrep'
    }

    options {
        timestamps()
        timeout(time: 60, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '15'))
        disableConcurrentBuilds()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Backend: compilar y probar') {
            steps {
                // Compila (Java 17 sobre JDK 21), ejecuta las pruebas (JUnit) y genera la cobertura (JaCoCo).
                // Las pruebas usan H2 en memoria y Redis simulado: no necesitan servicios externos.
                sh 'chmod +x gradlew && ./gradlew --no-daemon --console=plain clean test'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'build/test-results/test/*.xml'
                    recordCoverage tools: [[parser: 'JACOCO', pattern: 'build/reports/jacoco/test/jacocoTestReport.xml']], sourceCodeRetention: 'NEVER'
                }
            }
        }

        stage('App Flutter: análisis y pruebas') {
            steps {
                dir('app_flutter') {
                    sh '''
                        if [ ! -x "$FLUTTER_HOME/bin/flutter" ]; then
                          git clone --depth 1 --branch "$FLUTTER_VERSION" https://github.com/flutter/flutter.git "$FLUTTER_HOME"
                        fi
                        export PATH="$FLUTTER_HOME/bin:$PATH"
                        flutter config --no-analytics --no-cli-animations > /dev/null
                        flutter --version
                        flutter pub get
                        flutter analyze
                        flutter test --reporter expanded
                    '''
                }
            }
        }

        stage('Análisis SonarQube') {
            steps {
                // Requiere la credencial "sonar-token" (Secret text) en Jenkins.
                // sonar.qualitygate.wait=true hace fallar la etapa si no se cumple el Quality Gate.
                withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                    sh './gradlew --no-daemon --console=plain sonar -Dsonar.host.url=http://sonarqube:9000 -Dsonar.token=$SONAR_TOKEN -Dsonar.qualitygate.wait=true'
                }
            }
        }

        stage('Análisis Semgrep (OWASP)') {
            steps {
                // Reglas OWASP Top 10, Java y detección de secretos sobre el backend, la app Flutter y los scripts.
                // --error hace fallar la etapa si encuentra hallazgos; el reporte JSON queda como artefacto.
                sh '''
                    "$SEMGREP" scan --config p/owasp-top-ten --config p/java --config p/secrets \
                      --metrics=off --error \
                      --exclude build --exclude .dart_tool \
                      --json-output=semgrep-report.json \
                      src app_flutter/lib app_flutter/web scripts build.gradle
                '''
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'semgrep-report.json, build/reports/jacoco/**, build/reports/tests/**', allowEmptyArchive: true
        }
    }
}
