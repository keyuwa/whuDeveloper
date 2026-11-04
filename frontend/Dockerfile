FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
# Official nginx image envsubst's /etc/nginx/templates/*.template on start.
# PORT=80 keeps Compose mapped at 5173:80. Render overrides PORT (default 10000).
# API_UPSTREAM is an origin. Compose uses the api service. On Render free tier
# set the API public URL, e.g. https://whu-developer-api.onrender.com
# (private DNS names cannot receive traffic on a free web service).
ENV PORT=80
ENV API_UPSTREAM=http://api:8080
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY docker-entrypoint.d/15-dynamic-upstream.envsh /docker-entrypoint.d/15-dynamic-upstream.envsh
RUN chmod 755 /docker-entrypoint.d/15-dynamic-upstream.envsh
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 80
