package network.foodsharing.dev.web;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.HtmlUtils;

/**
 * Redirect target of the seeded OAuth clients in local/e2e ({@code /dev/oauth-callback}): shows what the partner app
 * would receive. The code itself is read with {@code GET /api/dev/oauth/last-code}.
 */
@Controller
@Profile({"local", "e2e"})
public class DevOAuthCallbackController {

    @GetMapping(value = "/dev/oauth-callback", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
                           @RequestParam(required = false) String error) {
        String result = error != null ? "error: " + HtmlUtils.htmlEscape(error)
                : "code received" + (code == null ? "" : " (" + code.length() + " characters)");
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>OAuth callback</title></head>"
                + "<body><h1 data-testid=\"dev.oauth-callback.result\">" + result + "</h1>"
                + (state == null ? "" : "<p data-testid=\"dev.oauth-callback.state\">" + HtmlUtils.htmlEscape(state)
                + "</p>") + "</body></html>";
    }
}
