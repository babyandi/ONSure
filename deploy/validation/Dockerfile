ARG BASE_IMAGE=onsure-goal-validator:node20
FROM ${BASE_IMAGE}

ARG PYTHON3_YAML_VERSION=6.0.1-2build2
ARG PYTHON3_JSONSCHEMA_VERSION=4.10.3-2ubuntu1
ARG CLAMAV_VERSION=1.5.3+dfsg-0ubuntu0.24.04.1
ARG FONTCONFIG_VERSION=2.15.0-1.1ubuntu2
ARG FONTS_NOTO_CJK_VERSION=1:20230817+repack1-3

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        "python3-yaml=${PYTHON3_YAML_VERSION}" \
        "python3-jsonschema=${PYTHON3_JSONSCHEMA_VERSION}" \
        "clamav=${CLAMAV_VERSION}" \
        "fontconfig=${FONTCONFIG_VERSION}" \
        "fonts-noto-cjk=${FONTS_NOTO_CJK_VERSION}" \
    && rm -rf /var/lib/apt/lists/*

USER 65534:65534
WORKDIR /workspace
ENTRYPOINT []
CMD ["true"]
