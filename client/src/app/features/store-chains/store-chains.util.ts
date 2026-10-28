import { SessionInfo, StoreChain, StoreChainStatus, WorkingGroupFunction } from '../../api';
import { isOrga } from '../../core/auth/permission.util';

export const CHAIN_STATUSES: readonly StoreChainStatus[] = [
  StoreChainStatus.Cooperating,
  StoreChainStatus.Waiting,
  StoreChainStatus.NotCooperating,
  StoreChainStatus.InsufficientBranches,
  StoreChainStatus.Closed,
];

/**
 * Chain admins (decision 55): ORGA or admins of a working group with the function STORE_CHAINS. The server decides;
 * the client only uses this to offer "create" and the KAM picker.
 */
export function mayAdministrateChains(session: SessionInfo | null | undefined): boolean {
  return (
    isOrga(session) ||
    (session?.units ?? []).some((unit) => unit.admin && unit.function === WorkingGroupFunction.StoreChains)
  );
}

/** KAMs edit their chain (but not the KAM list). */
export function isKamOf(chain: StoreChain | undefined, userId: string | null | undefined): boolean {
  return !!chain && !!userId && chain.kams.some((kam) => kam.id === userId);
}
