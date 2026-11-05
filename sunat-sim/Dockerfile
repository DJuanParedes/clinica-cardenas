# API simulada de SUNAT (billService SOAP + consulta RUC). Sin dependencias: ~45 MB.
FROM node:20.18-alpine
WORKDIR /app
COPY server.js .
RUN mkdir -p /data && chown node:node /data
USER node
ENV PORT=8090 DATA_FILE=/data/registro.json
EXPOSE 8090
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD wget -qO- http://127.0.0.1:8090/health || exit 1
CMD ["node", "server.js"]
