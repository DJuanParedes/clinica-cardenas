# Simulador de WhatsApp (compatible con WhatsApp Cloud API de Meta), sin dependencias: ~45 MB.
# Ningún mensaje sale a celulares reales: todo se ve en el panel http://localhost:9084
FROM node:20.18-alpine
WORKDIR /app
COPY server.js .
RUN mkdir -p /data && chown node:node /data
USER node
ENV PORT=8094 DATA_FILE=/data/whatsapp.json
EXPOSE 8094
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD wget -qO- http://127.0.0.1:8094/health || exit 1
CMD ["node", "server.js"]
