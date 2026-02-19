import {metrics} from '@opentelemetry/api';
import {
  AggregationTemporality,
  DataPointType,
  HistogramMetricData,
  InMemoryMetricExporter,
  MeterProvider,
  MetricData,
  PeriodicExportingMetricReader,
  ResourceMetrics,
} from '@opentelemetry/sdk-metrics';
import {connect} from 'http2';
import {request as httpRequest} from 'http';
import {createConnection} from 'net';
import fastify from 'fastify';
import {fastifyHttpMetricsPlugin, HTTP_SERVER_REQUEST_DURATION} from './fastify-http-metrics.plugin';

const findHistogramMetricByName = (resourceMetrics: ResourceMetrics[], name: string): HistogramMetricData | null => {
  for (const resourceMetric of resourceMetrics) {
    for (const scopeMetric of resourceMetric.scopeMetrics) {
      const metric: MetricData | undefined = scopeMetric.metrics.find(({descriptor}) => descriptor.name === name);
      if (metric != null && metric.dataPointType === DataPointType.HISTOGRAM) {
        return metric;
      }
    }
  }

  return null;
};

const sleep = (ms: number): Promise<void> => {
  return new Promise((resolve) => setTimeout(resolve, ms));
};

const collectHistogramDataPoints = async (
  meterProvider: MeterProvider,
  metricReader: PeriodicExportingMetricReader,
  metricExporter: InMemoryMetricExporter,
  metricName: string,
) => {
  await meterProvider.forceFlush();
  await metricReader.collect();

  const resourceMetrics = metricExporter.getMetrics();
  const histogram = findHistogramMetricByName(resourceMetrics, metricName);
  return histogram?.dataPoints ?? [];
};

const sendHttp2Get = (port: number, path: string): Promise<number> => {
  return new Promise((resolve, reject) => {
    const client = connect(`http://127.0.0.1:${port}`);
    const request = client.request({
      ':method': 'GET',
      ':path': path,
    });

    let statusCode = 0;
    const timeout = setTimeout(() => {
      request.close();
      client.close();
      reject(new Error(`Timed out while requesting ${path}`));
    }, 5_000);

    client.once('error', (error) => {
      clearTimeout(timeout);
      request.close();
      client.close();
      reject(error);
    });

    request.once('response', (headers) => {
      statusCode = Number(headers[':status'] ?? 0);
    });

    request.on('data', () => {
      // Consume response body to allow stream completion.
    });

    request.once('error', (error) => {
      clearTimeout(timeout);
      client.close();
      reject(error);
    });

    request.once('end', () => {
      clearTimeout(timeout);
      client.close();
      resolve(statusCode);
    });

    request.end();
  });
};

const sendHttp1Get = (port: number, path: string): Promise<void> => {
  return new Promise((resolve) => {
    const request = httpRequest(
      {
        host: '127.0.0.1',
        port,
        method: 'GET',
        path,
      },
      (response) => {
        response.on('data', () => {
          // Consume response body to allow stream completion.
        });
        response.once('end', resolve);
      },
    );

    request.once('error', () => resolve());
    request.end();
  });
};

const sendPartialBodyAndAbort = (port: number, path: string): Promise<void> => {
  return new Promise((resolve) => {
    let settled = false;
    const done = (): void => {
      if (settled) return;
      settled = true;
      resolve();
    };

    const socket = createConnection({host: '127.0.0.1', port});
    socket.once('error', done);
    socket.once('close', done);
    socket.once('connect', () => {
      const request = ['POST ' + path + ' HTTP/1.1', 'Host: 127.0.0.1', 'Content-Type: application/json', 'Content-Length: 100000', '', '{"a":"x"'].join(
        '\r\n',
      );
      socket.write(request);
      setTimeout(() => {
        socket.destroy();
      }, 30);
      setTimeout(done, 700);
    });
  });
};

