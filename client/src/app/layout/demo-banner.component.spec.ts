import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AppInfo } from '../api';
import { AppInfoService } from '../core/app-info.service';
import { provideDataStateTesting } from '../core/testing/data-state-testing';
import { DemoBannerComponent } from './demo-banner.component';

const APP_INFO: AppInfo = {
  version: '1.2.3',
  devMode: false,
  personaPanel: false,
  demoBanner: false,
  languages: [],
  loginMethods: { google: true, magicLink: true, passkeys: true },
  serverTime: '2026-11-02T09:00:00Z',
};

describe('DemoBannerComponent', () => {
  let fixture: ComponentFixture<DemoBannerComponent>;
  let host: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: provideDataStateTesting({
        translations: { 'layout.demoBanner.text': 'Testumgebung: keine echten Daten' },
      }),
    });
  });

  async function create(info: AppInfo | null): Promise<void> {
    TestBed.inject(AppInfoService).info.set(info);
    fixture = TestBed.createComponent(DemoBannerComponent);
    host = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  }

  const banner = () => host.querySelector('[data-testid="shell.demoBanner"]');

  it('shows the red banner on the test environment', async () => {
    await create({ ...APP_INFO, demoBanner: true });
    expect(banner()?.textContent).toContain('Testumgebung: keine echten Daten');
    expect(banner()?.getAttribute('role')).toBe('note');
  });

  it('shows nothing in production or before the app info is loaded', async () => {
    await create(APP_INFO);
    expect(banner()).toBeNull();
    await create(null);
    expect(banner()).toBeNull();
  });
});
