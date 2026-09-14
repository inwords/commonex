FROM certbot/certbot:v5.8.0@sha256:f70ad0adbb7e117f0fe42a63c553f28ea451edabc0148757b6efcd9735acaa20 AS runtime

WORKDIR /opt/commonex/certificates
COPY infra/certificates/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt
COPY infra/certificates/commonex_certificates/*.py ./commonex_certificates/
ENV PYTHONDONTWRITEBYTECODE=1

FROM runtime AS test
RUN apk add --no-cache docker-cli docker-cli-compose
WORKDIR /workspace
ENV PYTHONPATH=/workspace/infra/certificates
COPY infra ./infra
COPY .github/workflows/ ./.github/workflows/
ENTRYPOINT ["python", "-B", "-m", "unittest"]
