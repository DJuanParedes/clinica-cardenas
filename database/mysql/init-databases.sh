#!/bin/sh
set -eu

# Los identificadores son nombres fijos y seguros. No usar backticks dentro de este
# heredoc: en /bin/sh se interpretarían como sustitución de comandos antes de llegar a MySQL.
MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql --protocol=socket -uroot <<EOSQL
CREATE DATABASE IF NOT EXISTS auth_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'auth_user'@'%' IDENTIFIED BY '${AUTH_DB_PASSWORD}';
ALTER USER 'auth_user'@'%' IDENTIFIED BY '${AUTH_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON auth_db.* TO 'auth_user'@'%';

CREATE DATABASE IF NOT EXISTS patient_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'patient_user'@'%' IDENTIFIED BY '${PATIENT_DB_PASSWORD}';
ALTER USER 'patient_user'@'%' IDENTIFIED BY '${PATIENT_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON patient_db.* TO 'patient_user'@'%';

CREATE DATABASE IF NOT EXISTS appointment_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'appointment_user'@'%' IDENTIFIED BY '${APPOINTMENT_DB_PASSWORD}';
ALTER USER 'appointment_user'@'%' IDENTIFIED BY '${APPOINTMENT_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON appointment_db.* TO 'appointment_user'@'%';

CREATE DATABASE IF NOT EXISTS clinical_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'clinical_user'@'%' IDENTIFIED BY '${CLINICAL_DB_PASSWORD}';
ALTER USER 'clinical_user'@'%' IDENTIFIED BY '${CLINICAL_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON clinical_db.* TO 'clinical_user'@'%';

CREATE DATABASE IF NOT EXISTS finance_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'finance_user'@'%' IDENTIFIED BY '${FINANCE_DB_PASSWORD}';
ALTER USER 'finance_user'@'%' IDENTIFIED BY '${FINANCE_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON finance_db.* TO 'finance_user'@'%';

CREATE DATABASE IF NOT EXISTS lab_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'lab_user'@'%' IDENTIFIED BY '${LAB_DB_PASSWORD}';
ALTER USER 'lab_user'@'%' IDENTIFIED BY '${LAB_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON lab_db.* TO 'lab_user'@'%';

CREATE DATABASE IF NOT EXISTS operations_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'operations_user'@'%' IDENTIFIED BY '${OPERATIONS_DB_PASSWORD}';
ALTER USER 'operations_user'@'%' IDENTIFIED BY '${OPERATIONS_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON operations_db.* TO 'operations_user'@'%';

CREATE DATABASE IF NOT EXISTS inventory_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'inventory_user'@'%' IDENTIFIED BY '${INVENTORY_DB_PASSWORD}';
ALTER USER 'inventory_user'@'%' IDENTIFIED BY '${INVENTORY_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON inventory_db.* TO 'inventory_user'@'%';

CREATE DATABASE IF NOT EXISTS content_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'content_user'@'%' IDENTIFIED BY '${CONTENT_DB_PASSWORD}';
ALTER USER 'content_user'@'%' IDENTIFIED BY '${CONTENT_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON content_db.* TO 'content_user'@'%';

CREATE DATABASE IF NOT EXISTS encounter_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'encounter_user'@'%' IDENTIFIED BY '${ENCOUNTER_DB_PASSWORD}';
ALTER USER 'encounter_user'@'%' IDENTIFIED BY '${ENCOUNTER_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON encounter_db.* TO 'encounter_user'@'%';

CREATE DATABASE IF NOT EXISTS survey_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'survey_user'@'%' IDENTIFIED BY '${SURVEY_DB_PASSWORD}';
ALTER USER 'survey_user'@'%' IDENTIFIED BY '${SURVEY_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON survey_db.* TO 'survey_user'@'%';

FLUSH PRIVILEGES;
EOSQL

echo "Bases y usuarios de Clínica Cárdenas inicializados en MySQL."
