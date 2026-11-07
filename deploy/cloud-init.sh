#!/bin/bash
# Clínica Dental Cárdenas: instalación automática al crear el servidor en Oracle Cloud (Ubuntu 24.04, ARM).
# Se pega en "Script de inicialización" (cloud-init). Deja el registro en /var/log/clinica-instalacion.log
set -eux
exec > /var/log/clinica-instalacion.log 2>&1
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile && echo '/swapfile none swap sw 0 0' >> /etc/fstab
apt-get update -y && apt-get install -y git curl openssl
curl -fsSL https://get.docker.com | sh
usermod -aG docker ubuntu
iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
netfilter-persistent save
git clone -b sprint-final-todas-las-hu https://github.com/DJuanParedes/clinica-cardenas.git /opt/clinica
bash /opt/clinica/deploy/instalar.sh
