import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { Subject } from 'rxjs';
import { filter } from 'rxjs/operators';

import {
  ChatMessage,
  ConversationDetail,
  ConversationKind,
  Language,
  RealtimeEvent,
  RealtimeEventType,
  Role,
  Theme,
} from '../../api';
import { SessionService } from '../../core/auth/session.service';
import { RealtimeService } from '../../core/realtime/realtime.service';
import { provideDataStateTesting } from '../../core/testing/data-state-testing';
import { ConversationViewComponent } from './components/conversation-view.component';
import { applyMessageUpdate } from './messaging.resource';

const ME = '00000001-0001-4000-8000-000000000006';
const NINA = '00000001-0001-4000-8000-000000000009';
const CONVERSATION = '00000041-0001-4000-8000-000000000004';

function message(seq: number, overrides: Partial<ChatMessage> = {}): ChatMessage {
  return {
    id: `m${seq}`,
    seq,
    senderId: ME,
    body: `Nachricht ${seq}`,
    sentAt: `2026-11-01T1${seq}:00:00Z`,
    mayEdit: false,
    mayDelete: false,
    ...overrides,
  };
}

function detail(messages: ChatMessage[]): ConversationDetail {
  return {
    id: CONVERSATION,
    kind: ConversationKind.StoreTeam,
    name: 'Team Coop',
    locked: true,
    members: [
      { id: ME, displayName: 'Sarah Müller', role: Role.Foodsaver, verified: true },
      { id: NINA, displayName: 'Nina Schneider', role: Role.StoreManager, verified: true },
    ],
    memberCount: 2,
    unreadCount: 0,
    lastReadSeq: messages.at(-1)?.seq ?? 0,
    lastSeq: messages.at(-1)?.seq ?? 0,
    messages,
  };
}

class FakeRealtime {
  readonly connectCount = signal(0);
  readonly events = new Subject<RealtimeEvent>();
  readonly reconnects: (() => void)[] = [];

  on(...types: string[]) {
    return this.events.pipe(filter((event) => types.includes(event.type)));
  }

  onReconnect(callback: () => void): () => void {
    this.reconnects.push(callback);
    return () => undefined;
  }
}

@Component({
  imports: [ConversationViewComponent],
  template: `<fs-conversation-view conversationId="${CONVERSATION}" />`,
})
class HostComponent {}

