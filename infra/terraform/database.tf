# 기존 udumeolidb2 ATP (Always Free, Transaction Processing, 19c) 그대로 재현
resource "oci_database_autonomous_database" "udumeolidb2" {
  compartment_id           = var.compartment_id
  db_name                  = var.adb_db_name
  display_name             = var.adb_db_name
  db_workload              = "OLTP" # 트랜잭션 처리
  db_version                = "19c"
  is_free_tier             = true
  cpu_core_count            = 1  # Always Free 고정값
  data_storage_size_in_tbs  = 1  # Always Free는 실사용 20GB로 제한되나 최소 입력값은 1
  admin_password            = var.adb_admin_password
  license_model             = "LICENSE_INCLUDED" # Always Free는 BYOL 불가

  # 기존 설정: mTLS 필수 + ACL(특정 IP 목록 + 0.0.0.0/0)
  is_mtls_connection_required = true

  # IP와 CIDR을 함께 받는 단순 list(string) 속성
  whitelisted_ips = concat(var.adb_acl_ips, var.adb_acl_cidrs)
}
