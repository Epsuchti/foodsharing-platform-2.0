package network.foodsharing.profile.service;

import com.lowagie.text.Document;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.Gender;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.domain.UserAction;
import network.foodsharing.user.service.BusinessCardPort;
import network.foodsharing.user.service.UserService;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business card (A14, upstream {@code BusinessCardControl}): 85 × 55 mm PDF (OpenPDF) with name, gendered role
 * label ({@code businessCard.role.<ROLE>.<f|m|n>} in {@code i18n/user}), "foodsharing &lt;region&gt;", contact email
 * (personal mailbox address if any, else the account email) and mobile phone. Only verified users; the role is at
 * most the own role; the region is one of the user's regions, for AMBASSADOR one they are ambassador of.
 */
@Service
public class BusinessCardService implements BusinessCardPort {

    static final Set<Role> CARD_ROLES = Set.of(Role.FOODSAVER, Role.STORE_MANAGER, Role.AMBASSADOR);
    static final float WIDTH = 85f / 25.4f * 72f;
    static final float HEIGHT = 55f / 25.4f * 72f;
    private static final Color GREEN = new Color(0x25, 0x83, 0x4a);
    private static final Color GREY = new Color(0x56, 0x63, 0x5a);

    private final UserService users;
    private final RegionService regions;
    private final CurrentActorService actors;
    private final PermissionService permissions;
    private final HistoryService history;
    private final MessageSource messages;
    private final List<PersonalMailboxAddress> mailboxAddresses;

    public BusinessCardService(UserService users, RegionService regions, CurrentActorService actors,
                               PermissionService permissions, HistoryService history, MessageSource messages,
                               List<PersonalMailboxAddress> mailboxAddresses) {
        this.users = users;
        this.regions = regions;
        this.actors = actors;
        this.permissions = permissions;
        this.history = history;
        this.messages = messages;
        this.mailboxAddresses = mailboxAddresses;
    }

    /** The rule (pure): empty = allowed, else the error code. */
    static Optional<ApiErrorCode> check(boolean verified, Role own, Role requested, boolean member,
                                        boolean ambassador) {
        if (!verified || requested == null || !CARD_ROLES.contains(requested)
                || !PermissionService.isAtLeast(own, requested)) {
            return Optional.of(ApiErrorCode.BUSINESS_CARD_NOT_ALLOWED);
        }
        if (!member) {
            return Optional.of(ApiErrorCode.NOT_REGION_MEMBER);
        }
        if (requested == Role.AMBASSADOR && !ambassador) {
            return Optional.of(ApiErrorCode.BUSINESS_CARD_NOT_ALLOWED);
        }
        return Optional.empty();
    }

    static String genderKey(Gender gender) {
        return gender == Gender.FEMALE ? "f" : gender == Gender.MALE ? "m" : "n";
    }

    @Override
    @Transactional
    public byte[] render(String roleName, UUID regionId) {
        SessionPrincipal actor = actors.requirePrincipal();
        User user = users.requireActive(actor.userId());
        Role role;
        try {
            role = roleName == null ? null : Role.valueOf(roleName);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "role");
        }
        Region region = regions.find(regionId)
                .orElseThrow(() -> ApiException.badRequest(ApiErrorCode.NOT_REGION_MEMBER));
        Optional<ApiErrorCode> denied = check(user.isVerified(), user.getRole(), role,
                permissions.isMemberOf(actor, regionId), permissions.isAmbassador(actor, regionId));
        if (denied.isPresent()) {
            throw denied.get() == ApiErrorCode.NOT_REGION_MEMBER ? ApiException.badRequest(denied.get())
                    : ApiException.forbidden(denied.get());
        }
        Locale locale = Locale.forLanguageTag(user.getPreferredLanguage() == null ? "de"
                : user.getPreferredLanguage().getValue());
        String label = messages.getMessage("businessCard.role." + role.name() + "." + genderKey(user.getGender()),
                null, role.name(), locale);
        String email = mailboxAddresses.stream().map(lookup -> lookup.of(user.getId())).flatMap(Optional::stream)
                .findFirst().orElse(user.getEmail());
        String name = user.getFirstName() != null && user.getLastName() != null
                ? user.getFirstName() + " " + user.getLastName() : user.getDisplayName();
        byte[] pdf = pdf(name, label, "foodsharing " + region.getName(), email, user.getMobilePhone());
        history.record(history.event(UserAction.BUSINESS_CARD_CREATED, user.getId()).region(regionId)
                .metadata("role", role.name()).build());
        return pdf;
    }

    static byte[] pdf(String name, String role, String region, String email, String phone) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(new Rectangle(WIDTH, HEIGHT), 0, 0, 0, 0);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.addTitle("foodsharing – " + name);
            document.open();
            BaseFont regular = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            BaseFont bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            PdfContentByte canvas = writer.getDirectContent();
            canvas.setColorFill(GREEN);
            canvas.rectangle(0, HEIGHT - 6, WIDTH, 6);
            canvas.fill();
            text(canvas, bold, 15, GREEN, 14, HEIGHT - 30, "foodsharing");
            text(canvas, bold, 11, Color.BLACK, 14, HEIGHT - 58, name);
            text(canvas, regular, 8.5f, GREY, 14, HEIGHT - 71, role);
            text(canvas, regular, 8.5f, GREY, 14, HEIGHT - 82, region);
            float y = 30;
            if (phone != null && !phone.isBlank()) {
                text(canvas, regular, 8, Color.BLACK, 14, y, phone);
                y += 11;
            }
            text(canvas, regular, 8, Color.BLACK, 14, y, email);
            text(canvas, regular, 7, GREEN, 14, 12, "foodsharing.network");
            document.close();
        } catch (Exception ex) {
            throw new IllegalStateException("Business card rendering failed", ex);
        }
        return out.toByteArray();
    }

    private static void text(PdfContentByte canvas, BaseFont font, float size, Color color, float x, float y,
                             String value) {
        canvas.beginText();
        canvas.setFontAndSize(font, size);
        canvas.setColorFill(color);
        canvas.setTextMatrix(x, y);
        canvas.showText(value == null ? "" : value);
        canvas.endText();
    }
}
