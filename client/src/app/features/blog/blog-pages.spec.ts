import { HttpTestingController } from '@angular/common/http/testing';
import { Type } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach, vi } from 'vitest';

import {
  ApiErrorCode,
  BlogPost,
  BlogPostPage,
  Role,
  SessionInfo,
  UnitType,
  WorkingGroupFunction,
} from '../../api';
import { SessionService } from '../../core/auth/session.service';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { sessionInfo } from '../auth/testing';
import { BlogEditorPageComponent } from './pages/blog-editor-page.component';
import { BlogListPageComponent } from './pages/blog-list-page.component';
import { BlogManagePageComponent } from './pages/blog-manage-page.component';
import { BlogPostPageComponent } from './pages/blog-post-page.component';

const ZURICH = { id: 'r-zh', slug: 'zuerich-stadt', name: 'Zürich Stadt', type: UnitType.City };
const WELCOME_GROUP = 'g-welcome';

const page = (items: BlogPostPage['items'], totalItems = items.length): BlogPostPage => ({
  items,
  page: { page: 0, size: 12, totalItems, totalPages: Math.ceil(totalItems / 12) },
});

const POST: BlogPost = {
  id: 'p1',
  title: 'Neuer Fairteiler',
  teaser: 'Am Stauffacher.',
  body: 'Ein **neuer** Kühlschrank.',
  published: false,
  awaitingCheck: false,
  mayEdit: true,
  mayPublish: false,
  region: ZURICH,
};

function ambassador(): SessionInfo {
  return {
    ...sessionInfo({ role: Role.Foodsaver }),
    units: [
      {
        id: WELCOME_GROUP,
        name: 'Begrüssungsteam Zürich',
        slug: 'begruessung',
        type: UnitType.WorkingGroup,
        member: true,
        admin: true,
        homeRegion: false,
        function: WorkingGroupFunction.Welcome,
      },
    ],
  };
}

function prAdmin(): SessionInfo {
  return {
    ...sessionInfo({ role: Role.Foodsaver }),
    units: [
      {
        id: 'g-pr',
        name: 'Öffentlichkeitsarbeit',
        slug: 'pr',
        type: UnitType.WorkingGroup,
        member: true,
        admin: true,
        homeRegion: false,
        function: WorkingGroupFunction.Pr,
      },
    ],
  };
}