describe('fastifyHttpMetricsPlugin', () => {
  let meterProvider: MeterProvider;
  let metricExporter: InMemoryMetricExporter;
  let metricReader: PeriodicExportingMetricReader;

  beforeEach(() => {
    metrics.disable();

    metricExporter = new InMemoryMetricExporter(AggregationTemporality.CUMULATIVE);
    metricReader = new PeriodicExportingMetricReader({
      exporter: metricExporter,
      exportIntervalMillis: 5_000,
      exportTimeoutMillis: 1_000,
    });
    meterProvider = new MeterProvider({
      readers: [metricReader],
    });

    metrics.setGlobalMeterProvider(meterProvider);
  });

  afterEach(async () => {
    if (meterProvider != null) {
      await meterProvider.shutdown();
    }
    metrics.disable();
  });

  it('records request duration metrics for HTTP/2 requests', async () => {
    const app = fastify({http2: true});

    try {
      await app.register(fastifyHttpMetricsPlugin);
      app.get('/users/:id', async () => ({ok: true}));

      const serverAddress = await app.listen({host: '127.0.0.1', port: 0});
      const url = new URL(serverAddress);
      const statusCode = await sendHttp2Get(Number(url.port), '/users/123');

      expect(statusCode).toBe(200);

      await sleep(50);

      const requestDurationDataPoints = await collectHistogramDataPoints(
        meterProvider,
        metricReader,
        metricExporter,
        HTTP_SERVER_REQUEST_DURATION,
      );

      const http2Point = requestDurationDataPoints.find(({attributes}) => {
        return (
          attributes['http.request.method'] === 'GET' &&
          attributes['http.response.status_code'] === 200 &&
          attributes['http.route'] === '/users/:id' &&
          attributes['network.protocol.name'] === 'http' &&
          attributes['network.protocol.version'] === '2'
        );
      });

      expect(http2Point).toBeDefined();
      expect(http2Point?.value.count).toBeGreaterThan(0);
    } finally {
      await app.close();
    }
  });

  it('records request duration metrics for HTTP/1.1 traffic', async () => {
    const app = fastify();

    try {
      await app.register(fastifyHttpMetricsPlugin);
      app.get('/users/:id', async () => ({ok: true}));

      const response = await app.inject({
        method: 'GET',
        url: '/users/123',
      });

      expect(response.statusCode).toBe(200);

      const requestDurationDataPoints = await collectHistogramDataPoints(
        meterProvider,
        metricReader,
        metricExporter,
        HTTP_SERVER_REQUEST_DURATION,
      );

      const http1Point = requestDurationDataPoints.find(({attributes}) => {
        return (
          attributes['http.request.method'] === 'GET' &&
          attributes['http.response.status_code'] === 200 &&
          attributes['http.route'] === '/users/:id' &&
          attributes['network.protocol.name'] === 'http' &&
          attributes['network.protocol.version'] === '1.1'
        );
      });

      expect(http1Point).toBeDefined();
      expect(http1Point?.value.count).toBeGreaterThan(0);
    } finally {
      await app.close();
    }
  });

  it('applies applicationRoot to route attributes when configured', async () => {
    const app = fastify();

    try {
      await app.register(fastifyHttpMetricsPlugin, {
        applicationRoot: '/api',
      });
      app.get('/users/:id', async () => ({ok: true}));

      const response = await app.inject({
        method: 'GET',
        url: '/users/123',
      });

      expect(response.statusCode).toBe(200);

      const requestDurationDataPoints = await collectHistogramDataPoints(
        meterProvider,
        metricReader,
        metricExporter,
        HTTP_SERVER_REQUEST_DURATION,
      );

      const point = requestDurationDataPoints.find(({attributes}) => {
        return attributes['http.response.status_code'] === 200 && attributes['http.route'] === '/api/users/:id';
      });

      expect(point).toBeDefined();
      expect(point?.value.count).toBeGreaterThan(0);
    } finally {
      await app.close();
    }
  });

  it('sets server_error only for 5xx responses', async () => {
    const app = fastify();

    try {
      await app.register(fastifyHttpMetricsPlugin);
      app.get('/boom', async () => {
        throw new Error('boom');
      });
      app.get('/not-found', async (_request, reply) => {
        return reply.code(404).send({ok: false});
      });

      const boom = await app.inject({method: 'GET', url: '/boom'});
      const notFound = await app.inject({method: 'GET', url: '/not-found'});
      expect(boom.statusCode).toBe(500);
      expect(notFound.statusCode).toBe(404);

      const requestDurationDataPoints = await collectHistogramDataPoints(
        meterProvider,
        metricReader,
        metricExporter,
        HTTP_SERVER_REQUEST_DURATION,
      );

      const serverErrorPoint = requestDurationDataPoints.find(({attributes}) => {
        return (
          attributes['http.route'] === '/boom' &&
          attributes['http.response.status_code'] === 500 &&
          attributes['error.type'] === 'server_error'
        );
      });
      const clientErrorPoint = requestDurationDataPoints.find(({attributes}) => {
        return attributes['http.route'] === '/not-found' && attributes['http.response.status_code'] === 404;
      });

      expect(serverErrorPoint).toBeDefined();
      expect(clientErrorPoint).toBeDefined();
      expect(clientErrorPoint?.attributes['error.type']).toBeUndefined();
    } finally {
      await app.close();
    }
  });

  it('records timeout requests with timeout error.type', async () => {
    const app = fastify({connectionTimeout: 50});

    try {
      await app.register(fastifyHttpMetricsPlugin);
      app.get('/slow', async () => {
        await sleep(300);
        return {ok: true};
      });

      const serverAddress = await app.listen({host: '127.0.0.1', port: 0});
      const url = new URL(serverAddress);
      await sendHttp1Get(Number(url.port), '/slow');
      await sleep(50);

      const requestDurationDataPoints = await collectHistogramDataPoints(
        meterProvider,
        metricReader,
        metricExporter,
        HTTP_SERVER_REQUEST_DURATION,
      );

      const timeoutPoint = requestDurationDataPoints.find(({attributes}) => {
        return attributes['http.request.method'] === 'GET' && attributes['http.route'] === '/slow' && attributes['error.type'] === 'timeout';
      });

      expect(timeoutPoint).toBeDefined();
      expect(timeoutPoint?.value.count).toBeGreaterThan(0);
    } finally {
      await app.close();
    }
  });

  it('records client abort requests with client.abort error.type', async () => {
    const app = fastify();

    try {
      await app.register(fastifyHttpMetricsPlugin);
      app.post('/upload', async () => ({ok: true}));

      const serverAddress = await app.listen({host: '127.0.0.1', port: 0});
      const url = new URL(serverAddress);
      await sendPartialBodyAndAbort(Number(url.port), '/upload');
      await sleep(50);

      const requestDurationDataPoints = await collectHistogramDataPoints(
        meterProvider,
        metricReader,
        metricExporter,
        HTTP_SERVER_REQUEST_DURATION,
      );

      const abortPoint = requestDurationDataPoints.find(({attributes}) => {
        return (
          attributes['http.request.method'] === 'POST' &&
          attributes['http.route'] === '/upload' &&
          attributes['error.type'] === 'client.abort'
        );
      });

      expect(abortPoint).toBeDefined();
      expect(abortPoint?.value.count).toBeGreaterThan(0);
    } finally {
      await app.close();
    }
  });
});
