import {Attributes, metrics} from '@opentelemetry/api';
import {FastifyPluginAsync, FastifyRequest} from 'fastify';
import fastifyPlugin from 'fastify-plugin';

const HTTP2_VERSION_MAJOR = 2;
const METER_NAME = 'commonex-backend.fastify-http2';
export const LEGACY_REQUEST_DURATION_METRIC_NAME = 'http.server.duration';
export const STABLE_REQUEST_DURATION_METRIC_NAME = 'http.server.request.duration';
const REQUEST_TIMEOUT_STATUS_CODE = 408;
const CLIENT_CLOSED_REQUEST_STATUS_CODE = 499;

const requestStartTimes = new WeakMap<FastifyRequest, bigint>();

const buildRequestAttributes = (request: FastifyRequest, statusCode: number, errorType?: string): Attributes => {
  const protocolVersion = request.raw.httpVersion;
  const attributes: Attributes = {
    // Legacy HTTP semantic convention labels. TODO remove them
    'http.method': request.method,
    'http.status_code': statusCode,
    'http.flavor': protocolVersion,
    // Stable HTTP semantic convention labels.
    'http.request.method': request.method,
    'http.response.status_code': statusCode,
    'network.protocol.name': 'http',
    'network.protocol.version': protocolVersion,
  };

  const route = request.routeOptions?.url;
  if (route != null && route.length > 0) {
    attributes['http.route'] = route;
  }

  const scheme = request.protocol;
  if (scheme != null && scheme.length > 0) {
    attributes['url.scheme'] = scheme;
  }

  if (errorType != null && errorType.length > 0) {
    attributes['error.type'] = errorType;
  }

  return attributes;
};

const fastifyHttp2MetricsPluginImpl: FastifyPluginAsync = async (instance): Promise<void> => {
  const meter = metrics.getMeter(METER_NAME);
  const legacyRequestDuration = meter.createHistogram(LEGACY_REQUEST_DURATION_METRIC_NAME, {
    description: 'Duration of inbound HTTP requests handled by Fastify over HTTP/2.',
    unit: 'ms',
  });
  const stableRequestDuration = meter.createHistogram(STABLE_REQUEST_DURATION_METRIC_NAME, {
    description: 'Duration of inbound HTTP requests handled by Fastify over HTTP/2.',
    unit: 's',
  });

  const finishRequest = (request: FastifyRequest, statusCode: number, errorType?: string): void => {
    const startedAt = requestStartTimes.get(request);
    if (startedAt == null) {
      return;
    }

    requestStartTimes.delete(request);

    const durationNs = process.hrtime.bigint() - startedAt;
    const durationMs = Number(durationNs) / 1_000_000;
    const attributes = buildRequestAttributes(request, statusCode, errorType);

    legacyRequestDuration.record(durationMs, attributes);
    stableRequestDuration.record(durationMs / 1000, attributes);
  };

  instance.addHook('onRequest', async (request) => {
    if (request.raw.httpVersionMajor !== HTTP2_VERSION_MAJOR) {
      return;
    }

    requestStartTimes.set(request, process.hrtime.bigint());
  });

  instance.addHook('onResponse', async (request, reply) => {
    finishRequest(request, reply.statusCode);
  });

  instance.addHook('onTimeout', async (request) => {
    finishRequest(request, REQUEST_TIMEOUT_STATUS_CODE, 'timeout');
  });

  instance.addHook('onRequestAbort', async (request) => {
    finishRequest(request, CLIENT_CLOSED_REQUEST_STATUS_CODE, 'client.abort');
  });
};

export const fastifyHttp2MetricsPlugin = fastifyPlugin(fastifyHttp2MetricsPluginImpl, {
  name: 'commonex-fastify-http2-metrics',
});
