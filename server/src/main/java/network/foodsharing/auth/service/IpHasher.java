package network.foodsharing.auth.service;

import network.foodsharing.config.AppProperties;
import org.springframework.stereotype.Component;

/** Salted SHA-256 of client IPs ({@code app.auth.ip-salt}); IPs are never stored in clear. */
@Component
public class IpHasher {

    private final AppProperties properties;

    public IpHasher(AppProperties properties) {
        this.properties = properties;
    }

    public String hash(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        return TokenHasher.sha256Hex(properties.getAuth().getIpSalt() + "|" + ip.strip());
    }
}
