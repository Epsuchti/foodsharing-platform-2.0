import { clickFocusTarget, installClickFocus } from './click-focus';

/** WebKit focuses no button on a click; the app catches up so Escape and toggles behave as in Chromium/Firefox. */
describe('click focus', () => {
  let host: HTMLElement;
  let uninstall: () => void;

  beforeEach(() => {
    host = document.createElement('main');
    host.tabIndex = -1;
    host.innerHTML = `
      <button id="plain"><span id="label">Navigate</span></button>
      <button id="off" disabled>Off</button>
      <a id="link" href="#x">Link</a>
      <div id="tab" role="tab" tabindex="0">Tab</div>
      <p id="text">Text</p>`;
    document.body.append(host);
    uninstall = installClickFocus(document);
  });

  afterEach(() => {
    uninstall();
    host.remove();
  });

  const el = (id: string) => document.getElementById(id)!;
  const click = (target: HTMLElement, { detail = 1, preventMousedown = false } = {}) => {
    const down = new MouseEvent('mousedown', { bubbles: true, cancelable: true, detail });
    if (preventMousedown) target.addEventListener('mousedown', (e) => e.preventDefault(), { once: true });
    target.dispatchEvent(down);
    target.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, detail }));
  };

  it('focuses the clicked button, link or tab, also when the click lands on its label', () => {
    host.focus();
    click(el('label'));
    expect(document.activeElement).toBe(el('plain'));
    click(el('link'));
    expect(document.activeElement).toBe(el('link'));
    click(el('tab'));
    expect(document.activeElement).toBe(el('tab'));
  });

  it('leaves the focus alone for plain text, disabled controls, keyboard clicks and a prevented mousedown', () => {
    host.focus();
    click(el('text'));
    expect(document.activeElement).toBe(host);
    expect(clickFocusTarget(el('off'), host, false)).toBeNull();
    click(el('plain'), { detail: 0 });
    expect(document.activeElement).toBe(host);
    click(el('plain'), { preventMousedown: true });
    expect(document.activeElement).toBe(host);
    // the next ordinary click is not affected by the earlier prevented one
    click(el('plain'));
    expect(document.activeElement).toBe(el('plain'));
  });

  it('does nothing when the browser already focused the control or something inside it', () => {
    expect(clickFocusTarget(el('label'), el('plain'), false)).toBeNull();
    expect(clickFocusTarget(el('plain'), el('label'), false)).toBeNull();
  });
});
