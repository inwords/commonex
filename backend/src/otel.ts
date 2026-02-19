import {NodeSDK} from '@opentelemetry/sdk-node';
import {getNodeAutoInstrumentations} from '@opentelemetry/auto-instrumentations-node';
import {OTLPTraceExporter} from '@opentelemetry/exporter-trace-otlp-grpc';
import {OTLPMetricExporter} from '@opentelemetry/exporter-metrics-otlp-grpc';
import {AggregationType, InstrumentType, PeriodicExportingMetricReader} from '@opentelemetry/sdk-metrics';
import FastifyOtelInstrumentation from '@fastify/otel';
import {env} from './config';

const traceExporter = new OTLPTraceExporter({
  url: 'http://otelcollector:4317',
});

const metricExporter = new OTLPMetricExporter({
  url: 'http://otelcollector:4317',
});

const metricReader = new PeriodicExportingMetricReader({
  exporter: metricExporter,
  exportIntervalMillis: 5000,
});

const httpServerRequestDurationBucketsSeconds = [0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1, 2.5, 5, 7.5, 10];

export const fastifyOtelInstrumentation = new FastifyOtelInstrumentation();

const allowedAutoInstrumentationNames = new Set<string>([
  '@opentelemetry/instrumentation-grpc',
  '@opentelemetry/instrumentation-pg',
  '@opentelemetry/instrumentation-nestjs-core',
  '@opentelemetry/instrumentation-runtime-node',
]);

const autoInstrumentations = getNodeAutoInstrumentations().filter(({instrumentationName}) =>
  allowedAutoInstrumentationNames.has(instrumentationName),
);

const sdk = new NodeSDK({
  serviceName: env.OTEL_SERVICE_NAME,
  traceExporter: traceExporter,
  instrumentations: [...autoInstrumentations, fastifyOtelInstrumentation],
  metricReaders: [metricReader],
  views: [
    {
      meterName: 'commonex-backend.fastify-http',
      instrumentName: 'http.server.request.duration',
      instrumentType: InstrumentType.HISTOGRAM,
      aggregation: {
        type: AggregationType.EXPLICIT_BUCKET_HISTOGRAM,
        options: {
          boundaries: httpServerRequestDurationBucketsSeconds,
        },
      },
    },
  ],
});

sdk.start();

const shutdown = async (signal: NodeJS.Signals): Promise<void> => {
  await sdk.shutdown();
  console.log(`OpenTelemetry shut down (${signal})`);
  process.exit(0);
};

process.once('SIGTERM', () => {
  void shutdown('SIGTERM');
});

process.once('SIGINT', () => {
  void shutdown('SIGINT');
});
