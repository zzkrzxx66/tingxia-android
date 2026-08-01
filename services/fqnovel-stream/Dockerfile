FROM python:3.12-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY server.py /app/server.py
RUN mkdir -p /var/cache/fqnovel-audio

ENV FQNOVEL_UPSTREAM=http://127.0.0.1:9999 \
    AUDIO_CACHE_DIR=/var/cache/fqnovel-audio \
    STREAM_HOST=127.0.0.1 \
    STREAM_PORT=9998

EXPOSE 9998
CMD ["python", "/app/server.py"]
