# syntax=docker/dockerfile:1

# ---- build ----
# HTML/JS/CSS não dependem de CPU: builda na arquitetura de quem builda, só o nginx é por plataforma
FROM --platform=$BUILDPLATFORM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
RUN npx ng build --configuration production

# ---- runtime: nginx sem root, servindo o build e fazendo proxy de /api para o backend ----
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY nginx/security-headers.conf /etc/nginx/security-headers.conf
COPY --from=build /app/dist/atlas-web/browser /usr/share/nginx/html
EXPOSE 8080
