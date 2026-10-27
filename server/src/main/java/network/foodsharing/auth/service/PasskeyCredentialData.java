package network.foodsharing.auth.service;

/**
 * A WebAuthn credential record as handed over by Spring Security (binary values base64url-encoded, transports as a
 * comma-separated list). {@code userHandle} is the WebAuthn user handle, not the user id.
 */
public record PasskeyCredentialData(String credentialId, String userHandle, String publicKeyCose, long signatureCount,
                                    String transports, String label, String credentialType, boolean uvInitialized,
                                    boolean backupEligible, boolean backupState, String attestationObject,
                                    String attestationClientDataJson) {
}
