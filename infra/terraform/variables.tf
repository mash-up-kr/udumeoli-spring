variable "region" {
  description = "OCI region. 신규 테넌시의 홈 리전을 사용 (테넌시 생성 시 고정됨)"
  type        = string
  default     = "ap-tokyo-1"
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
  description = "부트 볼륨 크기(GB). 기존 서버는 47GB였지만 이 테넌시 최소 요구치가 50GB라 50으로 올림"
  type        = number
  default     = 50
}

# 비워두면 data.oci_core_images 로 해당 리전의 최신 Ubuntu 24.04 이미지를 자동 조회함
variable "app_image_ocid" {
  description = "Ubuntu 24.04 이미지 OCID. 비워두면 자동 조회"
  type        = string
  default     = null
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

# 비워두면 data.oci_core_images 로 해당 리전의 최신 Oracle Linux 9 이미지를 자동 조회함
variable "thumbnail_image_ocid" {
  description = "Oracle Linux 9 이미지 OCID. 비워두면 자동 조회"
  type        = string
  default     = null
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

variable "adb_acl_extra_ips" {
  description = "앱/썸네일 서버 외에 ATP 접근을 추가로 허용할 IP (예: 관리자 자택/사무실 IP). 새 앱·썸네일 서버 IP는 자동으로 포함됨"
  type        = list(string)
  default     = []
}

variable "adb_acl_cidrs" {
  description = "ATP 접근 허용 CIDR. 기존 설정은 0.0.0.0/0 (사실상 전체 허용) 이었음 - 보안 강화를 원하면 빈 리스트로 좁히고 adb_acl_extra_ips/앱 서버 IP만 허용"
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
  description = "테넌시 Object Storage 네임스페이스. 비워두면 data.oci_objectstorage_namespace 로 자동 조회"
  type        = string
  default     = null
}
