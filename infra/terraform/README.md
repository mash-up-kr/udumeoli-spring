# Infra Terraform — 신규 OCI 테넌시 이전

기존 OCI 계정(`cjunk0304`, 컴파트먼트 `mashup_udumeoli`, 리전 `ap-chuncheon-1`)에 떠 있는
Always Free 리소스를 신규 OCI 테넌시에 동일 스펙으로 재현하기 위한 Terraform 코드.

## 재현 대상

| 리소스 | 기존 스펙 |
|---|---|
| `oci_core_instance.app` | VM.Standard.E2.1.Micro, 1 OCPU/1GB, Ubuntu 24.04, 부트볼륨 47GB |
| `oci_core_instance.thumbnail` | VM.Standard.E2.1.Micro, 1 OCPU/1GB, Oracle Linux 9.7 |
| `oci_core_vcn.main` + subnets | 10.0.0.0/16 (public 10.0.0.0/24, private 10.0.1.0/24) |
| `oci_core_security_list.public` | 22/80/443/8080 inbound, all outbound |
| `oci_database_autonomous_database.udumeolidb2` | Always Free ATP, OLTP, 19c |
| `oci_objectstorage_bucket.udumeori` | Standard tier, Public |

## 1회성 사전 준비 (사람이 직접)

1. 신규 OCI 계정 생성 (Always Free 리소스는 테넌시당 한도가 있으므로 가능하면 신규 테넌시로)
2. OCI 콘솔 → 사용자 설정 → API 키 생성 → `~/.oci/config` 에 등록
   ```
   [DEFAULT]
   user=ocid1.user.oc1..xxxx
   fingerprint=xx:xx:...
   tenancy=ocid1.tenancy.oc1..xxxx
   region=ap-chuncheon-1
   key_file=~/.oci/oci_api_key.pem
   ```
3. `terraform.tfvars.example` 을 `terraform.tfvars` 로 복사하고 실제 값 채우기
   - 이미지 OCID는 리전마다 다르므로 반드시 `oci compute image list` 로 재확인
   - ATP admin 비밀번호는 기존 값을 재사용하지 말고 새로 발급

## 실행

```bash
cd infra/terraform
terraform init
terraform plan   # 과금되는 리소스가 생성되는지 반드시 먼저 확인
terraform apply
```

## apply 이후 수동으로 해야 하는 것 (Terraform이 커버하지 않음)

1. **nginx 리버스 프록시 설정** — 기존 서버의 `/etc/nginx/...` 설정 파일을 그대로 복사해서 새 서버에 배치
   (리포에 nginx 설정이 없어서 코드화 불가, 기존 서버에서 직접 가져올 것)
2. **ATP Wallet 다운로드 및 배치** — 콘솔 또는 `oci db autonomous-database generate-wallet` 로 다운로드 후
   새 서버의 `/opt/oracle/wallet` 에 업로드 (cloud-init으로 디렉토리는 미리 생성해둠)
   ⚠️ **콘솔에서 새로 받은 Wallet의 `ojdbc.properties`는 JKS 설정이 기본적으로 주석처리되어 있고
   `oracle.net.wallet_location=(...METHOD=FILE...)` 가 활성화돼 있음.** 이 상태로 두면 드라이버가
   `cwallet.sso` 기반 SSO 자동로그인 방식을 시도하다가 `KeyStoreException: SSO not found` /
   `NoSuchAlgorithmException: SSO KeyStore not available` 로 접속이 실패한다 (native 이미지에서
   SSO KeyStore 프로바이더가 등록되어 있지 않음). 반드시 아래처럼 JKS 모드로 명시 설정할 것:
   ```
   javax.net.ssl.trustStore=/opt/oracle/wallet/truststore.jks
   javax.net.ssl.trustStoreType=JKS
   javax.net.ssl.trustStorePassword=<wallet 비밀번호>
   javax.net.ssl.keyStore=/opt/oracle/wallet/keystore.jks
   javax.net.ssl.keyStoreType=JKS
   javax.net.ssl.keyStorePassword=<wallet 비밀번호>
   oracle.net.ssl_server_dn_match=true
   ```
3. **`/home/deploy/app/.env` 작성** — `DB_PASSWORD`, `WALLET_PASSWORD`, OAuth 키, `JWT_SECRET`,
   `KMS_MASTER_KEY`, Object Storage 키 등 ([docker-compose.yml](../../docker-compose.yml) 참고)
4. **데이터 이전** — 기존 ATP의 운영 데이터가 있다면 Data Pump Export/Import, Object Storage 버킷 데이터는
   `oci os object bulk-copy` 또는 `rclone` 으로 복사
5. **GitHub Actions Secrets 갱신** — `SERVER_HOST` 를 `terraform output app_public_ip` 값으로,
   `SSH_PRIVATE_KEY` 를 신규 `deploy` 계정 키로 교체
6. **SSL 인증서 재발급** — DNS 전환 후 certbot 등으로 새로 발급
7. **DNS 전환** — A 레코드를 `terraform output app_public_ip` 로 변경

## 주의

- `VM.Standard.E2.1.Micro` 와 Always Free ATP는 테넌시당 수량 제한이 있음 (ARM이 아닌 기존 세대 Always Free shape)
- `adb_acl_ips`/`adb_acl_cidrs` 는 기존 설정을 그대로 복사한 것으로, `0.0.0.0/0` 이 포함되어 사실상 전체 허용 상태였음.
  보안 강화를 원하면 새 앱 서버 Public IP 확정 후 CIDR을 좁히는 것을 권장.

## ⚠️ State 파일 관리 (중요)

로컬 PC의 Windows Smart App Control이 terraform 실행 파일을 차단하는 문제로, 결국 **OCI Cloud Shell
(`~/infra/terraform`)에서 `apply`를 실행**했다. 이 때문에 **`terraform.tfstate`의 최신 원본은 Cloud Shell
쪽에만 있고, 로컬 저장소의 state는 낡은 채로 남아있다** (`terraform.tfstate.STALE_DO_NOT_USE` 로 이름 변경해둠).

- 로컬에서 실수로 낡은 state로 `apply`하면 이미 떠 있는 리소스를 전부 다시 만들려고 시도해 **서비스 중단**이
  발생한다 (실제로 한 번 발생: 앱 서버가 destroy된 후 구버전 설정값(부트볼륨 47GB)으로 재생성을 시도하다
  실패해서 몇 분간 다운됨).
- **당분간은 Cloud Shell에서만 `plan`/`apply`를 실행할 것.** 로컬에서 작업하려면 반드시 먼저
  `terraform plan`으로 "N to add"가 과도하게 크지 않은지(기존 리소스를 다시 만들려는 건 아닌지) 확인.
- 근본적인 해결책은 **원격 backend(OCI Object Storage) 설정** — 로컬/Cloud Shell 어디서 실행하든 같은
  state를 보게 되어 이런 drift 자체가 발생하지 않는다. 다음 작업으로 `backend "http"` 또는 OCI Object
  Storage backend 구성을 권장.
