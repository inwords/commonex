import {ExecutionContext, UnauthorizedException} from '@nestjs/common';

import {DevtoolsSecretGuard} from '#api/http/devtools/guards/devtools-secret.guard';

import {env} from '../../../../../config';

const contextWithHeaders = (headers: Record<string, string | string[] | undefined>): ExecutionContext =>
  ({switchToHttp: () => ({getRequest: () => ({headers})})}) as unknown as ExecutionContext;

describe('DevtoolsSecretGuard', () => {
  const guard = new DevtoolsSecretGuard();

  it('allows a request carrying the configured secret', () => {
    expect(guard.canActivate(contextWithHeaders({'x-devtools-secret': env.DEVTOOLS_SECRET}))).toBe(true);
  });

  it('uses the first value of a repeated header', () => {
    expect(guard.canActivate(contextWithHeaders({'x-devtools-secret': [env.DEVTOOLS_SECRET, 'other']}))).toBe(true);
  });

  it('rejects a missing header', () => {
    expect(() => guard.canActivate(contextWithHeaders({}))).toThrow(UnauthorizedException);
  });

  it('rejects a wrong secret', () => {
    expect(() => guard.canActivate(contextWithHeaders({'x-devtools-secret': `${env.DEVTOOLS_SECRET}x`}))).toThrow(
      'Invalid devtools secret',
    );
  });

  it('rejects everything when no secret is configured', () => {
    jest.replaceProperty(env, 'DEVTOOLS_SECRET', '');

    expect(() => guard.canActivate(contextWithHeaders({'x-devtools-secret': ''}))).toThrow(
      'Devtools secret is not configured',
    );
  });
});
