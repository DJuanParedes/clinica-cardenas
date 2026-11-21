/** Estilos comunes de las páginas públicas (reservar, prerregistro, encuesta). */
export const PUBLIC_STYLES = `
  :host { display: block; min-height: 100vh; background: linear-gradient(180deg, #e8f6fb 0, #f5f9fb 260px); }
  .top { display: flex; align-items: center; gap: 12px; max-width: 720px; margin: 0 auto; padding: 18px 16px 6px; }
  .top .name { font-weight: 800; font-size: 18px; color: var(--teal-strong, #0b5cad); line-height: 1.1; }
  .top .addr { font-size: 12.5px; color: var(--muted, #64748b); }
  main { max-width: 720px; margin: 0 auto; padding: 8px 16px 40px; }
  h1 { font-size: 24px; margin: 10px 0 4px; }
  .lead { color: var(--muted, #64748b); margin: 0 0 16px; }
  .card { background: #fff; border: 1px solid var(--line, #e2e8f0); border-radius: 20px; padding: 18px; box-shadow: 0 8px 24px -18px rgba(11, 60, 90, .35); display: grid; gap: 12px; }
  .steps { display: flex; gap: 6px; margin: 4px 0 14px; }
  .steps span { flex: 1; height: 5px; border-radius: 99px; background: #d9e6ec; }
  .steps span.on { background: var(--teal, #0aa5c9); }
  .grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
  @media (max-width: 560px) { .grid2 { grid-template-columns: 1fr; } }
  .span2 { grid-column: 1 / -1; }
  .opts { display: grid; gap: 8px; }
  .opt { display: flex; align-items: center; gap: 10px; border: 1px solid var(--line, #e2e8f0); border-radius: 14px; background: #fff; padding: 10px 12px; text-align: left; font: inherit; cursor: pointer; }
  .opt.on { border-color: var(--teal, #0aa5c9); background: var(--teal-soft, #e6f7fb); }
  .opt img { width: 42px; height: 42px; border-radius: 50%; object-fit: cover; }
  .opt .av { width: 42px; height: 42px; border-radius: 50%; display: grid; place-items: center; background: var(--teal-soft, #e6f7fb); color: var(--teal-strong); font-weight: 800; }
  .opt small { display: block; color: var(--muted, #64748b); }
  .chips { display: flex; flex-wrap: wrap; gap: 8px; }
  .chips button { border: 1px solid var(--line, #e2e8f0); background: #fff; border-radius: 999px; padding: 7px 14px; font: inherit; cursor: pointer; }
  .chips button.on { background: var(--teal-soft, #e6f7fb); border-color: var(--teal, #0aa5c9); color: var(--teal-strong); font-weight: 700; }
  .slots { display: grid; grid-template-columns: repeat(auto-fill, minmax(84px, 1fr)); gap: 8px; }
  .slots button { padding: 10px 0; border: 1px solid var(--line, #e2e8f0); border-radius: 10px; background: #fff; font: inherit; font-weight: 700; cursor: pointer; }
  .slots button.on { background: var(--teal, #0aa5c9); border-color: var(--teal, #0aa5c9); color: #fff; }
  .back { background: none; border: 0; color: var(--teal-strong); padding: 0; font: inherit; font-size: 14px; cursor: pointer; justify-self: start; }
  .consent { display: flex; gap: 10px; align-items: flex-start; border: 1px solid var(--line, #e2e8f0); border-radius: 14px; padding: 12px; font-size: 14px; }
  .consent.ok { border-color: #bfe0ca; background: var(--green-soft, #ecf8f0); }
  .consent.invalid { border-color: var(--gum, #c0264d); }
  .done { text-align: center; padding: 28px 18px; }
  .done .big { font-size: 46px; }
  .sum { display: block; background: #fff4df; border-radius: 12px; padding: 10px 12px; font-size: 14px; }
  .err { color: var(--gum, #c0264d); font-size: 13px; }
  .notice { background: #fff4df; border-radius: 12px; padding: 10px 12px; font-size: 14px; }
  .foot { text-align: center; color: var(--muted, #64748b); font-size: 12.5px; margin-top: 18px; }
`;
