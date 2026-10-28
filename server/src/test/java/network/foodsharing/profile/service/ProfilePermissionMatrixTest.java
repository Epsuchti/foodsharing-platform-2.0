package network.foodsharing.profile.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import network.foodsharing.api.model.ApiErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Viewer × target rows per profile permission (upstream ProfilePermissions, BananaPermissions, profile wall). */
class ProfilePermissionMatrixTest {

    static final UUID A = UUID.fromString("00000000-0000-4000-8000-00000000000a");
    static final UUID B = UUID.fromString("00000000-0000-4000-8000-00000000000b");
    static final UUID C = UUID.fromString("00000000-0000-4000-8000-00000000000c");

    // facts: self, orga, administers, shareStoreTeam, managesStoreOfUser, reportButton
    // expected: edit, notes, report, history, commitments, administer, pickups, stores
    static Stream<Arguments> matrix() {
        return Stream.of(
                Arguments.of("self", f(true, false, false, false, false, false),
                        p(true, false, false, false, true, false, true, true)),
                Arguments.of("stranger", f(false, false, false, false, false, false),
                        p(false, false, false, false, false, false, false, false)),
                Arguments.of("stranger, report button", f(false, false, false, false, false, true),
                        p(false, false, true, false, false, false, false, false)),
                Arguments.of("orga", f(false, true, false, false, false, false),
                        p(true, true, false, true, true, true, true, true)),
                Arguments.of("ambassador", f(false, false, true, false, false, true),
                        p(true, true, true, true, true, true, true, true)),
                Arguments.of("team mate", f(false, false, false, true, false, false),
                        p(false, false, false, false, false, false, false, true)),
                Arguments.of("store manager", f(false, false, false, true, true, false),
                        p(false, false, false, false, true, false, false, true)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matrix")
    void permissions(String row, ProfileRules.Facts facts, ProfileRules.Permissions expected) {
        assertThat(ProfileRules.permissions(facts)).isEqualTo(expected);
    }

    @Test
    void bananaRules() {
        assertThat(ProfileRules.bananaDenied(A, true, B, true, false)).isEmpty();
        assertThat(ProfileRules.bananaDenied(A, true, A, true, false)).contains(ApiErrorCode.BANANA_NOT_ALLOWED);
        assertThat(ProfileRules.bananaDenied(A, false, B, true, false)).contains(ApiErrorCode.BANANA_NOT_ALLOWED);
        assertThat(ProfileRules.bananaDenied(A, true, B, false, false)).contains(ApiErrorCode.BANANA_NOT_ALLOWED);
        assertThat(ProfileRules.bananaDenied(A, true, B, true, true)).isEqualTo(
                Optional.of(ApiErrorCode.BANANA_ALREADY_GIVEN));
        assertThat(ProfileRules.bananaMessageValid("x".repeat(99))).isFalse();
        assertThat(ProfileRules.bananaMessageValid("  " + "x".repeat(99) + "  ")).isFalse();
        assertThat(ProfileRules.bananaMessageValid("x".repeat(100))).isTrue();
        assertThat(ProfileRules.bananaMessageValid(null)).isFalse();
        assertThat(ProfileRules.mayDeleteBanana(A, false, A, B)).isTrue();
        assertThat(ProfileRules.mayDeleteBanana(B, false, A, B)).isTrue();
        assertThat(ProfileRules.mayDeleteBanana(C, false, A, B)).isFalse();
        assertThat(ProfileRules.mayDeleteBanana(C, true, A, B)).isTrue();
    }

    @Test
    void wallRules() {
        assertThat(ProfileRules.mayWriteWall(true)).isTrue();
        assertThat(ProfileRules.mayWriteWall(false)).isFalse();
        assertThat(ProfileRules.mayDeleteWallPost(C, false, false, A, B)).isFalse();
        assertThat(ProfileRules.mayDeleteWallPost(A, false, false, A, B)).isTrue();
        assertThat(ProfileRules.mayDeleteWallPost(B, false, false, A, B)).isTrue();
        assertThat(ProfileRules.mayDeleteWallPost(C, true, false, A, B)).isTrue();
        assertThat(ProfileRules.mayDeleteWallPost(C, false, true, A, B)).isTrue();
    }

    private static ProfileRules.Facts f(boolean self, boolean orga, boolean admin, boolean team, boolean manages,
                                        boolean report) {
        return new ProfileRules.Facts(self, orga, admin, team, manages, report);
    }

    private static ProfileRules.Permissions p(boolean edit, boolean notes, boolean report, boolean history,
                                              boolean commitments, boolean administer, boolean pickups,
                                              boolean stores) {
        return new ProfileRules.Permissions(edit, notes, report, history, commitments, administer, pickups, stores);
    }
}
