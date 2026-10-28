import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { Textarea } from '@openng/optimus-ui/textarea';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';
import { catchError, forkJoin, of, throwError } from 'rxjs';

import { RegionOptionPermissions, RegionPin, RegionsService } from '../../../api';
import { toApiErrorView } from '../../../core/data/api-error';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { MapViewComponent } from '../../../shared/map-view/map-view.component';
import { RegionPageContext } from '../region-page.context';

interface PinData {
  pin: RegionPin | null;
  permissions: RegionOptionPermissions;
}

const ZURICH: [number, number] = [47.3769, 8.5417];

/**
 * Pin tab (R6): the region's marker on the public map. The PR function group (else ambassadors) moves it on the
 * map (click or drag), writes a description and switches it on or off; others see it read-only.
 */
@Component({
  selector: 'fs-region-pin-tab',
  imports: [...DATA_STATE_IMPORTS, Button, FormsModule, MapViewComponent, TPipe, Textarea, ToggleSwitch],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state [state]="data" skeleton="card" testId="regions.pin.state" data-testid="regions.pin.state">
      <ng-template [fsData]="data" let-d>
        <div class="cols">
          <section class="fs-card card">
            <h2 class="card__title"><i class="pi pi-map-marker" aria-hidden="true"></i>{{ 'regions.pin.title' | t }}</h2>
            @if (d.permissions.mayEditPin) {
              <p class="muted">{{ 'regions.pin.editHint' | t }}</p>
            }
            <fs-map-view
              [position]="position()"
              [editable]="d.permissions.mayEditPin"
              (positionChange)="position.set($event)"
              [zoom]="13"
              height="22rem"
              [ariaLabel]="'regions.pin.mapLabel' | t"
              testId="regions.pin.map"
            />
            @if (!position()) {
              <p class="muted">{{ 'regions.pin.none' | t }}</p>
            }
          </section>
          <section class="fs-card card">
            <h2 class="card__title"><i class="pi pi-pencil" aria-hidden="true"></i>{{ 'regions.pin.details' | t }}</h2>
            @if (!d.permissions.mayEditPin) {
              <p class="locked"><i class="pi pi-lock" aria-hidden="true"></i>{{ 'regions.pin.who' | t }}</p>
            }
            <div class="switch-row">
              <span class="switch-row__text">
                <label class="switch-row__label" for="pin-active">{{ 'regions.pin.active' | t }}</label>
                <span class="muted">{{ 'regions.pin.activeHint' | t }}</span>
              </span>
              <p-toggleswitch inputId="pin-active" [ngModel]="active()" (ngModelChange)="active.set($event)" [disabled]="!d.permissions.mayEditPin" data-testid="regions.pin.active" />
            </div>
            <div class="field">
              <label for="pin-description">{{ 'regions.pin.description' | t }}</label>
              <textarea pTextarea id="pin-description" rows="5" maxlength="2000" [ngModel]="description()" (ngModelChange)="description.set($event)" [disabled]="!d.permissions.mayEditPin" data-testid="regions.pin.description"></textarea>
            </div>
            @if (position(); as here) {
              <p class="muted number" data-testid="regions.pin.coordinates">{{ here[0].toFixed(5) }}, {{ here[1].toFixed(5) }}</p>
            }
            @if (d.permissions.mayEditPin) {
              <div class="actions">
                <p-button [label]="'common.save' | t" icon="pi pi-check" [disabled]="!position()" [loading]="save.busy()" (onClick)="save.run()" data-testid="regions.pin.save" />
              </div>
            }
          </section>
        </div>
      </ng-template>
    </fs-data-state>
  `,
  styleUrl: './tab.scss',
  styles: `
    textarea {
      width: 100%;
    }
  `,
})
export class RegionPinTab {
  private readonly ctx = inject(RegionPageContext);
  private readonly api = inject(RegionsService);

  protected readonly position = signal<[number, number] | null>(null);
  protected readonly active = signal(true);
  protected readonly description = signal('');

  protected readonly data = loadResource<PinData, string>({
    params: () => this.ctx.regionId() ?? undefined,
    loader: (regionId) =>
      forkJoin({
        // no pin yet: 404 → null (the editor starts from the region's centre)
        pin: this.api
          .getRegionPin({ regionId })
          .pipe(catchError((error: unknown) => (toApiErrorView(error).status === 404 ? of(null) : throwError(() => error)))),
        permissions: this.api.getRegionOptionPermissions({ regionId }),
      }),
    isEmpty: () => false,
  });

  protected readonly editable = computed(() => !!this.data.data()?.permissions.mayEditPin);

  constructor() {
    effect(() => {
      const value = this.data.data();
      if (!value) {
        return;
      }
      const pin = value.pin;
      this.position.set(pin ? [pin.latitude, pin.longitude] : value.permissions.mayEditPin ? ZURICH : null);
      this.active.set(pin?.active ?? true);
      this.description.set(pin?.description ?? '');
    });
  }

  protected readonly save = runMutation(
    () => {
      const [latitude, longitude] = this.position()!;
      return this.api.updateRegionPin({
        regionId: this.ctx.regionId()!,
        regionPin: { latitude, longitude, active: this.active(), description: this.description().trim() || undefined },
      });
    },
    { successToastKey: 'regions.pin.saved' },
  );
}
