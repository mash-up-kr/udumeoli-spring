# 기존 instance-20260524-0047 (앱 서버) 그대로 재현
resource "oci_core_instance" "app" {
  compartment_id      = var.compartment_id
  availability_domain = data.oci_identity_availability_domains.ads.availability_domains[0].name
  display_name        = "udumeoli-app"
  shape               = var.app_instance_shape

  shape_config {
    ocpus         = var.app_ocpus
    memory_in_gbs = var.app_memory_in_gbs
  }

  create_vnic_details {
    subnet_id        = oci_core_subnet.public.id
    assign_public_ip = true
    display_name     = "udumeoli-app-vnic"
  }

  source_details {
    source_type             = "image"
    source_id               = var.app_image_ocid
    boot_volume_size_in_gbs = var.app_boot_volume_size_in_gbs
  }

  metadata = {
    ssh_authorized_keys = var.ssh_public_key
    # docker-compose.yml 이 기대하는 /opt/oracle/wallet, deploy 계정, docker 설치를 부팅 시 준비
    user_data = base64encode(templatefile("${path.module}/cloud-init/app.yaml.tpl", {
      deploy_ssh_key = var.deploy_user_ssh_public_key != "" ? var.deploy_user_ssh_public_key : var.ssh_public_key
    }))
  }

  # VM.Standard.E2.1.Micro 는 크기 조정 미지원 (Always Free shape)
  lifecycle {
    ignore_changes = [source_details[0].source_id]
  }
}

# 기존 thumbnail 인스턴스 그대로 재현 (Oracle Linux, opc 계정)
resource "oci_core_instance" "thumbnail" {
  compartment_id      = var.compartment_id
  availability_domain = data.oci_identity_availability_domains.ads.availability_domains[0].name
  display_name        = "thumbnail"
  shape               = var.thumbnail_instance_shape

  shape_config {
    ocpus         = var.thumbnail_ocpus
    memory_in_gbs = var.thumbnail_memory_in_gbs
  }

  create_vnic_details {
    subnet_id        = oci_core_subnet.public.id
    assign_public_ip = true
    display_name     = "thumbnail-vnic"
  }

  source_details {
    source_type = "image"
    source_id   = var.thumbnail_image_ocid
  }

  metadata = {
    ssh_authorized_keys = var.ssh_public_key
  }

  lifecycle {
    ignore_changes = [source_details[0].source_id]
  }
}

data "oci_identity_availability_domains" "ads" {
  compartment_id = var.tenancy_ocid
}
