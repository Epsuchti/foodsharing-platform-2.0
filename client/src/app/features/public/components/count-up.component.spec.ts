import { TestBed } from '@angular/core/testing';

import { provideDataStateTesting } from '../../../core/testing/data-state-testing';
import { CountUpComponent } from './count-up.component';

/** The landing page's impact counters are formatted for Switzerland like every other number (they showed 1.481.927). */
describe('CountUpComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [...provideDataStateTesting()] });
    vi.spyOn(window, 'matchMedia').mockImplementation(
      (query: string) => ({ matches: query.includes('reduce'), media: query }) as MediaQueryList,
    );
  });
  afterEach(() => vi.restoreAllMocks());

  it('shows the final value in the Swiss format of the UI language', async () => {
    const fixture = TestBed.createComponent(CountUpComponent);
    fixture.componentRef.setInput('value', 1481927);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toBe(new Intl.NumberFormat('de-CH').format(1481927));
    expect(fixture.nativeElement.getAttribute('data-value')).toBe('1481927');
  });
});
