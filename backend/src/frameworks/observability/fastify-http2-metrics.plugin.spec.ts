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
import fastify from 'fastify';
import {fastifyHttp2MetricsPlugin, LEGACY_REQUEST_DURATION_METRIC_NAME, STABLE_REQUEST_DURATION_METRIC_NAME,} from './fastify-http2-metrics.plugin';

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

const getHistogramCount = (metric: HistogramMetricData | null): number => {
  if (metric == null) {
    return 0;
  }

  return metric.dataPoints.reduce((sum, point) => sum + point.value.count, 0);
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

describe('fastifyHttp2MetricsPlugin', () => {
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

  it('records legacy and stable request duration metrics for HTTP/2 requests', async () => {
    const app = fastify({http2: true});

    try {
      await app.register(fastifyHttp2MetricsPlugin);
      app.get('/users/:id', async () => ({ok: true}));

      const serverAddress = await app.listen({host: '127.0.0.1', port: 0});
      const url = new URL(serverAddress);
      const statusCode = await sendHttp2Get(Number(url.port), '/users/123');

      expect(statusCode).toBe(200);

      await new Promise((resolve) => setTimeout(resolve, 50));

      await meterProvider.forceFlush();
      await metricReader.collect();

      const resourceMetrics = metricExporter.getMetrics();
      const legacyMetric = findHistogramMetricByName(resourceMetrics, LEGACY_REQUEST_DURATION_METRIC_NAME);
      const stableMetric = findHistogramMetricByName(resourceMetrics, STABLE_REQUEST_DURATION_METRIC_NAME);

      expect(legacyMetric).not.toBeNull();
      expect(stableMetric).not.toBeNull();

      const legacyPoint = legacyMetric?.dataPoints.find(({attributes}) => {
        return (
          attributes['http.method'] === 'GET' &&
          attributes['http.status_code'] === 200 &&
          attributes['http.route'] === '/users/:id' &&
          attributes['http.flavor'] === '2.0'
        );
      });

      expect(legacyPoint).toBeDefined();
      expect(legacyPoint?.value.count).toBeGreaterThan(0);

      const stablePoint = stableMetric?.dataPoints.find(({attributes}) => {
        return (
          attributes['http.request.method'] === 'GET' &&
          attributes['http.response.status_code'] === 200 &&
          attributes['http.route'] === '/users/:id' &&
          attributes['network.protocol.name'] === 'http' &&
          attributes['network.protocol.version'] === '2.0'
        );
      });

      expect(stablePoint).toBeDefined();
      expect(stablePoint?.value.count).toBeGreaterThan(0);
    } finally {
      await app.close();
    }
  });

  it('does not record request duration metrics for HTTP/1.1 traffic', async () => {
    const app = fastify();

    try {
      await app.register(fastifyHttp2MetricsPlugin);
      app.get('/users/:id', async () => ({ok: true}));

      const response = await app.inject({
        method: 'GET',
        url: '/users/123',
      });

      expect(response.statusCode).toBe(200);

      await meterProvider.forceFlush();
      await metricReader.collect();

      const resourceMetrics = metricExporter.getMetrics();

      expect(getHistogramCount(findHistogramMetricByName(resourceMetrics, LEGACY_REQUEST_DURATION_METRIC_NAME))).toBe(
        0,
      );
      expect(getHistogramCount(findHistogramMetricByName(resourceMetrics, STABLE_REQUEST_DURATION_METRIC_NAME))).toBe(
        0,
      );
    } finally {
      await app.close();
    }
  });
});
