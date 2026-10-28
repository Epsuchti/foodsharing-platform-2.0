package network.foodsharing.mailbox.service;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Mail address helpers: strict parsing of recipients and the JSON form stored in {@code to_json}/{@code cc_json}. */
public final class MailAddresses {

    /** One address with an optional display name. */
    public record Address(String address, String name) {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<Address>> LIST = new TypeReference<>() {
    };

    private MailAddresses() {
    }

    /**
     * Strictly parses each entry (RFC 822, one address per entry, a domain required).
     *
     * @return the addresses in order, de-duplicated case-insensitively
     * @throws IllegalArgumentException with the offending entry
     */
    public static List<Address> parseStrict(Collection<String> entries) {
        Map<String, Address> result = new LinkedHashMap<>();
        if (entries == null) {
            return List.of();
        }
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            try {
                InternetAddress[] parsed = InternetAddress.parse(entry.strip(), true);
                if (parsed.length != 1) {
                    throw new IllegalArgumentException(entry);
                }
                parsed[0].validate();
                String address = parsed[0].getAddress();
                int at = address.indexOf('@');
                if (at < 1 || at == address.length() - 1) {
                    throw new IllegalArgumentException(entry);
                }
                result.putIfAbsent(address.toLowerCase(Locale.ROOT), new Address(address, parsed[0].getPersonal()));
            } catch (AddressException ex) {
                throw new IllegalArgumentException(entry, ex);
            }
        }
        return new ArrayList<>(result.values());
    }

    public static String toJson(List<Address> addresses) {
        return JSON.writeValueAsString(addresses == null ? List.of() : addresses);
    }

    public static List<Address> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return JSON.readValue(json, LIST);
    }

    /** {@code "Name <address>"} or the bare address. */
    public static String format(Address address) {
        try {
            return new InternetAddress(address.address(), address.name(), "UTF-8").toString();
        } catch (java.io.UnsupportedEncodingException ex) {
            return address.address();
        }
    }
}
