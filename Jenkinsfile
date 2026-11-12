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
                    // Incluye el código que genera MapStruct (*MapperImpl) para que la cobertura lo encuentre
                    recordCoverage tools: [[parser: 'JACOCO', pattern: 'build/reports/jacoco/test/jacocoTestReport.xml']],
                            sourceDirectories: [[path: 'src/main/java'], [path: 'build/generated/sources/annotationProcessor/java/main']],
                            sourceCodeRetention: 'NEVER'
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
                // SonarScanner CLI configurado en Jenkins (Tools -> sonar-scanner). Se usa en lugar del plugin de Gradle
                // del proyecto (4.0.0.2929), que no es compatible con Gradle 9.
                // Reutiliza lo que generó la etapa del backend: clases compiladas, resultados JUnit y cobertura JaCoCo.
                // Requiere la credencial "sonar-token" (Secret text); sonar.qualitygate.wait=true falla si no se cumple el Quality Gate.
                script {
                    env.SCANNER_HOME = tool 'sonar-scanner'
                }
                withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                    sh '''
                        "$SCANNER_HOME/bin/sonar-scanner" \
                          -Dsonar.host.url=http://sonarqube:9000 \
                          -Dsonar.token="$SONAR_TOKEN" \
                          -Dsonar.projectKey=gestopago \
                          -Dsonar.projectName=GestoPago \
                          -Dsonar.sourceEncoding=UTF-8 \
                          -Dsonar.sources=src/main/java,src/main/resources \
                          -Dsonar.tests=src/test/java \
                          -Dsonar.java.source=17 \
                          -Dsonar.java.binaries=build/classes/java/main \
                          -Dsonar.java.test.binaries=build/classes/java/test \
                          -Dsonar.java.libraries="$HOME/.gradle/caches/modules-2/files-2.1/**/*.jar" \
                          -Dsonar.java.test.libraries="$HOME/.gradle/caches/modules-2/files-2.1/**/*.jar" \
                          -Dsonar.junit.reportPaths=build/test-results/test \
                          -Dsonar.coverage.jacoco.xmlReportPaths=build/reports/jacoco/test/jacocoTestReport.xml \
                          -Dsonar.coverage.exclusions="**/App.java,**/model/**,**/entity/**,**/enums/**" \
                          -Dsonar.qualitygate.wait=true \
                          -Dsonar.qualitygate.timeout=300
                    '''
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
