/**
 * Minimal file uploads (docs/conventions.md A34): owner-only until claimed by a module, read through that
 * module's {@code UploadAccessRule}.
 */
@ApplicationModule(displayName = "Uploads")
package network.foodsharing.upload;

import org.springframework.modulith.ApplicationModule;
