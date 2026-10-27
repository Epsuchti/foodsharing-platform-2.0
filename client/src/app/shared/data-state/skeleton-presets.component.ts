import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Skeleton } from '@openng/optimus-ui/skeleton';

export type SkeletonPreset = 'list' | 'card' | 'table' | 'form' | 'detail' | 'inline';

/**
 * Loading placeholders that roughly match the final layout, so content does not jump when it arrives.
 * `<fs-skeleton preset="list" [rows]="4" />`
 */
@Component({
  selector: 'fs-skeleton',
  imports: [Skeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'fs-skeleton', 'aria-hidden': 'true', '[attr.data-preset]': 'preset()' },
  template: `
    @switch (preset()) {
      @case ('list') {
        <div class="sk-list">
          @for (row of rowItems(); track row) {
            <div class="sk-list__row">
              <p-skeleton shape="circle" size="2.75rem" />
              <div class="sk-list__text">
                <p-skeleton width="45%" height="0.95rem" />
                <p-skeleton width="75%" height="0.8rem" />
              </div>
            </div>
          }
        </div>
      }
      @case ('card') {
        <div class="sk-card">
          <p-skeleton width="40%" height="1.4rem" />
          @for (row of rowItems(); track row) {
            <p-skeleton [width]="row % 2 ? '85%' : '100%'" height="0.85rem" />
          }
          <div class="sk-card__actions">
            <p-skeleton width="7rem" height="2.5rem" borderRadius="0.75rem" />
          </div>
        </div>
      }
      @case ('table') {
        <div class="sk-table">
          <div class="sk-table__row sk-table__row--head">
            @for (col of columns; track col) {
              <p-skeleton height="0.75rem" [width]="col === 0 ? '60%' : '45%'" />
            }
          </div>
          @for (row of rowItems(); track row) {
            <div class="sk-table__row">
              @for (col of columns; track col) {
                <p-skeleton height="0.9rem" [width]="(row + col) % 3 === 0 ? '55%' : '80%'" />
              }
            </div>
          }
        </div>
      }
      @case ('form') {
        <div class="sk-form">
          @for (row of rowItems(); track row) {
            <div class="sk-form__field">
              <p-skeleton width="30%" height="0.8rem" />
              <p-skeleton height="2.75rem" borderRadius="0.75rem" />
            </div>
          }
          <p-skeleton width="8rem" height="2.75rem" borderRadius="0.75rem" />
        </div>
      }
      @case ('detail') {
        <div class="sk-detail">
          <div class="sk-detail__head">
            <p-skeleton shape="circle" size="4rem" />
            <div class="sk-list__text">
              <p-skeleton width="50%" height="1.5rem" />
              <p-skeleton width="30%" height="0.9rem" />
            </div>
          </div>
          @for (row of rowItems(); track row) {
            <p-skeleton [width]="row % 3 === 2 ? '60%' : '100%'" height="0.9rem" />
          }
        </div>
      }
      @default {
        <p-skeleton width="8rem" height="1rem" />
      }
    }
  `,
  styles: `
    :host {
      display: block;
    }
    .sk-list {
      display: grid;
      gap: 1.1rem;
    }
    .sk-list__row,
    .sk-detail__head {
      display: flex;
      align-items: center;
      gap: 0.9rem;
    }
    .sk-list__text {
      display: grid;
      flex: 1;
      gap: 0.5rem;
    }
    .sk-card,
    .sk-form,
    .sk-detail {
      display: grid;
      gap: 0.85rem;
    }
    .sk-card__actions {
      padding-top: 0.5rem;
    }
    .sk-form__field {
      display: grid;
      gap: 0.45rem;
    }
    .sk-detail__head {
      margin-bottom: 0.5rem;
    }
    .sk-table {
      display: grid;
      overflow: hidden;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-md);
    }
    .sk-table__row {
      display: grid;
      grid-template-columns: 2fr 1fr 1fr 1fr;
      gap: 1rem;
      padding: 0.95rem 1rem;
      border-top: 1px solid var(--fs-border);
    }
    .sk-table__row--head {
      border-top: 0;
      background: var(--fs-surface-muted);
    }
  `,
})
export class SkeletonPresetComponent {
  readonly preset = input<SkeletonPreset>('list');
  readonly rows = input<number | undefined>(undefined);
  protected readonly columns = [0, 1, 2, 3];
  protected readonly rowItems = computed(() => {
    const defaults: Record<SkeletonPreset, number> = {
      list: 3,
      card: 3,
      table: 5,
      form: 3,
      detail: 4,
      inline: 1,
    };
    return Array.from({ length: this.rows() ?? defaults[this.preset()] }, (_, index) => index);
  });
}
