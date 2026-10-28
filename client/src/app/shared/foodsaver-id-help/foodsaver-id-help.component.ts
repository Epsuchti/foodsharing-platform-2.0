import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';

import { TPipe } from '../../core/i18n/t.pipe';

/**
 * "What is my foodsaver ID?" (EA3): a small help button next to the number. The dialog explains where the ID is
 * (profile, passport, this box) and what it is used for (guides add past intro pickups with it, imports match it).
 */
@Component({
  selector: 'fs-foodsaver-id-help',
  imports: [Button, Dialog, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'fs-foodsaver-id-help' },
  template: `
    <p-button
      icon="pi pi-question-circle"
      [text]="true"
      [rounded]="true"
      size="small"
      [ariaLabel]="'shared.foodsaverId.help' | t"
      (onClick)="open.set(true)"
      data-testid="shared.foodsaverId.help"
    />
    <p-dialog
      [visible]="open()"
      (visibleChange)="open.set($event)"
      [modal]="true"
      [draggable]="false"
      [header]="'shared.foodsaverId.title' | t"
      [style]="{ width: 'min(30rem, 94vw)' }"
      data-testid="shared.foodsaverId.dialog"
    >
      @if (number()) {
        <p class="number" aria-live="polite">
          <span>{{ 'shared.foodsaverId.yours' | t }}</span>
          <strong data-testid="shared.foodsaverId.number">{{ number() }}</strong>
        </p>
      }
      <p>{{ 'shared.foodsaverId.what' | t }}</p>
      <ul>
        <li>{{ 'shared.foodsaverId.where1' | t }}</li>
        <li>{{ 'shared.foodsaverId.where2' | t }}</li>
        <li>{{ 'shared.foodsaverId.where3' | t }}</li>
      </ul>
    </p-dialog>
  `,
  styles: `
    :host {
      display: inline-flex;
    }
    .number {
      display: flex;
      align-items: baseline;
      gap: 0.6rem;
      padding: 0.75rem 1rem;
      border-radius: var(--fs-radius-md);
      background: var(--fs-primary-soft);
    }
    .number strong {
      color: var(--fs-primary-strong);
      font-size: 1.5rem;
      font-variant-numeric: tabular-nums;
      letter-spacing: 0.04em;
    }
    ul {
      margin: 0;
      padding-left: 1.2rem;
    }
    li + li {
      margin-top: 0.35rem;
    }
  `,
})
export class FoodsaverIdHelpComponent {
  readonly number = input<number | null | undefined>(undefined);
  protected readonly open = signal(false);
}
