import {DeleteEventResponseDto} from '#api/http/user/dto/delete-event.dto';

import {toIsoString} from './iso-date';

export interface DeleteEventGrpcResponseDto {
  id: string;
  deletedAt: string;
}

export const toDeleteEventGrpcResponse = (deletedEvent: DeleteEventResponseDto): DeleteEventGrpcResponseDto => ({
  id: deletedEvent.id,
  deletedAt: toIsoString(deletedEvent.deletedAt),
});
