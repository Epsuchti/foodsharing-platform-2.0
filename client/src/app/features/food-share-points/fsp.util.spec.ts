import { FspStatus, FspSummary } from '../../api';
import {
  MANAGERS_MAX,
  addManager,
  fspCreateBody,
  fspMapLinks,
  fspMarkers,
  fspUpdateBody,
  photoVariant,
  removeManager,
} from './fsp.util';

const ADDRESS = { street: 'Schlossgasse 10', postcode: '8003', city: 'Zürich' };

function summary(id: string, name: string): FspSummary {
  return { id, name, city: 'Zürich', status: FspStatus.Active, followerCount: 0, following: false };
}

describe('fsp.util', () => {
  describe('fspMapLinks', () => {
    it('uses the coordinates when known', () => {
      const links = fspMapLinks({ name: 'Fairteiler Wiedikon', lat: 47.3721, lon: 8.5172, address: ADDRESS });
      expect(links?.google).toBe('https://www.google.com/maps/search/?api=1&query=47.3721,8.5172');
      expect(links?.apple).toBe('https://maps.apple.com/?ll=47.3721,8.5172&q=Fairteiler%20Wiedikon');
    });

    it('falls back to the address, and to nothing without one', () => {
      const links = fspMapLinks({ name: 'X', address: ADDRESS });
      expect(links?.google).toContain(encodeURIComponent('Schlossgasse 10 8003 Zürich'));
      expect(fspMapLinks({ name: 'X', address: { street: '', postcode: '', city: '' } })).toBeNull();
    });
  });

  it('photoVariant swaps the size of upload URLs only', () => {
    expect(photoVariant('/api/uploads/u1?size=md', 'lg')).toBe('/api/uploads/u1?size=lg');
    expect(photoVariant('/api/uploads/u1', 'sm')).toBe('/api/uploads/u1?size=sm');
    expect(photoVariant('data:image/png;base64,AA', 'sm')).toBe('data:image/png;base64,AA');
    expect(photoVariant(undefined, 'sm')).toBeUndefined();
  });

  it('fspMarkers pins every FSP of the list with a position (inactive ones too), labelled with its name', () => {
    const markers = fspMarkers([
      { ...summary('a', 'Wiedikon'), lat: 47.37, lon: 8.51 },
      { ...summary('b', 'Altstetten'), status: FspStatus.Inactive, lat: 47.38, lon: 0 },
      summary('c', 'Ohne Ort'),
    ]);
    expect(markers).toEqual([
      { id: 'a', kind: 'fsp', lat: 47.37, lon: 8.51, label: 'Wiedikon' },
      { id: 'b', kind: 'fsp', lat: 47.38, lon: 0, label: 'Altstetten' },
    ]);
  });

  describe('bodies', () => {
    const value = {
      name: '  Fairteiler Altstetten ',
      description: '  ',
      street: ' Badenerstrasse 600 ',
      postcode: '8048',
      city: 'Zürich ',
      position: null as [number, number] | null,
      photoUploadId: null as string | null,
    };

    it('create trims the texts, drops an empty description and lets the server geocode without a pin', () => {
      expect(fspCreateBody(value)).toEqual({
        name: 'Fairteiler Altstetten',
        street: 'Badenerstrasse 600',
        postcode: '8048',
        city: 'Zürich',
        description: undefined,
        lat: undefined,
        lon: undefined,
        photoUploadId: undefined,
      });
    });

    it('update sends the pin and the new photo; an empty description clears it', () => {
      const body = fspUpdateBody({ ...value, position: [47.388, 8.488], photoUploadId: 'up1' });
      expect(body).toMatchObject({ lat: 47.388, lon: 8.488, photoUploadId: 'up1', description: '' });
    });
  });

  describe('managers', () => {
    it('adds once and at most MANAGERS_MAX', () => {
      expect(addManager(['a'], 'b')).toEqual(['a', 'b']);
      expect(addManager(['a', 'b'], 'b')).toEqual(['a', 'b']);
      const full = Array.from({ length: MANAGERS_MAX }, (_, i) => `u${i}`);
      expect(addManager(full, 'new')).toEqual(full);
    });

    it('never removes the last manager', () => {
      expect(removeManager(['a', 'b'], 'a')).toEqual(['b']);
      expect(removeManager(['a'], 'a')).toEqual(['a']);
    });
  });
});
