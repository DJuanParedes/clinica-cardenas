# Microservicio de notificaciones: WhatsApp Cloud API (Meta) y correo SMTP (nodemailer).
FROM node:20.18-alpine
WORKDIR /app
COPY package.json ./
RUN npm install --omit=dev --no-audit --no-fund && npm cache clean --force
COPY server.js proveedores.js ./
RUN mkdir -p /data && chown node:node /data
USER node
ENV PORT=8093 DATA_FILE=/data/notificaciones.json
EXPOSE 8093
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD wget -qO- http://127.0.0.1:8093/health || exit 1
CMD ["node", "server.js"]
