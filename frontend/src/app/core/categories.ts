/**
 * v2.10: categorías de procedimientos. Las mismas en el motivo de la cita, la agenda, el plan de tratamiento y las
 * reglas de comisión, con un color fijo cada una para reconocerlas de un vistazo.
 */
export const CATEGORY_ORDER = ['Diagnóstico', 'Prevención', 'Operatoria', 'Endodoncia', 'Periodoncia', 'Cirugía', 'Ortodoncia',
  'Prótesis', 'Implantología', 'Estética'];

const COLORS: Record<string, string> = {
  'Diagnóstico': '#5b6b7f', 'Prevención': '#1f9d6b', 'Operatoria': '#0a8fb5', 'Endodoncia': '#c0264d', 'Periodoncia': '#b26a00',
  'Cirugía': '#8e3fb8', 'Ortodoncia': '#2563eb', 'Prótesis': '#7a5a2e', 'Implantología': '#0f766e', 'Estética': '#d14d8b',
};

export const CATEGORY_HINT: Record<string, string> = {
  'Diagnóstico': 'Evaluación, control, dolor, radiografías', 'Prevención': 'Profilaxis, flúor, sellantes',
  'Operatoria': 'Curaciones y restauraciones', 'Endodoncia': 'Tratamiento de conductos', 'Periodoncia': 'Encías y raspado',
  'Cirugía': 'Extracciones y cordales', 'Ortodoncia': 'Brackets y controles', 'Prótesis': 'Coronas y prótesis',
  'Implantología': 'Implantes', 'Estética': 'Blanqueamiento y carillas',
};

export function categoryColor(c?: string | null): string { return (c && COLORS[c]) || '#64748b'; }

/** Ordena las categorías que existen en el catálogo según el orden clínico habitual. */
export function sortCategories(list: (string | null | undefined)[]): string[] {
  const set = [...new Set(list.filter((c): c is string => !!c))];
  return set.sort((a, b) => (CATEGORY_ORDER.indexOf(a) + 1 || 99) - (CATEGORY_ORDER.indexOf(b) + 1 || 99) || a.localeCompare(b));
}