describe('blog pages (Q11, C2)', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<unknown>;
  const q = (testId: string) =>
    document.querySelector(`[data-testid="${testId}"]`) as HTMLElement | null;
  const all = (testId: string) => document.querySelectorAll(`[data-testid="${testId}"]`);
  const state = (testId: string) => q(testId)?.getAttribute('data-state');
  const button = (testId: string) => q(testId)!.querySelector('button') as HTMLButtonElement;
  const settle = async () => {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
  };
  const create = async <T>(component: Type<T>, inputs: Record<string, unknown> = {}) => {
    fixture = TestBed.createComponent(component) as ComponentFixture<unknown>;
    for (const [name, value] of Object.entries(inputs)) fixture.componentRef.setInput(name, value);
    document.body.appendChild(fixture.nativeElement as HTMLElement);
    await settle();
  };
  const type = (testId: string, value: string) => {
    const input = q(testId) as HTMLInputElement | HTMLTextAreaElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({ translations: { 'blog.list.filtered': 'Beiträge aus {region}' } }),
        provideOptimus(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify({ ignoreCancelled: true });
    (fixture?.nativeElement as HTMLElement | undefined)?.remove();
  });

  describe('list', () => {
    it('shows the skeleton, then the cards with region links; no manage link for visitors', async () => {
      await create(BlogListPageComponent);
      expect(state('blog.list.state')).toBe('loading');
      const request = http.expectOne((req) => req.url === '/api/public/blog');
      expect(request.request.params.get('page')).toBe('0');
      expect(request.request.params.get('size')).toBe('12');
      request.flush(
        page([
          { ...POST, published: true, pictureUrl: '/api/uploads/u1?size=lg' },
          { id: 'p2', title: 'Global', teaser: '', published: true },
        ]),
      );
      await settle();
      expect(state('blog.list.state')).toBe('success');
      expect(all('blog.list.post')).toHaveLength(2);
      expect(q('blog.list.post')!.querySelector('img')!.getAttribute('src')).toBe('/api/uploads/u1?size=md');
      expect(all('blog.list.region')).toHaveLength(1);
      expect(q('blog.list.manage')).toBeNull();
    });

    it('filters by region from the query parameter; empty and error states', async () => {
      await create(BlogListPageComponent, { region: ZURICH.id });
      const request = http.expectOne((req) => req.url === '/api/public/blog');
      expect(request.request.params.get('regionId')).toBe(ZURICH.id);
      request.flush(page([{ ...POST, published: true }]));
      await settle();
      expect(q('blog.list.filter')?.textContent).toContain('Zürich Stadt');
      fixture.destroy();

      await create(BlogListPageComponent);
      http.expectOne((req) => req.url === '/api/public/blog').flush(page([]));
      await settle();
      expect(state('blog.list.state')).toBe('empty');
      fixture.destroy();

      await create(BlogListPageComponent);
      http
        .expectOne((req) => req.url === '/api/public/blog')
        .flush(null, { status: 500, statusText: 'Server Error' });
      await settle();
      expect(state('blog.list.state')).toBe('error');
    });

    it('shows the manage link to ambassadors', async () => {
      TestBed.inject(SessionService).applySession(ambassador());
      await create(BlogListPageComponent);
      http.expectOne((req) => req.url === '/api/public/blog').flush(page([]));
      await settle();
      expect(q('blog.list.manage')).not.toBeNull();
    });
  });

  it('post: renders the Markdown body; unknown posts are not found', async () => {
    await create(BlogPostPageComponent, { id: 'p1' });
    http.expectOne('/api/public/blog/p1').flush({ ...POST, published: true, mayEdit: false });
    await settle();
    expect(q('blog.post.body')?.innerHTML).toContain('<strong>neuer</strong>');
    expect(q('blog.post.edit')).toBeNull();
    fixture.destroy();

    await create(BlogPostPageComponent, { id: 'nope' });
    const missing = apiHttpError(404, ApiErrorCode.BlogPostNotFound);
    http.expectOne('/api/public/blog/nope').flush(missing.error, { status: 404, statusText: 'Not Found' });
    await settle();
    expect(state('blog.post.state')).toBe('notFound');
  });

  it('manage: status tags per post; forbidden for users without blog rights', async () => {
    TestBed.inject(SessionService).applySession(ambassador());
    await create(BlogManagePageComponent);
    http
      .expectOne((req) => req.url === '/api/blog')
      .flush(page([{ ...POST, awaitingCheck: true }, { ...POST, id: 'p2', published: true }]));
    await settle();
    const tags = Array.from(all('blog.manage.status')).map((tag) => tag.getAttribute('data-status'));
    expect(tags).toEqual(['awaitingCheck', 'published']);
    fixture.destroy();

    await create(BlogManagePageComponent);
    const denied = apiHttpError(403, ApiErrorCode.AccessDenied);
    http.expectOne((req) => req.url === '/api/blog').flush(denied.error, { status: 403, statusText: 'Forbidden' });
    // the error interceptor refreshes the session after a 403 (the role may have changed)
    http.expectOne('/api/auth/session').flush(ambassador());
    await settle();
    expect(state('blog.manage.state')).toBe('forbidden');
  });

  describe('editor', () => {
    it('ambassador: the only administrated unit is preselected; the teaser comes from the body', async () => {
      TestBed.inject(SessionService).applySession(ambassador());
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      await create(BlogEditorPageComponent, { id: 'new' });
      expect(q('blog.editor.publish')).toBeNull();
      type('blog.editor.postTitle', 'Neuer Fairteiler');
      type('blog.editor.body', '# Hallo\n\nEin **neuer** Kühlschrank.');
      await settle();
      expect(button('blog.editor.save').disabled).toBe(false);
      button('blog.editor.save').click();
      const request = http.expectOne('/api/blog');
      expect(request.request.body).toEqual({
        title: 'Neuer Fairteiler',
        teaser: 'Hallo Ein neuer Kühlschrank.',
        body: '# Hallo\n\nEin **neuer** Kühlschrank.',
        regionId: WELCOME_GROUP,
      });
      request.flush({ ...POST, id: 'p9' });
      await settle();
      expect(navigate).toHaveBeenCalledWith(['/blog', 'p9', 'edit']);
    });

    it('ambassador: requests a check instead of publishing; the tag follows', async () => {
      TestBed.inject(SessionService).applySession(ambassador());
      await create(BlogEditorPageComponent, { id: 'p1' });
      http.expectOne('/api/blog/p1').flush(POST);
      await settle();
      expect(q('blog.editor.publish')).toBeNull();
      expect(q('blog.editor.status')?.getAttribute('data-status')).toBe('draft');
      button('blog.editor.requestCheck').click();
      const request = http.expectOne('/api/blog/p1/published');
      expect(request.request.body).toEqual({ published: true });
      request.flush({ ...POST, awaitingCheck: true });
      await settle();
      expect(q('blog.editor.status')?.getAttribute('data-status')).toBe('awaitingCheck');
      expect(q('blog.editor.withdraw')).not.toBeNull();
    });

    it('PR admin: publishes directly; a global post sends no region', async () => {
      TestBed.inject(SessionService).applySession(prAdmin());
      await create(BlogEditorPageComponent, { id: 'p1' });
      http.expectOne('/api/blog/p1').flush({ ...POST, region: undefined, awaitingCheck: true, mayPublish: true });
      await settle();
      expect(q('blog.editor.requestCheck')).toBeNull();
      type('blog.editor.postTitle', 'Geändert');
      await settle();
      // unsaved changes are saved first, then published
      button('blog.editor.publish').click();
      const save = http.expectOne((req) => req.url === '/api/blog/p1' && req.method === 'PATCH');
      expect(save.request.body.regionId).toBeUndefined();
      expect(save.request.body.title).toBe('Geändert');
      save.flush({ ...POST, title: 'Geändert', region: undefined, awaitingCheck: true });
      await settle();
      http.expectOne('/api/blog/p1/published').flush({ ...POST, published: true });
      await settle();
      expect(q('blog.editor.status')?.getAttribute('data-status')).toBe('published');
      expect(q('blog.editor.unpublish')).not.toBeNull();
    });
  });
});
