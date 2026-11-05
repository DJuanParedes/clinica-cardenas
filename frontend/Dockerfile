# ---------- 1) Compilación de Angular ----------
FROM node:20.18-alpine AS build
WORKDIR /app
COPY package.json ./
RUN npm install --no-audit --no-fund --loglevel=error
COPY . .
RUN npx ng build --configuration production

# ---------- 2) Servidor estático (~20 MB) ----------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/clinica/browser /usr/share/nginx/html
EXPOSE 80
