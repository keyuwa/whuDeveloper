FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
# Official nginx image envsubst's /etc/nginx/templates/*.template on start.
# Default keeps local compose working (service name `api`). Override on Render:
# API_UPSTREAM=whu-developer-api:8080
ENV API_UPSTREAM=api:8080
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 80
