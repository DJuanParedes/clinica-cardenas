package pe.clinicacardenas.admin;

import java.util.Map;

/** Nombre entendible de cada tabla, para explicar qué se borra junto con un registro. */
final class TableNames {
    private TableNames() {}

    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("anamnesis_records", "anamnesis llenadas"),
            Map.entry("appointments", "citas"),
            Map.entry("assistant_bookings", "reservas del asistente"),
            Map.entry("automation_rules", "recordatorios automáticos"),
            Map.entry("campaign_recipients", "destinatarios de campañas"),
            Map.entry("campaigns", "campañas"),
            Map.entry("clinical_histories", "historias clínicas"),
            Map.entry("commission_lines", "líneas de comisión"),
            Map.entry("commission_rules", "reglas de comisión"),
            Map.entry("commission_settlements", "liquidaciones de comisión"),
            Map.entry("complaints", "reclamos"),
            Map.entry("consent_records", "aceptaciones de la política de datos"),
            Map.entry("consent_requests", "solicitudes de consentimiento"),
            Map.entry("dentist_schedules", "horarios"),
            Map.entry("dentists", "odontólogos"),
            Map.entry("diagnoses", "diagnósticos"),
            Map.entry("evolutions", "evoluciones"),
            Map.entry("file_requests", "pedidos de archivos"),
            Map.entry("installments", "cuotas"),
            Map.entry("inventory_lots", "lotes de inventario"),
            Map.entry("inventory_moves", "movimientos de inventario"),
            Map.entry("invoices", "comprobantes"),
            Map.entry("lab_order_events", "eventos de laboratorio"),
            Map.entry("lab_order_payments", "pagos al laboratorio"),
            Map.entry("lab_orders", "órdenes de laboratorio"),
            Map.entry("lead_events", "movimientos del CRM"),
            Map.entry("leads", "oportunidades del CRM"),
            Map.entry("notifications", "notificaciones"),
            Map.entry("odontogram_entries", "marcas del odontograma"),
            Map.entry("online_charges", "cobros en línea"),
            Map.entry("ortho_cases", "casos de ortodoncia"),
            Map.entry("ortho_controls", "controles de ortodoncia"),
            Map.entry("ortho_photos", "fotos de ortodoncia"),
            Map.entry("password_reset_codes", "códigos de recuperación"),
            Map.entry("patient_accounts", "cuentas del portal"),
            Map.entry("patient_files", "archivos"),
            Map.entry("patient_notes", "notas"),
            Map.entry("patient_notices", "avisos"),
            Map.entry("patient_relations", "parentescos"),
            Map.entry("patient_tags", "etiquetas"),
            Map.entry("payments", "pagos"),
            Map.entry("periodontograms", "periodontogramas"),
            Map.entry("portal_codes", "códigos del portal"),
            Map.entry("procedure_supplies", "materiales por procedimiento"),
            Map.entry("promotions", "promociones"),
            Map.entry("supply_requests", "pedidos de material"),
            Map.entry("surveys", "encuestas"),
            Map.entry("tasks", "tareas"),
            Map.entry("treatment_items", "tratamientos"),
            Map.entry("treatment_plans", "planes de tratamiento"),
            Map.entry("users", "usuarios"),
            Map.entry("vital_signs", "signos vitales"),
            Map.entry("wa_threads", "conversaciones de WhatsApp"),
            Map.entry("waitlist", "registros en lista de espera")
    );

    static String of(String table) {
        return NAMES.getOrDefault(table, table.replace('_', ' '));
    }
}
