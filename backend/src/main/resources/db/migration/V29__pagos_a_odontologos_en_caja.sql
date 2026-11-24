-- Cada pago de comisiones a un odontólogo queda también como egreso de caja (categoría HONORARIOS).
alter table commission_payouts add column expense_id bigint null;
create index ix_commission_payouts_expense on commission_payouts (expense_id);
