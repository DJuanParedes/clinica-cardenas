declare global {
  interface Window { __DEPLOYMENT__?: { pages?: boolean; apiBaseUrl?: string }; }
}
const config = window.__DEPLOYMENT__ ?? {};
export const pagesDeployment = config.pages === true;
export const apiBaseUrl = (config.apiBaseUrl ?? '').replace(/\/+$/, '');
export const backendPending = pagesDeployment && !apiBaseUrl;
export const pendingMessage = 'Servidor pendiente de publicación. El acceso y los datos todavía no están disponibles en esta web.';
