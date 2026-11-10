import { MapViewParams, mapViewQuery, parseMapView } from './map-view-query';

/** Query params as Angular's ParamMap sees them: `get` returns the first of duplicated values. */
function params(query: string): MapViewParams {
  const search = new URLSearchParams(query);
  return { get: (name) => search.get(name) };
}

/** M1 deep link `?lat&lon&z` (decision 176, D12). */
describe('map view query', () => {
  it('round-trips a view at 4 decimals', () => {
    const query = mapViewQuery({ lat: 47.376912345, lon: 8.541694, zoom: 13 });
    expect(query).toEqual({ lat: '47.3769', lon: '8.5417', z: 13 });
    expect(parseMapView(params(`lat=${query.lat}&lon=${query.lon}&z=${query.z}`))).toEqual({
      lat: 47.3769,
      lon: 8.5417,
      zoom: 13,
    });
  });

  it('rounds and clamps the zoom to [3, 19]', () => {
    expect(parseMapView(params('lat=47&lon=8&z=0'))?.zoom).toBe(3);
    expect(parseMapView(params('lat=47&lon=8&z=25'))?.zoom).toBe(19);
    expect(parseMapView(params('lat=47&lon=8&z=7.6'))?.zoom).toBe(8);
    expect(mapViewQuery({ lat: 47, lon: 8, zoom: 0 }).z).toBe(3);
    expect(mapViewQuery({ lat: 47, lon: 8, zoom: 25 }).z).toBe(19);
  });

  it('uses zoom 12 when z is missing or not a number', () => {
    expect(parseMapView(params('lat=47.37&lon=8.54'))).toEqual({ lat: 47.37, lon: 8.54, zoom: 12 });
    expect(parseMapView(params('lat=47.37&lon=8.54&z=NaN'))?.zoom).toBe(12);
    expect(parseMapView(params('lat=47.37&lon=8.54&z=abc'))?.zoom).toBe(12);
    expect(mapViewQuery({ lat: 47, lon: 8 }).z).toBe(12);
  });

  it('ignores the whole link for an invalid or missing position', () => {
    for (const query of [
      'lat=91&lon=8',
      'lat=-91&lon=8',
      'lat=47&lon=-181',
      'lat=47&lon=181',
      'lat=abc&lon=8',
      'lat=47&lon=abc',
      'lat=&lon=',
      '',
      'lat=47',
      'lon=8',
      'lat=Infinity&lon=8',
      'lat=47&lon=-Infinity',
      'lat=1e400&lon=8',
      'lat=1e1&lon=8',
      'lat=0x10&lon=8',
    ]) {
      expect(parseMapView(params(query)), query).toBeNull();
    }
  });

  it('accepts the edges of the range', () => {
    expect(parseMapView(params('lat=-90&lon=180&z=3'))).toEqual({ lat: -90, lon: 180, zoom: 3 });
    expect(parseMapView(params('lat=90&lon=-180&z=19'))).toEqual({ lat: 90, lon: -180, zoom: 19 });
  });

  it('takes the first of duplicated params and ignores extra ones', () => {
    expect(parseMapView(params('lat=47.5&lat=10&lon=8.7&lon=1&z=11&z=5&layer=fsps'))).toEqual({
      lat: 47.5,
      lon: 8.7,
      zoom: 11,
    });
  });
});
