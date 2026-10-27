package network.foodsharing.verification.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.user.domain.User;
import network.foodsharing.verification.domain.PassportAction;
import network.foodsharing.verification.domain.WalletPass;
import network.foodsharing.verification.domain.WalletType;
import network.foodsharing.verification.repository.WalletPassRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Digital passport in Apple / Google Wallet for verified users with a valid passport; one {@code wallet_pass} per user
 * and wallet (serial, token, Google object id). 503 WALLET_NOT_CONFIGURED while the wallet is switched off.
 */
@Service
public class WalletService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final WalletConfiguration configuration;
    private final WalletPassRepository passes;
    private final PassportService passports;
    private final HistoryService history;
    private final Clock clock;

    public WalletService(WalletConfiguration configuration, WalletPassRepository passes, PassportService passports,
                         HistoryService history, Clock clock) {
        this.configuration = configuration;
        this.passes = passes;
        this.passports = passports;
        this.history = history;
        this.clock = clock;
    }

    /** @throws ApiException 403 NOT_VERIFIED, 409 PASSPORT_NOT_VALID, 503 WALLET_NOT_CONFIGURED */
    @Transactional
    public byte[] applePass() {
        User user = passports.requireVerifiedAndValid();
        if (!configuration.appleEnabled()) {
            throw notConfigured();
        }
        WalletPass pass = passOf(user, WalletType.APPLE);
        PassportService.Validity validity = passports.validity(user);
        PassportPdfRenderer.Card card = passports.card(user, validity);
        byte[] pkpass = configuration.apple().build(new AppleWalletPassBuilder.PassData(pass.getSerial(),
                pass.getAuthToken(), card.name(), card.foodsaverNumber(), card.regionName(),
                DATE.format(validity.validUntil()), card.qrUrl(), null));
        pass.setLastRenderedAt(clock.instant());
        passes.save(pass);
        history.record(history.event(PassportAction.WALLET_PASS_CREATED, user.getId())
                .metadata("wallet", WalletType.APPLE.name()).build());
        return pkpass;
    }

    /** @throws ApiException 403 NOT_VERIFIED, 409 PASSPORT_NOT_VALID, 503 WALLET_NOT_CONFIGURED */
    @Transactional
    public String googleSaveUrl() {
        User user = passports.requireVerifiedAndValid();
        if (!configuration.googleEnabled()) {
            throw notConfigured();
        }
        WalletPass pass = passOf(user, WalletType.GOOGLE);
        GoogleWalletLinkBuilder builder = configuration.google();
        pass.setObjectId(builder.objectId(pass.getSerial()));
        PassportService.Validity validity = passports.validity(user);
        PassportPdfRenderer.Card card = passports.card(user, validity);
        String url = builder.saveUrl(new GoogleWalletLinkBuilder.ObjectData(pass.getSerial(), card.name(),
                card.foodsaverNumber(), card.regionName(), DATE.format(validity.validUntil()), card.qrUrl()),
                clock.instant());
        pass.setLastRenderedAt(clock.instant());
        passes.save(pass);
        history.record(history.event(PassportAction.WALLET_PASS_CREATED, user.getId())
                .metadata("wallet", WalletType.GOOGLE.name()).build());
        return url;
    }

    private WalletPass passOf(User user, WalletType type) {
        return passes.findByUserIdAndWalletType(user.getId(), type).orElseGet(() -> {
            WalletPass created = new WalletPass();
            created.setUserId(user.getId());
            created.setWalletType(type);
            created.setSerial(token(16));
            created.setAuthToken(token(24));
            return created;
        });
    }

    private static String token(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return HexFormat.of().formatHex(value);
    }

    private static ApiException notConfigured() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.WALLET_NOT_CONFIGURED);
    }
}
