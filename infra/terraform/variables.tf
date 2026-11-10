variable "region" {
  description = "OCI region. 기존 서버와 동일 리전: ap-chuncheon-1 (South Korea North)"
  type        = string
  default     = "ap-chuncheon-1"
}

variable "tenancy_ocid" {
  description = "신규 테넌시 OCID (~/.oci/config 의 tenancy)"
  type        = string
}

variable "compartment_id" {
  description = "리소스를 생성할 컴파트먼트 OCID. 기존 서버는 mashup_udumeoli 컴파트먼트에 있었음"
  type        = string
}

variable "ssh_public_key" {
  description = "Compute 인스턴스에 등록할 SSH 공개키 (내용 전체, 예: ssh-ed25519 AAAA...)"
  type        = string
}

variable "deploy_user_ssh_public_key" {
  description = "GitHub Actions 배포용 deploy 계정 SSH 공개키. 비워두면 ssh_public_key 와 동일 키를 cloud-init에서 deploy 계정에도 등록"
  type        = string
  default     = ""
}

# --- Compute: App 서버 ---
# 기존 instance-20260524-0047 스펙 그대로
variable "app_instance_shape" {
  type    = string
  default = "VM.Standard.E2.1.Micro"
}

variable "app_ocpus" {
  type    = number
  default = 1
}

variable "app_memory_in_gbs" {
  type    = number
  default = 1
}

variable "app_boot_volume_size_in_gbs" {
  type    = number
  default = 47
}

# Canonical-Ubuntu-24.04 이미지 OCID는 리전마다 다르므로 실제 apply 전에
# `oci compute image list --operating-system "Canonical Ubuntu" --operating-system-version "24.04"` 로 재확인할 것
variable "app_image_ocid" {
  description = "Ubuntu 24.04 이미지 OCID (리전별로 다름, apply 전 재확인 필요)"
  type        = string
}

# --- Compute: Thumbnail 서버 ---
# 기존 thumbnail 인스턴스 스펙 그대로
variable "thumbnail_instance_shape" {
  type    = string
  default = "VM.Standard.E2.1.Micro"
}

variable "thumbnail_ocpus" {
  type    = number
  default = 1
}

variable "thumbnail_memory_in_gbs" {
  type    = number
  default = 1
}

# Oracle-Linux-9.7 이미지 OCID도 리전마다 다름, apply 전 재확인 필요
variable "thumbnail_image_ocid" {
  description = "Oracle Linux 9.7 이미지 OCID (리전별로 다름, apply 전 재확인 필요)"
  type        = string
}

# --- Autonomous Database ---
variable "adb_db_name" {
  description = "ATP 데이터베이스 이름. 기존: udumeolidb2"
  type        = string
  default     = "udumeolidb2"
}

variable "adb_admin_password" {
  description = "ATP ADMIN 계정 비밀번호 (신규 발급, 기존 값 재사용 금지)"
  type        = string
  sensitive   = true
}

variable "adb_acl_ips" {
  description = "ATP 접근 허용 IP 목록. 기존: 134.185.123.123(thumbnail), 175.127.232.64(관리자 IP로 추정) - 새 서버 IP 확정 후 갱신 필요"
  type        = list(string)
  default     = ["134.185.123.123", "175.127.232.64"]
}

variable "adb_acl_cidrs" {
  description = "ATP 접근 허용 CIDR. 기존 설정은 0.0.0.0/0 (사실상 전체 허용) 이었음 - 이전 시 더 좁히는 것을 권장"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

# --- Object Storage ---
variable "object_storage_bucket_name" {
  description = "오브젝트 스토리지 버킷 이름. 기존: udumeori-bucket"
  type        = string
  default     = "udumeori-bucket"
}

variable "object_storage_namespace" {
  description = "테넌시 Object Storage 네임스페이스 (OCI 콘솔 Object Storage 개요 화면에서 확인)"
  type        = string
}
