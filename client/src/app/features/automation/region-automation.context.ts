import { Injectable, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { map } from 'rxjs';

/** Region id of `/regions/:regionId/automation/**`, provided by RegionAutomationPageComponent for its sections. */
@Injectable()
export class RegionAutomationContext {
  readonly regionId = toSignal(inject(ActivatedRoute).paramMap.pipe(map((p) => p.get('regionId') ?? undefined)), {
    initialValue: undefined,
  });
}
