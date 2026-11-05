import { WallMilestone } from '../../api';
import { milestoneText } from './stores.util';

describe('milestoneText (S11)', () => {
  const body = (milestone: WallMilestone, text?: string) => milestoneText({ milestone, text: text as string });

  it('translates cooperation and team status codes instead of showing them raw', () => {
    expect(body(WallMilestone.StatusChanged, 'IN_NEGOTIATION')).toEqual({
      key: 'shared.store.cooperation.IN_NEGOTIATION',
    });
    expect(body(WallMilestone.CooperationStarted, 'NO_CONTACT')).toEqual({ key: 'shared.store.cooperation.NO_CONTACT' });
    expect(body(WallMilestone.TeamStatusChanged, 'CLOSED')).toEqual({ key: 'shared.store.team.CLOSED' });
  });

  it('drops the redundant "cooperation established" body and keeps dates as dates', () => {
    expect(body(WallMilestone.CooperationStarted, 'COOPERATION_ESTABLISHED')).toBeNull();
    expect(body(WallMilestone.CooperationStarted, '2025-03-01')).toEqual({ date: '2025-03-01' });
  });

  it('keeps other texts and never translates plain posts', () => {
    expect(body(WallMilestone.StoreCreated, 'Coop Bahnhofbrücke')).toEqual({ text: 'Coop Bahnhofbrücke' });
    expect(body(WallMilestone.TeamStatusChanged, 'IN_NEGOTIATION')).toEqual({ text: 'IN_NEGOTIATION' });
    expect(body(WallMilestone.None, 'CLOSED')).toEqual({ text: 'CLOSED' });
    expect(body(WallMilestone.StatusChanged, '  ')).toBeNull();
    expect(body(WallMilestone.StatusChanged)).toBeNull();
  });
});
