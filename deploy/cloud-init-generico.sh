#!/bin/bash
# Clínica Dental Cárdenas: instalación automática en un servidor Ubuntu 22.04/24.04 de cualquier proveedor
# (Azure, AWS, Google Cloud, Hetzner…), x86 o ARM. Se pega en "Datos personalizados / cloud-init / user data"
# al crear la máquina. Los puertos 80 y 443 se abren en el firewall del proveedor (en Azure: reglas HTTP y HTTPS).
# Registro: /var/log/clinica-instalacion.log
set -eux
exec > /var/log/clinica-instalacion.log 2>&1
# Memoria de intercambio: permite correr el sistema completo en máquinas de 2 GB
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 4G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
export DEBIAN_FRONTEND=noninteractive
apt-get update -y && apt-get install -y git curl openssl
curl -fsSL https://get.docker.com | sh
# El usuario administrador de la máquina (ubuntu en AWS/Oracle, azureuser u otro en Azure) puede usar Docker
ADMIN=$(getent passwd 1000 | cut -d: -f1 || true)
[ -n "$ADMIN" ] && usermod -aG docker "$ADMIN"
# Si el firewall del sistema está activo, abrir web
if command -v ufw >/dev/null && ufw status | grep -q active; then ufw allow 80/tcp; ufw allow 443/tcp; fi
[ -d /opt/clinica ] || git clone -b sprint-final-todas-las-hu https://github.com/DJuanParedes/clinica-cardenas.git /opt/clinica
bash /opt/clinica/deploy/instalar.sh