describe('ConversationViewComponent edit/delete (item 16)', () => {
  let http: HttpTestingController;
  let realtime: FakeRealtime;
  let fixture: ComponentFixture<HostComponent>;
  const q = (selector: string) => fixture.nativeElement.querySelector(selector) as HTMLElement | null;
  const doc = (selector: string) => document.querySelector(selector) as HTMLElement | null;
  const row = (seq: number) => q(`[data-testid="messaging.conversation.message"][data-seq="${seq}"]`)!;
  const settle = async () => {
    fixture.detectChanges();
    await fixture.whenStable();
  };
  const messagesRequest = (req: { url: string }) => req.url === `/api/conversations/${CONVERSATION}/messages`;

  async function open(messages: ChatMessage[]): Promise<void> {
    fixture = TestBed.createComponent(HostComponent);
    await settle();
    http.expectOne(`/api/conversations/${CONVERSATION}`).flush(detail(messages));
    await settle();
  }

  beforeEach(() => {
    realtime = new FakeRealtime();
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          translations: {
            'messaging.message.deleted': 'Nachricht gelöscht',
            'messaging.message.deletedByModeration': 'Von der Moderation gelöscht',
            'messaging.message.edited': 'bearbeitet',
          },
        }),
        provideOptimus(),
        { provide: RealtimeService, useValue: realtime },
      ],
    });
    TestBed.inject(SessionService).applySession({
      authenticated: true,
      permissions: [],
      user: {
        id: ME,
        email: 'foodsaver@local',
        displayName: 'Sarah',
        role: Role.Foodsaver,
        language: Language.De,
        theme: Theme.System,
        verified: true,
      },
    });
    http = TestBed.inject(HttpTestingController);
  });

  it('shows the message menu only from the server flags and renders edited and deleted states', async () => {
    await open([
      message(1, { mayEdit: true, mayDelete: true }),
      message(2, { senderId: NINA }),
      message(3, { senderId: NINA, mayDelete: true }),
      message(4, { body: '', deletedAt: '2026-11-01T15:00:00Z', deletedByModerator: true }),
      message(5, { senderId: NINA, editedAt: '2026-11-01T15:30:00Z' }),
    ]);

    expect(row(1).querySelector('[data-testid="messaging.conversation.messageMenu"]')).not.toBeNull();
    expect(row(2).querySelector('[data-testid="messaging.conversation.messageMenu"]')).toBeNull();
    expect(row(3).querySelector('[data-testid="messaging.conversation.messageMenu"]')).not.toBeNull();
    expect(row(4).querySelector('[data-testid="messaging.conversation.messageMenu"]')).toBeNull();
    expect(row(4).getAttribute('data-deleted')).toBe('true');
    expect(row(4).querySelector('[data-testid="messaging.conversation.deleted"]')?.textContent).toContain(
      'Von der Moderation gelöscht',
    );
    expect(row(5).getAttribute('data-edited')).toBe('true');
    expect(row(5).querySelector('[data-testid="messaging.conversation.edited"]')?.textContent).toContain('bearbeitet');

    // someone else's message for a moderator: only the moderation delete
    row(3).querySelector<HTMLElement>('[data-testid="messaging.conversation.messageMenu"]')!.click();
    await settle();
    expect(doc('[data-testid="messaging.conversation.edit"]')).toBeNull();
    expect(doc('[data-testid="messaging.conversation.delete"]')?.getAttribute('data-moderation')).toBe('true');
  });

  it('edits in the composer and saves with PATCH', async () => {
    await open([message(1, { mayEdit: true, mayDelete: true })]);
    row(1).querySelector<HTMLElement>('[data-testid="messaging.conversation.messageMenu"]')!.click();
    await settle();
    // own message within the window: edit and a plain delete
    expect(doc('[data-testid="messaging.conversation.delete"]')?.getAttribute('data-moderation')).toBe('false');
    doc('[data-testid="messaging.conversation.edit"]')!.click();
    await settle();
    expect(q('[data-testid="messaging.conversation.editBanner"]')).not.toBeNull();
    const composer = q('[data-testid="messaging.conversation.composer"]') as HTMLTextAreaElement;
    expect(composer.value).toBe('Nachricht 1');
    expect(row(1).querySelector('[data-testid="messaging.conversation.messageMenu"]')).toBeNull();

    composer.value = 'Nachricht 1, korrigiert';
    composer.dispatchEvent(new Event('input'));
    await settle();
    composer.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await settle();
    const patch = http.expectOne(
      (req) => req.method === 'PATCH' && req.url === `/api/conversations/${CONVERSATION}/messages/m1`,
    );
    expect(patch.request.body).toEqual({ body: 'Nachricht 1, korrigiert' });
    patch.flush(
      message(1, { body: 'Nachricht 1, korrigiert', editedAt: '2026-11-01T11:05:00Z', mayEdit: true, mayDelete: true }),
    );
    await settle();
    expect(q('[data-testid="messaging.conversation.editBanner"]')).toBeNull();
    expect(row(1).textContent).toContain('Nachricht 1, korrigiert');
    expect(row(1).getAttribute('data-edited')).toBe('true');
    expect((q('[data-testid="messaging.conversation.composer"]') as HTMLTextAreaElement).value).toBe('');
  });

  it('shows a picked mention as plain @Name and sends it as a token (decision 162)', async () => {
    await open([message(1)]);
    const composer = q('[data-testid="messaging.conversation.composer"]') as HTMLTextAreaElement;
    composer.value = 'Hallo @Ni';
    composer.dispatchEvent(new Event('input'));
    await settle();
    q('[data-testid="messaging.conversation.mentionPick"]')!.click();
    await settle();
    expect(composer.value).toBe('Hallo @Nina Schneider ');
    composer.value = 'Hallo @Nina Schneider, kommst du?';
    composer.dispatchEvent(new Event('input'));
    await settle();
    composer.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await settle();
    const post = http.expectOne((req) => req.method === 'POST' && messagesRequest(req));
    expect((post.request.body as { body: string }).body).toBe(`Hallo @[Nina Schneider](user:${NINA}), kommst du?`);
    expect(composer.value).toBe('');
  });

  it('edits a message with a mention as plain text and keeps the token on save', async () => {
    const body = `Danke @[Nina Schneider](user:${NINA})`;
    await open([message(1, { body, mayEdit: true, mayDelete: true })]);
    row(1).querySelector<HTMLElement>('[data-testid="messaging.conversation.messageMenu"]')!.click();
    await settle();
    doc('[data-testid="messaging.conversation.edit"]')!.click();
    await settle();
    const composer = q('[data-testid="messaging.conversation.composer"]') as HTMLTextAreaElement;
    expect(composer.value).toBe('Danke @Nina Schneider');
    // unchanged (compared encoded): Enter saves nothing
    composer.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await settle();
    http.expectNone((req) => req.method === 'PATCH');

    composer.value = 'Danke @Nina Schneider!';
    composer.dispatchEvent(new Event('input'));
    await settle();
    composer.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await settle();
    const patch = http.expectOne((req) => req.method === 'PATCH');
    expect(patch.request.body).toEqual({ body: `Danke @[Nina Schneider](user:${NINA})!` });
  });

  it('replaces a message on CONVERSATION_MESSAGE_UPDATED and drops the menu of a deleted one', async () => {
    await open([message(1, { mayEdit: true, mayDelete: true }), message(2, { senderId: NINA })]);
    realtime.events.next({
      type: RealtimeEventType.ConversationMessageUpdated,
      at: '2026-11-01T12:00:00Z',
      payload: { ...message(2, { senderId: NINA, body: 'Neu', editedAt: '2026-11-01T12:00:00Z' }), conversationId: CONVERSATION },
    });
    await settle();
    expect(row(2).textContent).toContain('Neu');
    expect(row(2).getAttribute('data-edited')).toBe('true');

    const { mayEdit: _edit, mayDelete: _delete, ...deleted } = message(1, {
      body: '',
      deletedAt: '2026-11-01T12:01:00Z',
      deletedByModerator: false,
    });
    realtime.events.next({
      type: RealtimeEventType.ConversationMessageUpdated,
      at: '2026-11-01T12:01:00Z',
      payload: { ...deleted, conversationId: CONVERSATION },
    });
    await settle();
    expect(row(1).querySelector('[data-testid="messaging.conversation.deleted"]')?.textContent).toContain(
      'Nachricht gelöscht',
    );
    expect(row(1).querySelector('[data-testid="messaging.conversation.messageMenu"]')).toBeNull();
  });

  it('reloads the loaded window on reconnect so offline edits show up', async () => {
    await open([message(1), message(2), message(3)]);
    realtime.reconnects.forEach((callback) => callback());
    await settle();
    const requests: TestRequest[] = http.match(messagesRequest);
    const reload = requests.find((req) => req.request.params.has('beforeSeq'))!;
    expect(reload.request.params.get('beforeSeq')).toBe('4');
    expect(reload.request.params.get('limit')).toBe('3');
    expect(requests.find((req) => req.request.params.get('afterSeq') === '3')).toBeDefined();
    reload.flush([message(1), message(2, { body: 'Offline geändert', editedAt: '2026-11-01T13:00:00Z' }), message(3)]);
    requests.filter((req) => req !== reload).forEach((req) => req.flush([]));
    await settle();
    await settle();
    expect(row(2).textContent).toContain('Offline geändert');
  });
});

describe('applyMessageUpdate', () => {
  it('keeps the viewer flags of a realtime update and clears them on delete', () => {
    const list = [message(1, { mayEdit: true, mayDelete: true }), message(2)];
    const edited = applyMessageUpdate(list, { ...message(1, { body: 'x' }), mayEdit: undefined, mayDelete: undefined });
    expect(edited[0]).toMatchObject({ body: 'x', mayEdit: true, mayDelete: true });
    expect(edited[1]).toBe(list[1]);
    const deleted = applyMessageUpdate(list, { ...message(1, { body: '', deletedAt: '2026-11-01T12:00:00Z' }), mayEdit: undefined, mayDelete: undefined });
    expect(deleted[0]).toMatchObject({ body: '', mayEdit: false, mayDelete: false });
  });
});
