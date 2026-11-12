output "app_public_ip" {
  description = "새 앱 서버 Reserved Public IP. deploy.yml 의 SERVER_HOST secret 과 DNS A 레코드에 이 값을 사용 (Ephemeral이 아니라 재부팅/정지에도 바뀌지 않음)"
  value       = oci_core_public_ip.app_reserved.ip_address
}

output "thumbnail_public_ip" {
  value = oci_core_instance.thumbnail.public_ip
}

output "adb_connection_strings" {
  value     = oci_database_autonomous_database.udumeolidb2.connection_strings
  sensitive = true
}

output "object_storage_bucket_name" {
  value = oci_objectstorage_bucket.udumeori.name
}
