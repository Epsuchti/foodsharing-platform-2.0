package network.foodsharing.fsp.service;

import network.foodsharing.fsp.domain.FspStatus;

/**
 * Food share point permission matrix (FS1, upstream {@code FoodSharePointPermissions}), pure so it is unit tested
 * without a database. {@code mayAdd} = ORGA, admins of the region's FSP function group, or — when the region has no
 * FSP group — administrators of the region or an ancestor (A10).
 */
public final class FspRules {

    /** What the permission service knows about one viewer and one FSP. */
    public record Facts(boolean mayAdd, boolean regionMember, boolean manager, boolean follower, boolean creator,
                        FspStatus status) {
    }

    private FspRules() {
    }

    /** Status of a new FSP; null = the actor may not create one in the region. */
    public static FspStatus createStatus(boolean mayAdd, boolean regionMember) {
        if (mayAdd) {
            return FspStatus.ACTIVE;
        }
        return regionMember ? FspStatus.INACTIVE : null;
    }

    /** INACTIVE FSPs are visible to the people who may edit or activate them and to their creator. */
    public static boolean mayView(Facts facts) {
        return facts.status() == FspStatus.ACTIVE || mayEdit(facts) || facts.creator();
    }

    public static boolean mayEdit(Facts facts) {
        return facts.mayAdd() || facts.manager();
    }

    public static boolean mayDelete(Facts facts) {
        return facts.mayAdd();
    }

    public static boolean mayActivate(Facts facts) {
        return facts.mayAdd();
    }

    /** Region members and followers write on the wall of an active FSP; editors always. */
    public static boolean mayPost(Facts facts) {
        if (mayEdit(facts)) {
            return true;
        }
        return facts.status() == FspStatus.ACTIVE && (facts.regionMember() || facts.follower() || facts.manager());
    }

    /** Every signed-in user may follow an active FSP. */
    public static boolean mayFollow(Facts facts) {
        return facts.status() == FspStatus.ACTIVE;
    }

    /** Wall posts: ORGA / FSP group admins ({@code mayAdd}) and the author. */
    public static boolean mayDeletePost(Facts facts, boolean author) {
        return facts.mayAdd() || author;
    }
}
