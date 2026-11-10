data "oci_objectstorage_namespace" "ns" {
  compartment_id = var.compartment_id
}

# 기존 udumeori-bucket (Standard tier, Public 가시성) 그대로 재현
resource "oci_objectstorage_bucket" "udumeori" {
  compartment_id = var.compartment_id
  namespace      = coalesce(var.object_storage_namespace, data.oci_objectstorage_namespace.ns.namespace)
  name           = var.object_storage_bucket_name
  access_type    = "ObjectRead" # 기존 설정: Public (익명 읽기 허용)
  storage_tier   = "Standard"
}
