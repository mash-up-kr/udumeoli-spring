# 기존 udumeori-bucket (Standard tier, Public 가시성) 그대로 재현
resource "oci_objectstorage_bucket" "udumeori" {
  compartment_id = var.compartment_id
  namespace      = var.object_storage_namespace
  name           = var.object_storage_bucket_name
  access_type    = "ObjectRead" # 기존 설정: Public (익명 읽기 허용)
  storage_tier   = "Standard"
}
