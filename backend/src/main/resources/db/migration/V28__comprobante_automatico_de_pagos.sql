-- Comprobante electrónico emitido automáticamente para cada cobro (evita emitir dos veces el mismo pago).
alter table payments add column invoice_id bigint null;
create index ix_payments_invoice on payments (invoice_id);
