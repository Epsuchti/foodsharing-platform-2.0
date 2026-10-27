import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup } from '@angular/forms';
import { MessageService } from '@openng/optimus-ui/api';
import { Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { ApiErrorCode } from '../../api';
import { apiHttpError, provideDataStateTesting } from '../testing/data-state-testing';
import { runMutation } from './mutation';

describe('runMutation', () => {
  let messages: MessageService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: provideDataStateTesting({
        translations: { 'errors.CONCURRENT_MODIFICATION': 'Inzwischen geändert' },
      }),
    });
    messages = TestBed.inject(MessageService);
  });

  const mutation = <T, A extends unknown[] = []>(...args: Parameters<typeof runMutation<T, A>>) =>
    TestBed.runInInjectionContext(() => runMutation<T, A>(...args));

  it('tracks busy, prevents double submit and calls onSuccess with a toast', async () => {
    const response = new Subject<string>();
    const call = vi.fn(() => response.asObservable());
    const onSuccess = vi.fn();
    const add = vi.spyOn(messages, 'add');
    const save = mutation(call, { onSuccess, successToastKey: 'x.saved' });

    const done = save.run();
    expect(save.busy()).toBe(true);
    void save.run();
    expect(call).toHaveBeenCalledTimes(1);

    response.next('ok');
    response.complete();
    expect(await done).toBe(true);
    expect(save.busy()).toBe(false);
    expect(onSuccess).toHaveBeenCalledWith('ok');
    expect(add).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'success', summary: 'x.saved' }),
    );
  });

  it('passes run() arguments to the call', async () => {
    const call = vi.fn((id: string) => of(id));
    const remove = mutation(call);
    await remove.run('42');
    expect(call).toHaveBeenCalledWith('42');
  });

  it('sets server field errors on the form and keeps its values', async () => {
    const form = new FormGroup({ email: new FormControl('bad@') });
    const add = vi.spyOn(messages, 'add');
    const save = mutation(
      () =>
        throwError(() =>
          apiHttpError(400, ApiErrorCode.ValidationFailed, {
            fieldErrors: [{ field: 'email', code: 'Email' }],
          }),
        ),
      { form },
    );
    expect(await save.run()).toBe(false);
    expect(form.get('email')?.errors).toEqual(expect.objectContaining({ server: 'Email' }));
    expect(form.get('email')?.value).toBe('bad@');
    expect(save.error()?.code).toBe(ApiErrorCode.ValidationFailed);
    expect(add).not.toHaveBeenCalled();
  });

  it('shows an error toast and rolls back optimistic updates', async () => {
    const add = vi.spyOn(messages, 'add');
    const optimistic = { apply: vi.fn(), rollback: vi.fn() };
    const save = mutation(
      () => throwError(() => apiHttpError(409, ApiErrorCode.ConcurrentModification)),
      { optimistic },
    );
    await save.run();
    expect(optimistic.apply).toHaveBeenCalled();
    expect(optimistic.rollback).toHaveBeenCalled();
    expect(add).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'error', detail: 'Inzwischen geändert' }),
    );
  });

  it('leaves 401 to the session-expired dialog', async () => {
    const add = vi.spyOn(messages, 'add');
    await mutation(() =>
      throwError(() => apiHttpError(401, ApiErrorCode.AuthenticationRequired)),
    ).run();
    expect(add).not.toHaveBeenCalled();
  });
});
