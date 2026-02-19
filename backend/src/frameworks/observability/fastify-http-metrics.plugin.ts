import {Attributes, metrics} from '@opentelemetry/api';
import type {FastifyInstance, FastifyPluginCallback, FastifyReply, FastifyRequest, RouteHandlerMethod} from 'fastify';
import fastifyPlugin from 'fastify-plugin';
import {
  ATTR_ERROR_TYPE,
  ATTR_HTTP_REQUEST_METHOD,
  ATTR_HTTP_RESPONSE_STATUS_CODE,
  ATTR_HTTP_ROUTE,
  ATTR_NETWORK_PROTOCOL_NAME,
  ATTR_NETWORK_PROTOCOL_VERSION,
  ATTR_URL_SCHEME,
  HTTP_REQUEST_METHOD_VALUE_OTHER,
} from '@opentelemetry/semantic-conventions';
import {IncomingMessage} from 'node:http';

/**
 * Fastify HTTP server metrics (OTel semconv).
 *
 * DESIGN DECISIONS (intentional constraints for trustworthiness):
 * 1) Designed for h2c behind a proxy (e.g. Nginx 1.29.5+).
 *
 * 2) No synthetic data:
 *    - Never invent status codes (no 499/408). We only set http.response.status_code on onResponse.
 *    - http.route is emitted ONLY when we have a low-cardinality route template from Fastify (or a proven
 *      handler->template mapping captured from onRoute). We never fall back to raw URL/path.
 *    - If termination is ambiguous (raw 'close' without a terminal Fastify hook), we only decrement
 *      active_requests and DO NOT record a duration point.
 *
 * 3) Error semantics:
 *    - We do NOT set error.type for 4xx.
 *    - We set error.type only for:
 *      - 5xx (server_error)
 *      - onTimeout (timeout)
 *      - onRequestAbort (client.abort)
 *    - We do NOT use error.name in metrics to avoid accidental high-cardinality.
 *
 * 4) State storage:
 *    - WeakMap<FastifyRequest, State> only.
 *    - No Symbol properties, no fallback maps, no try/catch-based hot paths.
 *
 * 5) Exemplars:
 *    - VictoriaMetrics doesn’t support exemplars in practice; implementing exemplar-focused behavior
 *      in the plugin is not useful. We still keep instrumentation compatible with contexts naturally
 *      (OTel SDK handles exemplars when supported by backend), but we do not add complexity here.
 *
 * 6) Body size metrics:
 *    - Not implemented: too easy to get wrong with streaming, compression, missing content-length,
 *      and abort paths. (Prefer "no metric" to "semi-junk".)
 *
 * OUTSIDE-PLUGIN REQUIREMENT:
 * - Configure histogram bucket boundaries via OTel SDK Views for http.server.request.duration.
 */

const METER_NAME = 'commonex-backend.fastify-http';
const METER_VERSION = '1.0.0'; // Hardcoded intentionally; bump when behavior/semantics change.

export const HTTP_SERVER_REQUEST_DURATION = 'http.server.request.duration';
export const HTTP_SERVER_ACTIVE_REQUESTS = 'http.server.active_requests';

export interface FastifyHttpMetricsPluginOptions {
  /**
   * Optional application root prefix (e.g. Nest global prefix like "/api").
   * We do not infer this; inference would be guesswork.
   */
  applicationRoot?: string;
}

type FinishKind = 'response' | 'timeout' | 'client_abort' | 'unknown_close';

interface RequestState {
  readonly startedAtNs: bigint;
  readonly baseAttrs: Attributes; // method+scheme only (stable, low-card)
  detachCloseListener?: () => void;
  sawError?: boolean; // set on onError, used only when deciding server_error
}

const isValidHttpStatusCode = (code: number) => {
  return code >= 100 && code <= 599;
};

const assertNever = (value: never): never => {
  throw new Error(`Unhandled FinishKind: ${String(value)}`);
};

