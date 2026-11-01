package network.foodsharing.user.domain;

/**
 * "Contacts of X" (decision 147): the people search of {@code /api/search} and the user pickers only find users who
 * share an active region/group membership, a store team (member or jumper), or a conversation with X, or who are a
 * confirmed buddy of X. One SQL condition shared by {@code UserRepository.searchByNamePrefix} and the read-only USER
 * branch of {@code search.repository.SearchIndexRepository} (decision 77); PostgreSQL and H2.
 */
public final class UserContacts {

    /**
     * Condition on a {@code user_account u} row: {@code u} is a contact of the user given as the named parameter
     * {@code :contactOf} (a UUID).
     */
    public static final String CONDITION = "(exists (select 1 from region_membership ca join region_membership cb"
            + " on ca.region_id = cb.region_id where ca.user_id = u.id and cb.user_id = :contactOf"
            + " and ca.active = true and cb.active = true)"
            + " or exists (select 1 from store_membership sa join store_membership sb on sa.store_id = sb.store_id"
            + " where sa.user_id = u.id and sb.user_id = :contactOf and sa.status in ('MEMBER', 'JUMPER')"
            + " and sb.status in ('MEMBER', 'JUMPER'))"
            + " or exists (select 1 from conversation_member ma join conversation_member mb"
            + " on ma.conversation_id = mb.conversation_id where ma.user_id = u.id and mb.user_id = :contactOf)"
            + " or exists (select 1 from buddy bu where bu.status = 'CONFIRMED' and ((bu.user_id = :contactOf"
            + " and bu.buddy_id = u.id) or (bu.buddy_id = :contactOf and bu.user_id = u.id))))";

    private UserContacts() {
    }
}
