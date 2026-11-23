-- v2.11: el administrador puede listar, crear, editar y eliminar todo, incluidos roles y permisos.

-- Roles del sistema que la clínica no usa se pueden ocultar (no se borran: el código depende de ellos).
alter table roles add column hidden boolean not null default false;

-- Descripción editable de cada permiso (además del nombre y el grupo).
alter table permissions add column description varchar(255) null;

-- Copia completa de todo lo eliminado, para consulta o recuperación manual.
create table deleted_records (
    id          bigint auto_increment primary key,
    entity      varchar(40)  not null,
    entity_id   varchar(40)  not null,
    label       varchar(200) not null,
    summary     varchar(500) null,
    data        longtext     not null,
    reason      varchar(300) null,
    deleted_by  varchar(60)  not null,
    deleted_at  datetime     not null,
    index idx_deleted_records_at (deleted_at)
);

insert into permissions (code, group_name, label, sort_order, description)
values ('ELIMINAR_REGISTROS', 'Seguridad', 'Eliminar registros (pacientes, citas, usuarios, catálogo…)', 82,
        'Borra definitivamente un registro y lo que depende de él. Se guarda una copia en la papelera.');
insert into role_permissions (role_code, permission_code) values ('ADMIN', 'ELIMINAR_REGISTROS');

-- Administrar roles y permisos queda solo para el administrador.
delete from role_permissions where permission_code = 'ROLES' and role_code <> 'ADMIN';
