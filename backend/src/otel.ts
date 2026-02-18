import {NodeSDK} from '@opentelemetry/sdk-node';
import {getNodeAutoInstrumentations} from '@opentelemetry/auto-instrumentations-node';
import {OTLPTraceExporter} from '@opentelemetry/exporter-trace-otlp-grpc';
import {OTLPMetricExporter} from '@opentelemetry/exporter-metrics-otlp-grpc';
import {PeriodicExportingMetricReader} from '@opentelemetry/sdk-metrics';
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

const sdk = new NodeSDK({
  serviceName: env.OTEL_SERVICE_NAME,
  traceExporter: traceExporter,
  instrumentations: [
    getNodeAutoInstrumentations({
      '@opentelemetry/instrumentation-fastify': {enabled: true},
    }),
  ],
  metricReaders: [metricReader],
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
