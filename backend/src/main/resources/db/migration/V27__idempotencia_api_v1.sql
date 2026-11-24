-- v2.12: reintentos seguros desde la app (sincronización offline-first).
-- Una escritura enviada con la cabecera Idempotency-Key se ejecuta una sola vez; si la app la reintenta
-- (porque se cortó la red antes de recibir la respuesta) recibe la misma respuesta sin crear un duplicado.
create table idempotency_keys (
    idem_key    varchar(80)  not null,
    scope       varchar(60)  not null,
    principal   varchar(80)  not null,
    status      int          null,
    response    longtext     null,
    created_at  datetime     not null,
    primary key (idem_key, scope, principal),
    index idx_idempotency_created (created_at)
);
