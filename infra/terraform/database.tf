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

  # 새 앱/썸네일 서버의 실제 Public IP를 자동으로 포함 (기존 테넌시의 IP는 새 환경과 무관하므로 미사용)
  whitelisted_ips = concat(
    [oci_core_instance.app.public_ip, oci_core_instance.thumbnail.public_ip],
    var.adb_acl_extra_ips,
    var.adb_acl_cidrs,
  )

  # Always Free ADB는 cpu_core_count를 API로 변경 불가(403) - 드리프트 무시
  lifecycle {
    ignore_changes = [cpu_core_count]
  }
}
