# Build the react-admin bundle and serve it with nginx.
# The nginx template proxies /api/admin to BACKEND_URL (default http://vaultguard:8080),
# so the UI and API stay same-origin and no CORS configuration is required.
FROM node:22-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
# tsc -b type-checks tests/ and vitest.config.ts as well (see tsconfig includes)
COPY tsconfig.json tsconfig.node.json vite.config.ts vitest.config.ts index.html ./
COPY src ./src
COPY tests ./tests
RUN npm run build

FROM nginx:1.27-alpine
ENV BACKEND_URL=http://vaultguard:8080
COPY docker/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 8080
