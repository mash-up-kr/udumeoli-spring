#cloud-config
# deploy.yml 의 SSH 배포 대상(ubuntu + deploy 계정), docker-compose.yml 이 요구하는
# /opt/oracle/wallet, /home/deploy/app 디렉토리를 부팅 시점에 준비한다.
# nginx 리버스 프록시 설정 자체는 기존 서버에서 그대로 옮겨와야 한다 (이 템플릿은 설치까지만 담당).

users:
  - name: deploy
    groups: [docker, sudo]
    shell: /bin/bash
    sudo: ["ALL=(ALL) NOPASSWD:ALL"]
    ssh_authorized_keys:
      - ${deploy_ssh_key}

package_update: true
packages:
  - nginx
  - ca-certificates
  - curl
  - gnupg

runcmd:
  # Docker Engine + Compose plugin (Ubuntu 공식 저장소)
  - install -m 0755 -d /etc/apt/keyrings
  - curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  - chmod a+r /etc/apt/keyrings/docker.asc
  - echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" > /etc/apt/sources.list.d/docker.list
  - apt-get update -y
  - apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  - systemctl enable --now docker
  - mkdir -p /opt/oracle/wallet
  - mkdir -p /home/deploy/app/secrets
  - mkdir -p /home/deploy/app/logs
  - chown -R deploy:deploy /home/deploy
  - systemctl enable --now nginx
