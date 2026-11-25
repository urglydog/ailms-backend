# =====================================================================
# Backend production image — chỉ JRE 17 + ffprobe + file jar đã build sẵn.
#
# KHÔNG build Maven trong `docker build` (tốn ~1 GB RAM, server chỉ có 3,7 GB). Jar được build
# trước bằng container Maven có giới hạn RAM — xem be/docs/perf/QUY_TRINH_PHAT_HANH_BACKEND.md.
# Chế độ phát triển (hot-reload, mount thư mục) vẫn dùng Dockerfile.dev.
# =====================================================================
FROM eclipse-temurin:17-jre

# curl: healthcheck trong docker-compose. ffmpeg: FfprobeService gọi `ffprobe` để đọc thời lượng video.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ffmpeg \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --uid 10001 app

WORKDIR /app
COPY target/*.jar /app/app.jar

# Chạy bằng user thường (không phải root).
USER app
EXPOSE 8080

# Giới hạn heap để backend không ăn hết RAM của máy chủ dùng chung 3,7 GB với MySQL/Redis/AI.
# Đổi được từ docker-compose (biến BACKEND_JAVA_OPTS) mà không cần build lại image.
ENV JAVA_OPTS="-Xms128m -Xmx896m -XX:MaxMetaspaceSize=256m -XX:+UseSerialGC"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
