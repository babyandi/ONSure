FROM onsure-goal-validator:node20

USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       xvfb xauth libgtk-3-0 libnss3 libasound2t64 libxss1 libgbm1 libdrm2 \
       libxkbcommon0 libxcomposite1 libxdamage1 libxrandr2 libatk-bridge2.0-0 \
       libatspi2.0-0 libcups2 fonts-dejavu-core ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --gid 10001 onsure-e2e \
    && useradd --uid 10001 --gid 10001 --create-home --shell /bin/bash onsure-e2e

WORKDIR /onsure-product
USER 10001:10001
ENTRYPOINT []
CMD ["bash", "scripts/run-vscode-extension-host-e2e-container.sh", "--inside"]
