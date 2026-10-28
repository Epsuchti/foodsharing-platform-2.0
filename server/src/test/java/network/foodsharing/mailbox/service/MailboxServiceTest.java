package network.foodsharing.mailbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.mailbox.domain.Mailbox;
import network.foodsharing.mailbox.domain.MailboxOwnerType;
import network.foodsharing.mailbox.repository.MailboxMemberRepository;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.testsupport.JpaSliceTest;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.repository.UserRepository;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** A35/MB1/MB6: email-name rules, uniqueness across region and personal mailboxes, creation and member rights. */
@JpaSliceTest
@Import({MailboxService.class, MailboxAccessService.class, MailAttachmentStore.class, UserService.class})
class MailboxServiceTest {

    @Autowired
    private MailboxService service;
    @Autowired
    private MailboxAccessService access;
    @Autowired
    private MailboxMemberRepository members;
    @Autowired
    private UserRepository users;
    @MockitoBean
    private RegionService regions;
    @MockitoBean
    private PermissionService permissions;
    @MockitoBean
    private UploadService uploads;

    private final UUID regionId = UUID.randomUUID();
    private SessionPrincipal admin;
    private User ambassador;
    private User storeManager;
    private User foodsaver;

    @BeforeEach
    void setUp() {
        ambassador = users.saveAndFlush(TestUsers.user("mb-amb@test.local", Role.AMBASSADOR));
        storeManager = users.saveAndFlush(TestUsers.user("mb-sm@test.local", Role.STORE_MANAGER));
        foodsaver = users.saveAndFlush(TestUsers.user("mb-fs@test.local", Role.FOODSAVER));
        admin = TestUsers.principal(ambassador);
        Region region = new Region();
        region.setName("Testregion");
        when(regions.require(regionId)).thenReturn(region);
        when(regions.adminIds(regionId)).thenReturn(Set.of(ambassador.getId()));
        when(permissions.mayAdministrateRegion(eq(admin), eq(regionId))).thenReturn(true);
        when(permissions.isAmbassador(eq(admin), eq(regionId))).thenReturn(true);
    }

    @Test
    void emailNamesAreNormalisedValidatedAndNotReserved() {
        assertThat(service.validateEmailName(" Zuerich-Nord ", null)).isEqualTo("zuerich-nord");
        for (String invalid : List.of("a", "-start", "with space", "umläut", "a..b", "end.", "x_y", "")) {
            assertThatThrownBy(() -> service.validateEmailName(invalid, null)).as(invalid)
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.EMAIL_NAME_INVALID));
        }
        assertThatThrownBy(() -> service.validateEmailName("Postmaster", null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.EMAIL_NAME_RESERVED));
    }

    @Test
    void emailNamesAreUniqueAcrossRegionAndPersonalMailboxes() {
        Mailbox region = service.createForRegion(admin, regionId, "team", null);
        assertThat(region.getName()).isEqualTo("Testregion");
        assertThat(service.address(region)).isEqualTo("team@local.test");
        when(permissions.isOrga(any())).thenReturn(true);
        assertThatThrownBy(() -> service.createForUser(admin, ambassador.getId(), "TEAM", null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.EMAIL_NAME_TAKEN));
        assertThatThrownBy(() -> service.createForRegion(admin, regionId, "team2", null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.MAILBOX_ALREADY_EXISTS));
        assertThat(service.update(admin, region.getId(), "team", "Neu").getName()).isEqualTo("Neu");
    }

    @Test
    void regionMailboxesNeedRegionAdministration() {
        SessionPrincipal outsider = TestUsers.principal(foodsaver);
        doThrow(ApiException.forbidden(ApiErrorCode.ACCESS_DENIED)).when(permissions)
                .requireMayAdministrateRegion(outsider, regionId);
        assertThatThrownBy(() -> service.createForRegion(outsider, regionId, "nope", null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void personalMailboxesOnlyForAmbassadorsByOrgaOrUserAdministrators() {
        SessionPrincipal orga = TestUsers.principal(storeManager);
        assertThatThrownBy(() -> service.createForUser(orga, ambassador.getId(), "anna", null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.ACCESS_DENIED));
        when(permissions.isOrga(orga)).thenReturn(true);
        assertThatThrownBy(() -> service.createForUser(orga, foodsaver.getId(), "fs", null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.MAILBOX_NOT_ALLOWED));
        Mailbox personal = service.createForUser(orga, ambassador.getId(), "anna", null);
        assertThat(personal.getOwnerType()).isEqualTo(MailboxOwnerType.USER);
        assertThat(users.findById(ambassador.getId()).map(User::getMailboxId)).isEqualTo(Optional.of(personal.getId()));
        assertThat(access.mayRead(admin, personal)).isTrue();
        assertThatThrownBy(() -> service.createForUser(orga, ambassador.getId(), "anna2", null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.MAILBOX_ALREADY_EXISTS));
    }

    @Test
    void membersAreManagedByAdminsAndNeedAMailboxRole() {
        Mailbox mailbox = service.createForRegion(admin, regionId, "members", null);
        SessionPrincipal outsider = TestUsers.principal(foodsaver);
        assertThatThrownBy(() -> service.setMembers(outsider, mailbox.getId(), List.of(storeManager.getId())))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.ACCESS_DENIED));
        assertThatThrownBy(() -> service.setMembers(admin, mailbox.getId(), List.of(foodsaver.getId())))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> service.setMembers(admin, mailbox.getId(), List.of(UUID.randomUUID())))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.USER_NOT_FOUND));
        service.setMembers(admin, mailbox.getId(), List.of(storeManager.getId()));
        assertThat(access.readers(mailbox)).containsExactlyInAnyOrder(ambassador.getId(), storeManager.getId());
        assertThat(access.mayRead(TestUsers.principal(storeManager), mailbox)).isTrue();
        service.setMembers(admin, mailbox.getId(), List.of());
        assertThat(members.findByMailboxId(mailbox.getId())).isEmpty();
        assertThat(access.mayRead(TestUsers.principal(storeManager), mailbox)).isFalse();
        assertThat(access.visibleMailboxes(admin)).isEmpty();
    }
}
