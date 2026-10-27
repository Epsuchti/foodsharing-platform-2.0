import { safeReturnUrl } from './return-url';

describe('safeReturnUrl', () => {
  it('accepts in-app absolute paths', () => {
    expect(safeReturnUrl('/dashboard')).toBe('/dashboard');
    expect(safeReturnUrl('/account/sessions?tab=1#x')).toBe('/account/sessions?tab=1#x');
  });

  it('rejects everything that could leave the app or loop into login', () => {
    for (const value of [
      null,
      '',
      'dashboard',
      'https://evil.example',
      '//evil.example',
      '/\\evil.example',
      '/a//b',
      '/login',
      '/login/verify?token=1',
      `/${'a'.repeat(500)}`,
      '/x\u0000y',
    ]) {
      expect(safeReturnUrl(value)).toBeNull();
    }
  });

  it('accepts paths that only start like login', () => {
    expect(safeReturnUrl('/loginhelp')).toBe('/loginhelp');
  });
});
