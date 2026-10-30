import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';

import { UploadAdminSummary } from '../../../api';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { formatBytes } from '../admin-settings.util';

/** Storage usage of the uploads (C10): totals, unused files and one chip per module. */
@Component({
  selector: 'fs-upload-summary',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let sum = summary();
    <section class="fs-card stats" data-testid="adminSettings.uploads.summary">
      <div class="stat">
        <span class="stat__value" data-testid="adminSettings.uploads.count">{{ sum.count }}</span>
        <span class="muted">{{ 'adminSettings.uploads.files' | t }}</span>
      </div>
      <div class="stat">
        <span class="stat__value" data-testid="adminSettings.uploads.bytes">{{ bytes(sum.bytes) }}</span>
        <span class="muted">{{ 'adminSettings.uploads.storage' | t }}</span>
      </div>
      <div class="stat">
        <span class="stat__value" data-testid="adminSettings.uploads.unclaimedCount">{{ sum.unclaimedCount }}</span>
        <span class="muted">{{ 'adminSettings.uploads.unclaimed' | t }}</span>
      </div>
      <ul class="modules">
        @for (module of sum.byModule; track module.module) {
          <li class="chip" data-testid="adminSettings.uploads.module" [attr.data-module]="module.module">
            {{ moduleLabel(module.module) }}: {{ module.count }} · {{ bytes(module.bytes) }}
          </li>
        }
      </ul>
    </section>
  `,
  styles: `
    .stats {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 1rem 2rem;
      padding: 1rem;
    }
    .stat {
      display: grid;
    }
    .stat__value {
      font-size: 1.5rem;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
    }
    .muted {
      color: var(--fs-text-muted);
      font-size: 0.8125rem;
    }
    .modules {
      display: flex;
      flex-wrap: wrap;
      gap: 0.4rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .chip {
      padding: 0.1rem 0.5rem;
      border-radius: 999px;
      background: var(--fs-surface-muted);
      font-size: 0.75rem;
      font-weight: 650;
    }
  `,
})
export class UploadSummaryComponent {
  private readonly i18n = inject(I18nService);
  readonly summary = input.required<UploadAdminSummary>();

  protected bytes(value: number): string {
    return formatBytes(value);
  }

  protected moduleLabel(module: string): string {
    return uploadModuleLabel(this.i18n, module);
  }
}

/** Known modules have a label; others (new modules) show their code. */
export function uploadModuleLabel(i18n: I18nService, module: string): string {
  const key = `adminSettings.uploads.moduleName.${module}`;
  return i18n.has(key) ? i18n.t(key) : module;
}
