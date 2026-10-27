import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { HttpActivityService } from '../core/http/http-activity.service';
import { TPipe } from '../core/i18n/t.pipe';

/** Thin progress bar at the top while any request runs (fades in after 150 ms to avoid flicker). */
@Component({
  selector: 'fs-request-bar',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (activity.busy()) {
      <div
        class="bar"
        role="progressbar"
        [attr.aria-label]="'layout.requestBar.label' | t"
        data-testid="shell.requestBar"
      >
        <span class="bar__indicator"></span>
      </div>
    }
  `,
  styles: `
    :host {
      position: fixed;
      z-index: 1200;
      top: 0;
      right: 0;
      left: 0;
      height: 3px;
      pointer-events: none;
    }
    .bar {
      position: relative;
      height: 100%;
      overflow: hidden;
      background: color-mix(in srgb, var(--fs-primary) 18%, transparent);
      animation: fade-in 200ms ease 150ms both;
    }
    .bar__indicator {
      position: absolute;
      top: 0;
      bottom: 0;
      left: 0;
      width: 40%;
      border-radius: 3px;
      background: var(--fs-primary);
      animation: slide 1.1s ease-in-out infinite;
    }
    @keyframes slide {
      from {
        transform: translateX(-100%);
      }
      to {
        transform: translateX(260%);
      }
    }
    @keyframes fade-in {
      from {
        opacity: 0;
      }
      to {
        opacity: 1;
      }
    }
    @media (prefers-reduced-motion: reduce) {
      .bar__indicator {
        width: 100%;
        animation: none;
        opacity: 0.6;
      }
    }
  `,
})
export class RequestBarComponent {
  protected readonly activity = inject(HttpActivityService);
}