const normalizeApplicationRoot = (root?: string): string | undefined => {
  if (root == null) return undefined;
  const t = root.trim();
  if (t.length === 0) return undefined;
  if (t === '/') return '/';
  const withLeading = t.startsWith('/') ? t : `/${t}`;
  return withLeading.endsWith('/') ? withLeading.slice(0, -1) : withLeading;
};

// Avoid "/api" matching "/apiary"
const applyApplicationRoot = (root: string | undefined, routeTemplate: string): string => {
  if (root == null || root === '/' || routeTemplate === root) return routeTemplate;
  if (routeTemplate.startsWith(`${root}/`)) return routeTemplate;
  return routeTemplate.startsWith('/') ? `${root}${routeTemplate}` : `${root}/${routeTemplate}`;
};

const KNOWN_METHODS = new Set([
  'GET',
  'POST',
  'PUT',
  'DELETE',
  'HEAD',
  'OPTIONS',
  'TRACE',
  'CONNECT',
  'PATCH',
  'QUERY',
]);

const normalizeMethod = (method: string): string => {
  return KNOWN_METHODS.has(method) ? method : HTTP_REQUEST_METHOD_VALUE_OTHER;
};

const normalizeProtocolVersion = (httpVersion: string, httpVersionMajor: number): string => {
  // OTel semconv expects "2"/"3" (not "2.0"/"3.0"), while keeping "1.1"/"1.0".
  return httpVersionMajor >= 2 ? String(httpVersionMajor) : httpVersion;
};

const attachCloseListener = (raw: IncomingMessage, listener: () => void): (() => void) => {
  raw.on('close', listener);
  return () => raw.removeListener('close', listener);
};

const createRouteResolver = (instance: FastifyInstance) => {
  /**
   * Route template resolver:
   * - Primary: request.routeOptions.url (Fastify-provided route template)
   * - Secondary: handler->template mapping observed at registration time (onRoute), but ONLY if
   *   the handler is uniquely associated with a single template.
   *
   * If neither is available, we omit http.route.
   */
  const handlerToRoute = new WeakMap<RouteHandlerMethod, string | null>();

  instance.addHook('onRoute', (routeOptions) => {
    const url = routeOptions.url;
    if (url.length === 0) return;

    const handler = routeOptions.handler;
    const existing = handlerToRoute.get(handler);
    if (existing === undefined) {
      handlerToRoute.set(handler, url);
    } else if (existing !== null && existing !== url) {
      handlerToRoute.set(handler, null); // ambiguous mapping => omit later
    }
  });

  return (request: FastifyRequest): string | undefined => {
    const direct = request.routeOptions?.url;
    if (direct && direct.length > 0) return direct;

    const mapped = handlerToRoute.get(request.routeOptions?.handler);
    if (mapped && mapped.length > 0) return mapped;

    return undefined;
  };
};

