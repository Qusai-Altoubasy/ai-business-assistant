FROM ghcr.io/cirruslabs/flutter:3.44.0 AS build

ARG API_BASE_URL=http://127.0.0.1:8080
WORKDIR /app

COPY pubspec.yaml pubspec.lock ./
RUN flutter pub get

COPY . .
RUN flutter build web --release \
    --dart-define=API_BASE_URL=${API_BASE_URL}

FROM nginx:1.28-alpine AS runtime

COPY --from=build /app/build/web/ /usr/share/nginx/html/
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
