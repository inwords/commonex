import {Injectable} from '@nestjs/common';
import {UseCase} from '#packages/use-case';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {LessThan} from 'typeorm';

type Input = void;
type Output = void;

@Injectable()
export class CleanupIdempotencyKeysUseCase implements UseCase<Input, Output> {
  constructor(private readonly rDataService: RelationalDataServiceAbstract) {}

  public async execute(): Promise<void> {
    await this.rDataService.idempotencyKey.delete({expiresAt: LessThan(new Date())});
  }
}