const fastifyHttpMetricsPluginImpl: FastifyPluginCallback<FastifyHttpMetricsPluginOptions> = (instance, opts, done) => {
  const applicationRoot = normalizeApplicationRoot(opts.applicationRoot);

  const meter = metrics.getMeter(METER_NAME, METER_VERSION);

  const requestDuration = meter.createHistogram(HTTP_SERVER_REQUEST_DURATION, {
    description: 'Duration of inbound HTTP requests handled by Fastify.',
    unit: 's',
  });

  const activeRequests = meter.createUpDownCounter(HTTP_SERVER_ACTIVE_REQUESTS, {
    description: 'Number of active HTTP server requests.',
    unit: '{request}',
  });

  const stateByRequest = new WeakMap<FastifyRequest, RequestState>();
  const resolveRoute = createRouteResolver(instance);

  const finish = (request: FastifyRequest, kind: FinishKind, statusCode?: number | undefined): void => {
    const state = stateByRequest.get(request);
    if (state == null) return;

    // Idempotent: first finisher wins.
    stateByRequest.delete(request);

    // Always detach close listener if installed.
    state.detachCloseListener?.();
    state.detachCloseListener = undefined;

    // Always decrement active_requests for any terminal/cleanup path.
    activeRequests.add(-1, state.baseAttrs);

    // Ambiguous termination => no duration point (trustworthiness rule).
    switch (kind) {
      case 'unknown_close':
        return;
      case 'response':
      case 'timeout':
      case 'client_abort':
        break;
      default:
        assertNever(kind);
    }

    const durationNs = process.hrtime.bigint() - state.startedAtNs;
    if (durationNs <= 0n) return;

    const attrs: Attributes = {...state.baseAttrs};

    const routeTemplate = resolveRoute(request);
    if (routeTemplate != null) {
      attrs[ATTR_HTTP_ROUTE] = applyApplicationRoot(applicationRoot, routeTemplate);
    }

    attrs[ATTR_NETWORK_PROTOCOL_NAME] = 'http';
    attrs[ATTR_NETWORK_PROTOCOL_VERSION] = normalizeProtocolVersion(request.raw.httpVersion, request.raw.httpVersionMajor);

    switch (kind) {
      case 'response':
        if (statusCode && isValidHttpStatusCode(statusCode)) {
          attrs[ATTR_HTTP_RESPONSE_STATUS_CODE] = statusCode;

          // Only 5xx is server error.
          if (statusCode >= 500) {
            attrs[ATTR_ERROR_TYPE] = 'server_error';
          }
        } else if (state.sawError === true) {
          // If onError fired but status is missing/invalid, classify as server_error.
          attrs[ATTR_ERROR_TYPE] = 'server_error';
        }
        break;
      case 'timeout':
        attrs[ATTR_ERROR_TYPE] = 'timeout';
        break;
      case 'client_abort':
        attrs[ATTR_ERROR_TYPE] = 'client.abort';
        break;
      default:
        assertNever(kind);
    }

    requestDuration.record(Number(durationNs) / 1_000_000_000, attrs);
  };

  instance.addHook('onRequest', (request, _reply, hookDone) => {
    const baseAttrs: Attributes = {
      [ATTR_HTTP_REQUEST_METHOD]: normalizeMethod(request.method),
      [ATTR_URL_SCHEME]: request.protocol,
    };

    const state: RequestState = {
      startedAtNs: process.hrtime.bigint(),
      baseAttrs,
    };

    // Active requests increments at request start.
    activeRequests.add(1, baseAttrs);

    // Escape hatch: if we never get a terminal Fastify hook, ensure active_requests cannot leak.
    // Trustworthiness rule: close-only cleanup does NOT record duration.
    state.detachCloseListener = attachCloseListener(request.raw, () => {
      // `request.raw` can emit `close` before Fastify terminal hooks on HTTP/2.
      // Delay fallback cleanup one macrotask to let onResponse/onTimeout/onRequestAbort win first.
      setImmediate(() => {
        finish(request, 'unknown_close');
      });
    });

    stateByRequest.set(request, state);
    hookDone();
  });

  instance.addHook('onError', (request, _reply, _error, hookDone) => {
    const state = stateByRequest.get(request);
    if (state) state.sawError = true;
    hookDone();
  });

  instance.addHook('onResponse', (request, reply: FastifyReply, hookDone) => {
    finish(request, 'response', reply.statusCode);
    hookDone();
  });

  instance.addHook('onTimeout', (request, _reply, hookDone) => {
    finish(request, 'timeout');
    hookDone();
  });

  instance.addHook('onRequestAbort', (request, hookDone) => {
    finish(request, 'client_abort');
    hookDone();
  });

  done();
};

export const fastifyHttpMetricsPlugin = fastifyPlugin(fastifyHttpMetricsPluginImpl, {
  name: 'commonex-fastify-http-metrics',
});
