import {CanActivate, ExecutionContext, Injectable, UnauthorizedException} from '@nestjs/common';
import {IncomingHttpHeaders} from 'http';
import {env} from '../../../../config';

type RequestWithHeaders = {
  headers: IncomingHttpHeaders;
};

@Injectable()
export class DevtoolsSecretGuard implements CanActivate {
  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<RequestWithHeaders>();
    const secretHeader = request.headers['x-devtools-secret'];
    const secret = Array.isArray(secretHeader) ? secretHeader[0] : secretHeader;

    if (!env.DEVTOOLS_SECRET) {
      throw new UnauthorizedException('Devtools secret is not configured');
    }

    if (!secret || secret !== env.DEVTOOLS_SECRET) {
      throw new UnauthorizedException('Invalid devtools secret');
    }

    return true;
  }
}
