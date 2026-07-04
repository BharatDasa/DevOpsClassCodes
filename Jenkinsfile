pipeline {
  agent {
    kubernetes {
      inheritFrom 'jenkins-agent'
      defaultContainer 'jnlp'
    }
  }

  environment {
    IMAGE = "192.168.56.11:8082/repository/docker-hosted/addressbook"
    VERSION = "v${BUILD_NUMBER}"
    PROM_URL = "http://monitoring-kube-prometheus-prometheus.monitoring.svc:9090"
    ANSIBLE_HOST_KEY_CHECKING = "False"
  }

  stages {

    // =========================
    // INIT
    // =========================
    stage('Init Version') {
      steps {
        script {
          env.APP_VERSION = "2.0.${BUILD_NUMBER}-${System.currentTimeMillis()}"
        }
      }
    }

    // =========================
    // SOURCE
    // =========================
    stage('Clone Code') {
      steps {
        git url: 'git@github.com:BharatDasa/DevOpsClassCodes.git',
            credentialsId: 'github-ssh'
      }
    }

    // =========================
    // BUILD ARTIFACT
    // =========================
    stage('Build & Upload Artifact') {
      steps {
        container('maven') {
          sh '''
            echo "===== MAVEN BUILD & DEPLOY ====="
            mvn clean deploy -U -DskipTests -Drevision=${APP_VERSION}
          '''
        }
      }
    }

    // =========================
    // BUILD IMAGE
    // =========================
    stage('Build & Push Image') {
      steps {
        container('kaniko') {
          sh '''
            echo "===== KANIKO BUILD & PUSH ====="

            /kaniko/executor \
              --dockerfile=Dockerfile \
              --context=$(pwd) \
              --destination=${IMAGE}:latest \
              --destination=${IMAGE}:${VERSION} \
              --insecure \
              --skip-tls-verify
          '''
        }
      }
    }

    // =========================
    // ANSIBLE FIX
    // =========================
    stage('Ansible Fix') {
      steps {
        container('ansible') {
          withCredentials([
            sshUserPrivateKey(
              credentialsId: 'ansible-ssh-key',
              keyFileVariable: 'SSH_KEY'
            )
          ]) {
            sh '''
              echo "===== ANSIBLE AUTO-FIX ====="

              chmod 600 $SSH_KEY
              ansible-playbook -i ansible/inventory.ini ansible/auto-fix.yml \
                --private-key=$SSH_KEY -u bharat
            '''
          }
        }
      }
    }

    // =========================
    // VALIDATION + SECURITY
    // =========================
    stage('Validation + Security') {
      parallel {

        stage('Ansible Validation') {
          steps {
            container('ansible') {
              withCredentials([
                sshUserPrivateKey(
                  credentialsId: 'ansible-ssh-key',
                  keyFileVariable: 'SSH_KEY'
                )
              ]) {
                sh '''
                  echo "===== ANSIBLE VALIDATION ====="

                  chmod 600 $SSH_KEY
                  ansible-playbook -i ansible/inventory.ini ansible/precheck.yml \
                    --private-key=$SSH_KEY -u bharat
                '''
              }
            }
          }
        }

        stage('Trivy HTML Report') {
          steps {
            container('trivy') {
              sh '''
                echo "===== TRIVY HTML REPORT ====="

                rm -rf /tmp/trivy-cache-html
                mkdir -p /tmp/trivy-cache-html
                mkdir -p reports

                wget -q https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/html.tpl -O html.tpl

                trivy image \
                  --cache-dir /tmp/trivy-cache-html \
                  --timeout 15m \
                  --scanners vuln \
                  --format template \
                  --template "@html.tpl" \
                  -o reports/trivy-report.html \
                  ${IMAGE}:${VERSION} || true
              '''
            }
          }
        }

        stage('Trivy Critical Gate') {
          steps {
            container('trivy') {
              sh '''
                echo "===== TRIVY CRITICAL CHECK ====="

                rm -rf /tmp/trivy-cache-gate
                mkdir -p /tmp/trivy-cache-gate

                trivy image \
                  --cache-dir /tmp/trivy-cache-gate \
                  --timeout 15m \
                  --scanners vuln \
                  --severity CRITICAL \
                  --ignore-unfixed \
                  ${IMAGE}:${VERSION} || true
              '''
            }
          }
        }

      }
    }

    // =========================
    // REPORT UI
    // =========================
    stage('Publish Trivy Report') {
      steps {
        publishHTML([
          reportDir: 'reports',
          reportFiles: 'trivy-report.html',
          reportName: 'Trivy Security Report',
          keepAll: true,
          alwaysLinkToLastBuild: true,
          allowMissing: true
        ])
      }
    }

    // =========================
    // DEPLOY
    // =========================
    stage('Deploy to Kubernetes') {
      steps {
        sh '''
          echo "===== DEPLOY TO KUBERNETES ====="

          echo "===== DOWNLOAD kubectl ====="
          curl -LO https://dl.k8s.io/release/v1.29.0/bin/linux/amd64/kubectl
          chmod +x kubectl

          echo "===== APPLY NAMESPACE ====="
          ./kubectl apply -f k8s/namespace.yaml

          echo "===== APPLY MANIFESTS ====="
          ./kubectl apply -f k8s/

          echo "===== APPLY SERVICEMONITOR ====="
          ./kubectl apply -f k8s/servicemonitor.yaml

          echo "===== SET IMAGE ====="
          ./kubectl set image deployment/addressbook \
            addressbook=${IMAGE}:${VERSION} -n addressbook

          echo "===== ROLLOUT STATUS ====="
          ./kubectl rollout status deployment/addressbook -n addressbook --timeout=180s
        '''
      }
    }

    // =========================
    // HPA
    // =========================
    stage('Deploy HPA') {
      steps {
        sh '''
          echo "===== APPLY HPA ====="
          ./kubectl apply -f k8s/hpa.yaml
          ./kubectl get hpa -n addressbook
        '''
      }
    }

    // =========================
    // MONITORING
    // =========================
    stage('Verify Monitoring') {
      steps {
        sh '''
          echo "===== VERIFY PROMETHEUS ====="

          RESPONSE=$(curl -s ${PROM_URL}/api/v1/query?query=up)

          if echo "$RESPONSE" | grep -q '"status":"success"'; then
            echo "✅ Prometheus reachable"
          else
            echo "❌ Prometheus failed"
            exit 1
          fi
        '''
      }
    }

    // =========================
    // HEALTH CHECK (FINAL FIX)
    // =========================
    stage('Health Check & Rollback') {
      steps {
        sh '''
          echo "===== HEALTH CHECK ====="

          READY=$(./kubectl get deployment addressbook -n addressbook \
            -o jsonpath='{.status.readyReplicas}')

          DESIRED=$(./kubectl get deployment addressbook -n addressbook \
            -o jsonpath='{.spec.replicas}')

          READY=${READY:-0}

          if [ "$READY" != "$DESIRED" ]; then
            echo "❌ Rolling back..."
            ./kubectl rollout undo deployment/addressbook -n addressbook
            exit 1
          fi

          CRASH=$(./kubectl get pods -n addressbook | grep CrashLoopBackOff || true)

          if [ ! -z "$CRASH" ]; then
            echo "❌ CrashLoop → rollback"
            ./kubectl rollout undo deployment/addressbook -n addressbook
            exit 1
          fi

          echo "===== HPA STATUS ====="
          ./kubectl get hpa -n addressbook

          echo "✅ Deployment Healthy"
        '''
      }
    }

  }

  post {
    always {
      archiveArtifacts artifacts: 'reports/*.html', allowEmptyArchive: true
    }

    success {
      echo "✅ SUCCESS: ${IMAGE}:${VERSION} deployed"
      echo "🚀 DevSecOps pipeline completed"
      echo "🌐 URL: https://addressbook.192.168.56.100.nip.io"
    }

    failure {
      echo "❌ FAILED: Blocked or rolled back"
    }
  }
}
