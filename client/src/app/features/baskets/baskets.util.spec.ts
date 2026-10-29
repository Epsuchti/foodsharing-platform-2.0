import { BasketKind, BasketLocationType, BasketRequestStatus } from '../../api';
import {
  BasketFormValue,
  answerActions,
  basketCreateBody,
  basketFormProblems,
  basketMarkers,
  basketUpdateBody,
  clampDistance,
  formatDistance,
  isOpenRequest,
  photoVariant,
  profilePosition,
} from './baskets.util';

function value(overrides: Partial<BasketFormValue> = {}): BasketFormValue {
  return {
    description: '  Zwei Brote  ',
    weightKg: null,
    kinds: [BasketKind.BakedGoods],
    groceryIds: [],
    locationType: BasketLocationType.Home,
    position: null,
    contactMessage: true,
    contactPhone: false,
    contactMobile: false,
    phone: '+41441234506',
    mobile: null,
    lifetimeDays: 7,
    photoUploadId: null,
    ...overrides,
  };
}

describe('baskets.util', () => {
  it('copies the server rules: location and contact required', () => {
    expect(basketFormProblems(value(), true)).toEqual([]);
    expect(basketFormProblems(value(), false)).toEqual(['BASKET_LOCATION_REQUIRED']);
    expect(basketFormProblems(value({ locationType: BasketLocationType.Custom }), true)).toEqual(['BASKET_LOCATION_REQUIRED']);
    expect(basketFormProblems(value({ locationType: BasketLocationType.Custom, position: [47.38, 8.52] }), false)).toEqual([]);
    expect(basketFormProblems(value({ contactMessage: false }), true)).toEqual(['BASKET_CONTACT_REQUIRED']);
    expect(basketFormProblems(value({ contactMessage: false, contactMobile: true }), true)).toEqual([]);
  });

  it('builds the create body: trimmed text, grams, coordinates only for CUSTOM, numbers only when flagged', () => {
    expect(basketCreateBody(value({ weightKg: 1.25, lifetimeDays: 40 }))).toEqual({
      description: 'Zwei Brote',
      weightGrams: 1250,
      kinds: [BasketKind.BakedGoods],
      groceryIds: [],
      locationType: BasketLocationType.Home,
      lat: undefined,
      lon: undefined,
      contactMessage: true,
      contactPhone: false,
      contactMobile: false,
      phone: undefined,
      mobile: undefined,
      photoUploadId: undefined,
      lifetimeDays: 21,
    });
    const custom = basketCreateBody(
      value({ locationType: BasketLocationType.Custom, position: [47.4, 8.6], contactPhone: true, photoUploadId: 'u1', lifetimeDays: 0 }),
    );
    expect(custom).toMatchObject({ lat: 47.4, lon: 8.6, phone: '+41441234506', photoUploadId: 'u1', lifetimeDays: 1 });
  });

  it('builds the update body without a lifetime', () => {
    const body = basketUpdateBody(value({ contactMobile: true, mobile: '  ' }));
    expect(body).not.toHaveProperty('lifetimeDays');
    expect(body.mobile).toBeUndefined();
  });

  it('offers the answers the owner can still give', () => {
    expect(answerActions(BasketRequestStatus.Requested)).toEqual(['PICKED_UP', 'DENIED', 'NOT_PICKED_UP']);
    expect(answerActions(BasketRequestStatus.PickedUp)).toEqual(['DENIED', 'NOT_PICKED_UP']);
    expect(answerActions(BasketRequestStatus.Withdrawn)).toEqual([]);
    expect(isOpenRequest(BasketRequestStatus.Read)).toBe(true);
    expect(isOpenRequest(BasketRequestStatus.Denied)).toBe(false);
  });

  it('clamps the radius, reads profile coordinates and maps markers', () => {
    expect(clampDistance(0)).toBe(1);
    expect(clampDistance(70)).toBe(50);
    expect(clampDistance(12.4)).toBe(12);
    expect(clampDistance(Number.NaN)).toBe(10);
    expect(profilePosition({ latitude: 47.389, longitude: 8.548 })).toEqual([47.389, 8.548]);
    expect(profilePosition({ latitude: 0, longitude: 0 })).toBeNull();
    expect(profilePosition({})).toBeNull();
    expect(basketMarkers([{ id: 'b1', lat: 1, lon: 2, description: 'Brot' }])).toEqual([
      { id: 'b1', kind: 'basket', lat: 1, lon: 2, label: 'Brot' },
    ]);
  });

  it('switches photo variants and formats distances', () => {
    expect(photoVariant('/api/uploads/u1?size=md', 'lg')).toBe('/api/uploads/u1?size=lg');
    expect(photoVariant('/api/uploads/u1', 'sm')).toBe('/api/uploads/u1?size=sm');
    expect(photoVariant(undefined, 'sm')).toBeUndefined();
    expect(photoVariant('data:image/png;base64,AAAA', 'sm')).toBe('data:image/png;base64,AAAA');
    expect(formatDistance(1.234, 'en-CH')).toBe('1.2');
    expect(formatDistance(12.6, 'en-CH')).toBe('13');
    expect(formatDistance(undefined, 'en-CH')).toBeUndefined();
  });
});
