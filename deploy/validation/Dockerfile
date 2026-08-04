ARG BASE_IMAGE=onsure-goal-validator:node20
FROM ${BASE_IMAGE}

ARG PYTHON3_YAML_VERSION=6.0.1-2build2
ARG PYTHON3_JSONSCHEMA_VERSION=4.10.3-2ubuntu1

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        "python3-yaml=${PYTHON3_YAML_VERSION}" \
        "python3-jsonschema=${PYTHON3_JSONSCHEMA_VERSION}" \
    && rm -rf /var/lib/apt/lists/*

USER 65534:65534
WORKDIR /workspace
ENTRYPOINT []
CMD ["true"]
