package cv.igrp.platform.access_management.compat;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A minimal browser for the login flow tests: keeps cookies per host, follows
 * redirects one hop at a time (so a test can stop at the hop it cares about)
 * and submits the IdP's HTML login form.
 *
 * <p>The application sets its session cookie {@code Secure}, as it must behind
 * HTTPS in production. The tests talk plain HTTP to localhost, and the JDK's
 * cookie store would drop a {@code Secure} cookie there, so this browser strips
 * the attribute on receipt, playing the part of the TLS-terminating proxy
 * instead of weakening the server's cookie configuration.
 */
final class TestBrowser {

    private static final Pattern LOGIN_FORM_ACTION =
            Pattern.compile("<form[^>]*id=\"kc-form-login\"[^>]*action=\"([^\"]+)\"", Pattern.DOTALL);

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .cookieHandler(new InsecureTransportCookieHandler())
            .build();

    HttpResponse<String> get(String url) {
        return send(HttpRequest.newBuilder(URI.create(url)).GET().build());
    }

    HttpResponse<String> postForm(String url, Map<String, String> form) {
        return send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encode(form)))
                .build());
    }

    /**
     * Follows redirects from {@code response} until one points at a URL that
     * {@code stopAt} accepts (returned without being requested), or a
     * non-redirect response arrives (returned as is).
     */
    Hop followUntil(HttpResponse<String> response, Predicate<String> stopAt) {
        List<String> trail = new ArrayList<>();
        HttpResponse<String> current = response;
        for (int i = 0; i < 20; i++) {
            if (current.statusCode() / 100 != 3) {
                return new Hop(current, null, trail);
            }
            String location = current.headers().firstValue("Location")
                    .orElseThrow(() -> new IllegalStateException("redirect without Location"));
            String next = current.uri().resolve(location).toString();
            trail.add(current.statusCode() + " -> " + next);
            if (stopAt.test(next)) {
                return new Hop(current, next, trail);
            }
            current = get(next);
        }
        throw new IllegalStateException("too many redirects: " + trail);
    }

    /** Submits the Keycloak login form found on {@code loginPage}. */
    HttpResponse<String> submitLogin(HttpResponse<String> loginPage, String username, String password) {
        Matcher matcher = LOGIN_FORM_ACTION.matcher(loginPage.body());
        if (!matcher.find()) {
            throw new IllegalStateException("no login form on " + loginPage.uri() + ":\n" + loginPage.body());
        }
        String action = matcher.group(1).replace("&amp;", "&");
        Map<String, String> form = new LinkedHashMap<>();
        form.put("username", username);
        form.put("password", password);
        form.put("credentialId", "");
        return postForm(loginPage.uri().resolve(action).toString(), form);
    }

    record Hop(HttpResponse<String> response, String stoppedAt, List<String> trail) {
    }

    static Map<String, String> queryParameters(String url) {
        Map<String, String> params = new LinkedHashMap<>();
        String query = URI.create(url).getRawQuery();
        if (query == null) {
            return params;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            params.put(java.net.URLDecoder.decode(key, StandardCharsets.UTF_8),
                    java.net.URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return params;
    }

    static String encode(Map<String, String> form) {
        return form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** A cookie store that treats plain-HTTP localhost as the TLS endpoint it stands in for. */
    private static final class InsecureTransportCookieHandler extends CookieHandler {

        private final CookieManager delegate = new CookieManager(null, CookiePolicy.ACCEPT_ALL);

        @Override
        public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) throws IOException {
            return delegate.get(uri, requestHeaders);
        }

        @Override
        public void put(URI uri, Map<String, List<String>> responseHeaders) throws IOException {
            Map<String, List<String>> stripped = new LinkedHashMap<>();
            responseHeaders.forEach((name, values) -> {
                if (name != null && name.equalsIgnoreCase("Set-Cookie")) {
                    stripped.put(name, values.stream()
                            .map(v -> v.replaceAll("(?i);\\s*Secure", ""))
                            .toList());
                } else {
                    stripped.put(name, values);
                }
            });
            delegate.put(uri, stripped);
        }
    }
}
