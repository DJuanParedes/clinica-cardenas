import { writeFileSync } from 'node:fs';
const apiBaseUrl = (process.env.PUBLIC_API_BASE_URL ?? '').trim().replace(/\/+$/, '');
if (apiBaseUrl) {
  const url = new URL(apiBaseUrl);
  if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash || url.pathname !== '/') {
    throw new Error('PUBLIC_API_BASE_URL debe ser un origen HTTPS sin credenciales, ruta, query ni fragmento.');
  }
}
writeFileSync('public/deployment-config.js', `window.__DEPLOYMENT__ = ${JSON.stringify({pages:true,apiBaseUrl})};\n`);
