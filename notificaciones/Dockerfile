# Microservicio de notificaciones (WhatsApp y correo simulados). Sin dependencias: ~45 MB.
FROM node:20.18-alpine
WORKDIR /app
COPY server.js .
RUN mkdir -p /data && chown node:node /data
USER node
ENV PORT=8093 DATA_FILE=/data/notificaciones.json
EXPOSE 8093
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD wget -qO- http://127.0.0.1:8093/health || exit 1
CMD ["node", "server.js"]
