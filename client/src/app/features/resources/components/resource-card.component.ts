import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { Button } from '@openng/optimus-ui/button';

import { Resource } from '../../../api';
import { TPipe } from '../../../core/i18n/t.pipe';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { isCommons } from '../resources.util';

/** One resource (C6): name, favourite star, commons/private/openness/category tags, owner, description, images. */
@Component({
  selector: 'fs-resource-card',
  imports: [Button, TPipe, UserChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'fs-resource-card',
    'data-testid': 'resources.card',
    '[attr.data-resource-id]': 'resource().id',
    '[attr.data-commons]': 'commons()',
    '[attr.data-favourite]': 'resource().favourite',
  },
  template: `
    @let item = resource();
    <article class="card">
      <header class="card__head">
        <h3 class="card__name" data-testid="resources.card.name">{{ item.name }}</h3>
        <p-button
          [icon]="item.favourite ? 'pi pi-star-fill' : 'pi pi-star'"
          [text]="true"
          [rounded]="true"
          [severity]="item.favourite ? 'warn' : 'secondary'"
          [ariaLabel]="(item.favourite ? 'resources.card.unfavourite' : 'resources.card.favourite') | t: { name: item.name }"
          [attr.aria-pressed]="item.favourite"
          [loading]="busy()"
          (onClick)="favourite.emit()"
          data-testid="resources.card.favourite"
        />
      </header>
      <ul class="tags">
        @if (commons()) {
          <li class="tag tag--commons" data-testid="resources.card.commons"><i class="pi pi-building" aria-hidden="true"></i> {{ 'resources.card.commonsTag' | t }}</li>
        }
        @if (item.private) {
          <li class="tag tag--private" data-testid="resources.card.private"><i class="pi pi-lock" aria-hidden="true"></i> {{ 'resources.card.privateTag' | t }}</li>
        }
        <li class="tag tag--openness" [attr.data-openness]="item.openness">{{ 'resources.openness.' + item.openness | t }}</li>
        @for (category of item.categories; track category.id) {
          <li class="tag" [attr.data-category-id]="category.id" data-testid="resources.card.category">{{ category.name }}</li>
        }
      </ul>
      @if (item.owner; as owner) {
        <fs-user-chip
          [name]="owner.displayName"
          [userId]="owner.id"
          [avatarUrl]="owner.avatarUrl"
          [link]="['/profile', owner.id]"
          [sleepMode]="owner.sleepMode"
          [sleepMessage]="owner.sleepMessage"
        />
      }
      @if (item.description) {
        <p class="card__text">{{ item.description }}</p>
      }
      @if (item.images.length) {
        <ul class="images" data-testid="resources.card.images">
          @for (image of item.images; track image.uploadId; let i = $index) {
            <li>
              <a [href]="image.url + '?size=lg'" target="_blank" rel="noopener">
                <img [src]="image.url + '?size=sm'" [alt]="'resources.card.image' | t: { number: i + 1, name: item.name }" loading="lazy" />
              </a>
            </li>
          }
        </ul>
      }
      @if (item.mayEdit || item.mayDelete) {
        <footer class="card__actions">
          @if (item.mayEdit) {
            <p-button [label]="'resources.card.edit' | t" icon="pi pi-pencil" size="small" severity="secondary" [outlined]="true" (onClick)="edit.emit()" data-testid="resources.card.edit" />
          }
          @if (item.mayDelete) {
            <p-button [label]="'resources.card.delete' | t" icon="pi pi-trash" size="small" severity="danger" [text]="true" (onClick)="remove.emit()" data-testid="resources.card.delete" />
          }
        </footer>
      }
    </article>
  `,
  styles: `
    :host { display: block; min-width: 0; }
    .card {
      display: grid; gap: 0.6rem; height: 100%; padding: 0.9rem 1rem; border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-lg); background: var(--fs-surface); box-shadow: var(--fs-shadow-sm);
      align-content: start;
    }
    .card__head { display: flex; align-items: center; justify-content: space-between; gap: 0.5rem; }
    .card__name { margin: 0; font-size: 1.02rem; overflow-wrap: anywhere; }
    .tags { display: flex; flex-wrap: wrap; gap: 0.3rem; margin: 0; padding: 0; list-style: none; }
    .tag {
      display: inline-flex; align-items: center; gap: 0.3rem; padding: 0.1rem 0.55rem; border-radius: 999px;
      background: var(--fs-surface-muted); color: var(--fs-text-muted); font-size: 0.78rem; font-weight: 600;
    }
    .tag--commons { background: var(--fs-primary-soft); color: var(--fs-primary-strong); }
    .tag--private { background: var(--fs-warning-soft); color: var(--fs-warning-text); }
    .card__text { margin: 0; color: var(--fs-text); font-size: 0.9rem; white-space: pre-line; overflow-wrap: anywhere; }
    .images { display: flex; flex-wrap: wrap; gap: 0.4rem; margin: 0; padding: 0; list-style: none; }
    .images a { display: block; border-radius: var(--fs-radius-sm); }
    .images img { display: block; width: 4rem; height: 4rem; border-radius: var(--fs-radius-sm); object-fit: cover; }
    .card__actions { display: flex; flex-wrap: wrap; gap: 0.4rem; }
  `,
})
export class ResourceCardComponent {
  readonly resource = input.required<Resource>();
  readonly busy = input(false);
  readonly favourite = output<void>();
  readonly edit = output<void>();
  readonly remove = output<void>();

  protected readonly commons = computed(() => isCommons(this.resource()));
}
