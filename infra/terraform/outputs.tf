output "app_public_ip" {
  description = "새 앱 서버 Public IP. deploy.yml 의 SERVER_HOST secret 과 DNS A 레코드에 이 값을 사용"
  value       = oci_core_instance.app.public_ip
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
