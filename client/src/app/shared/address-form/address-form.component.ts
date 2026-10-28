import { ChangeDetectionStrategy, Component, computed, effect, input, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';

import { TPipe } from '../../core/i18n/t.pipe';
import { MapViewComponent } from '../map-view/map-view.component';

export type AddressFormGroup = FormGroup<{
  street: FormControl<string>;
  postcode: FormControl<string>;
  city: FormControl<string>;
  country: FormControl<string>;
  latitude: FormControl<number | null>;
  longitude: FormControl<number | null>;
}>;

/** A new address form group (street, postcode, city, country, map position). */
export function addressFormGroup(): AddressFormGroup {
  return new FormGroup({
    street: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(120)] }),
    postcode: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(10)] }),
    city: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(80)] }),
    country: new FormControl('CH', { nonNullable: true, validators: [Validators.maxLength(2)] }),
    latitude: new FormControl<number | null>(null),
    longitude: new FormControl<number | null>(null),
  });
}

/**
 * Address of a store (S2/S15): street, postcode, city, country and the map position (drag the pin or click the map;
 * "use my location" asks the browser). The parent owns the form group.
 */
@Component({
  selector: 'fs-address-form',
  imports: [Button, InputText, MapViewComponent, ReactiveFormsModule, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="grid" [formGroup]="group()">
      <div class="field field--wide">
        <label [for]="id + '-street'">{{ 'shared.address.street' | t }}</label>
        <input pInputText [id]="id + '-street'" formControlName="street" autocomplete="street-address" [attr.data-testid]="testId() + '.street'" />
      </div>
      <div class="field">
        <label [for]="id + '-postcode'">{{ 'shared.address.postcode' | t }}</label>
        <input pInputText [id]="id + '-postcode'" formControlName="postcode" autocomplete="postal-code" [attr.data-testid]="testId() + '.postcode'" />
      </div>
      <div class="field">
        <label [for]="id + '-city'">{{ 'shared.address.city' | t }}</label>
        <input pInputText [id]="id + '-city'" formControlName="city" autocomplete="address-level2" [attr.data-testid]="testId() + '.city'" />
      </div>
      <div class="field">
        <label [for]="id + '-country'">{{ 'shared.address.country' | t }}</label>
        <input pInputText [id]="id + '-country'" formControlName="country" maxlength="2" autocomplete="country" [attr.data-testid]="testId() + '.country'" />
      </div>
    </div>
    <div class="position">
      <div class="position__head">
        <span class="position__label">{{ 'shared.address.position' | t }}</span>
        <span class="muted" [attr.data-testid]="testId() + '.coordinates'">
          @if (position(); as pos) {
            {{ pos[0].toFixed(5) }}, {{ pos[1].toFixed(5) }}
          } @else {
            {{ 'shared.address.noPosition' | t }}
          }
        </span>
        <p-button
          [label]="'shared.address.useLocation' | t"
          icon="pi pi-map-marker"
          size="small"
          severity="secondary"
          [text]="true"
          [loading]="locating()"
          (onClick)="useMyLocation()"
          [attr.data-testid]="testId() + '.locate'"
        />
      </div>
      <fs-map-view
        [position]="position()"
        [editable]="!group().disabled"
        [markers]="[]"
        [fitMarkers]="false"
        [center]="position() ?? undefined"
        [zoom]="position() ? 16 : 12"
        height="14rem"
        [ariaLabel]="'shared.address.mapLabel' | t"
        [testId]="testId() + '.map'"
        (positionChange)="setPosition($event)"
      />
      <span class="muted hint">{{ 'shared.address.positionHint' | t }}</span>
    </div>
  `,
  styles: `
    :host { display: grid; gap: 1rem; }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(10rem, 100%), 1fr)); gap: 0.75rem 1rem; }
    .field { display: grid; gap: 0.35rem; min-width: 0; }
    .field input { width: 100%; min-width: 0; }
    .field--wide { grid-column: 1 / -1; }
    .field > label { font-weight: 600; font-size: 0.9rem; }
    .position { display: grid; gap: 0.5rem; }
    .position__head { display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem 0.75rem; }
    .position__label { font-weight: 600; font-size: 0.9rem; }
    .muted { color: var(--fs-text-muted); font-size: 0.85rem; }
  `,
})
export class AddressFormComponent {
  private static nextId = 0;
  readonly group = input.required<AddressFormGroup>();
  readonly testId = input('shared.address');
  protected readonly id = `fs-address-${AddressFormComponent.nextId++}`;
  protected readonly locating = signal(false);
  /** Latest coordinates of the group (patched from outside, e.g. when the store loads, or by the map). */
  private readonly coordinates = signal<[number | null, number | null]>([null, null]);

  protected readonly position = computed<[number, number] | null>(() => {
    const [lat, lon] = this.coordinates();
    return lat != null && lon != null ? [lat, lon] : null;
  });

  constructor() {
    effect((onCleanup) => {
      const group = this.group();
      const sync = () => this.coordinates.set([group.controls.latitude.value, group.controls.longitude.value]);
      sync();
      const subscription = group.valueChanges.subscribe(sync);
      onCleanup(() => subscription.unsubscribe());
    });
  }

  protected setPosition([lat, lon]: [number, number]): void {
    const group = this.group();
    group.patchValue({ latitude: round(lat), longitude: round(lon) });
    group.markAsDirty();
  }

  protected useMyLocation(): void {
    const geo = typeof navigator !== 'undefined' ? navigator.geolocation : undefined;
    if (!geo) return;
    this.locating.set(true);
    geo.getCurrentPosition(
      (pos) => {
        this.locating.set(false);
        this.setPosition([pos.coords.latitude, pos.coords.longitude]);
      },
      () => this.locating.set(false),
      { timeout: 10000 },
    );
  }
}

function round(value: number): number {
  return Math.round(value * 1e6) / 1e6;
}
