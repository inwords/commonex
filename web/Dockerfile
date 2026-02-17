# Build stage
FROM node:24-alpine3.23 AS build-stage

WORKDIR /app

# Copy package files first for better cache
COPY package.json package-lock.json ./

# Install dependencies
RUN npm ci

# Copy source code and build
COPY . ./
ENV NODE_ENV=production
RUN npm run build

# Production stage with Nginx (unprivileged for security)
FROM nginxinc/nginx-unprivileged:1.29.5-alpine3.23

# Install curl for health checks (as root, then switch back)
USER root
RUN apk add --no-cache curl
USER nginx

# Copy the static files from the build-stage
COPY --from=build-stage /app/build /usr/share/nginx/html

# Copy custom Nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 4001

CMD ["nginx", "-g", "daemon off;"]
