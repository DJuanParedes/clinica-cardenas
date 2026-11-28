-- v2.20: la papelera permite restaurar lo eliminado.
alter table deleted_records add column restored_at datetime null, add column restored_by varchar(60) null;
