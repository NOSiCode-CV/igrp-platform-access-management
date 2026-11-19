package cv.igrp.platform.access_management.page_access.domain.service;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.OAuthClientEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.OAuthClientJpaRepository;
import cv.igrp.platform.access_management.page_access.application.dto.ApplicationPageDTO;
import cv.igrp.platform.access_management.page_access.application.dto.ManifestRegistrationRequestDTO;
import cv.igrp.platform.access_management.page_access.application.dto.ManifestRegistrationRequestDTO.OptionalPermissionDTO;
import cv.igrp.platform.access_management.page_access.application.dto.ManifestRegistrationRequestDTO.PageManifestDTO;
import cv.igrp.platform.access_management.page_access.application.dto.ManifestRegistrationResultDTO;
import cv.igrp.platform.access_management.routes.domain.service.RouteKeys;
import cv.igrp.platform.access_management.routes.domain.service.ServiceVersions;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import cv.igrp.platform.access_management.shared.security.ServiceAccountTokenClaims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Stores a frontend's page manifests (v3-27, #35; spec "Frontend", page model),
 * with the version rules of route registration:
 * <ul>
 *   <li>any registration, of any version, creates the pages it declares that
 *       are missing, and brings back a deleted page it declares;</li>
 *   <li>only a registration from the newest version seen for the application
 *       changes a page's path or keys, and marks the application's live pages
 *       it no longer declares DELETED.</li>
 * </ul>
 * Unlike route keys, a page has no assignments of its own, so a page the newest
 * version drops is deleted at once, with no grace period.
 *
 * <p>The pages belong to the calling client's application: a manifest naming
 * another application is refused, and so is a client without one. Replicas of
 * a frontend register at the same moment on a deploy: a transaction-scoped
 * advisory lock per application serialises them.
 */
@Service
public class PageManifestService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PageManifestService.class);

    /** First half of the two-int advisory lock key, distinct from route registration's. */
    private static final int LOCK_CLASS = 0x50414745; // "PAGE"

    private static final String PERMISSION_KEY = "permission_key";
    private static final String PAGE_KEY = "page_key";
    private static final Pattern PAGE_NAME = Pattern.compile("[A-Za-z0-9_-]+");

    private static final String PAGES = """
            SELECT p.id, p.page_key, p.path, p.status, p.last_seen_version
              FROM t_application_page p WHERE p.application_id = ?""";

    private static final String PAGE_PERMISSIONS = """
            SELECT pp.page_id, pp.permission_key, pp.required, pp.label
              FROM t_application_page_permission pp
              JOIN t_application_page p ON p.id = pp.page_id
             WHERE p.application_id = ?""";

    private static final String LIVE_PAGES = """
            SELECT p.id, p.page_key, p.path FROM t_application_page p
              JOIN t_application a ON a.id = p.application_id
             WHERE a.code = ? AND p.status = 'ACTIVE'
             ORDER BY p.path, p.page_key""";

    private static final String LIVE_PAGE_PERMISSIONS = """
            SELECT pp.page_id, pp.permission_key, pp.required, pp.label
              FROM t_application_page_permission pp
              JOIN t_application_page p ON p.id = pp.page_id
              JOIN t_application a ON a.id = p.application_id
             WHERE a.code = ? AND p.status = 'ACTIVE'
             ORDER BY pp.permission_key""";

    private final OAuthClientJpaRepository clients;
    private final AuthenticationHelper authenticationHelper;
    private final JdbcTemplate jdbc;

    public PageManifestService(OAuthClientJpaRepository clients, AuthenticationHelper authenticationHelper,
                               JdbcTemplate jdbc) {
        this.clients = clients;
        this.authenticationHelper = authenticationHelper;
        this.jdbc = jdbc;
    }

    /** A page as stored, with its keys. */
    private record Stored(int id, String key, String path, boolean live, String version, Definition definition) {
    }

    /** What a page declares: its path, its required keys and its optional keys with their labels. */
    private record Definition(String path, Set<String> required, Map<String, String> optional) {

        static Definition of(PageManifestDTO manifest) {
            Map<String, String> optional = new TreeMap<>();
            for (OptionalPermissionDTO o : manifest.optionalKeys()) {
                optional.put(o.key(), o.label());
            }
            return new Definition(manifest.path(), new HashSet<>(manifest.requiredKeys()), optional);
        }
    }

    /** A frontend's registration, with the calling service account's token. */
    @Transactional
    public ManifestRegistrationResultDTO register(ManifestRegistrationRequestDTO registration) {
        ApplicationEntity application = callerApplication();
        Map<String, PageManifestDTO> declared = validated(application.getCode(), registration.pages());
        String version = registration.version();

        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?, ?)::text", String.class, LOCK_CLASS,
                application.getId());
        Map<String, Stored> stored = stored(application.getId());
        boolean newest = stored.values().stream().map(Stored::version).filter(Objects::nonNull)
                .allMatch(seen -> ServiceVersions.compare(version, seen) >= 0);

        int deleted = 0;
        if (newest) {
            deleted = clearTheWay(stored, declared);
        }

        int created = 0;
        int updated = 0;
        for (PageManifestDTO manifest : declared.values()) {
            Outcome outcome = apply(application.getId(), manifest, stored, version, newest);
            if (outcome == Outcome.CREATED) {
                created++;
            } else if (outcome == Outcome.UPDATED) {
                updated++;
            }
        }

        int unchanged = declared.size() - created - updated;
        LOGGER.info("[PAGES] {} {} registered {} pages: {} created, {} updated, {} unchanged, {} deleted",
                application.getCode(), version, declared.size(), created, updated, unchanged, deleted);
        return new ManifestRegistrationResultDTO(created, updated, unchanged, deleted);
    }

    /** What a registration did to one declared page. */
    private enum Outcome { CREATED, UPDATED, UNCHANGED }

    /** Writes one declared page as the version rules say. */
    private Outcome apply(int applicationId, PageManifestDTO manifest, Map<String, Stored> stored, String version,
                          boolean newest) {
        Stored page = stored.get(manifest.page());
        Definition definition = Definition.of(manifest);
        if (page != null && page.live()) {
            if (!newest) {
                return Outcome.UNCHANGED;
            }
            if (!definition.equals(page.definition())) {
                write(page.id(), definition, version);
                return Outcome.UPDATED;
            }
            jdbc.update("UPDATE t_application_page SET last_seen_version = ? WHERE id = ?", version, page.id());
            return Outcome.UNCHANGED;
        }
        // Missing, or deleted and declared again: it comes back as this version declares it.
        // An older version never takes a path a live page has.
        if (!newest && !pathFree(definition.path(), page, stored)) {
            return Outcome.UNCHANGED;
        }
        if (page == null) {
            insert(applicationId, manifest.page(), definition, version);
        } else {
            write(page.id(), definition, newest ? version : page.version());
        }
        return Outcome.CREATED;
    }

    /** The application's live pages, by path, with their keys. */
    @Transactional(readOnly = true)
    public List<ApplicationPageDTO> pages(String applicationCode) {
        Map<Integer, List<String>> required = new HashMap<>();
        Map<Integer, List<OptionalPermissionDTO>> optional = new HashMap<>();
        jdbc.query(LIVE_PAGE_PERMISSIONS, rs -> {
            int pageId = rs.getInt("page_id");
            if (rs.getBoolean("required")) {
                required.computeIfAbsent(pageId, id -> new ArrayList<>()).add(rs.getString(PERMISSION_KEY));
            } else {
                optional.computeIfAbsent(pageId, id -> new ArrayList<>())
                        .add(new OptionalPermissionDTO(rs.getString(PERMISSION_KEY), rs.getString("label")));
            }
        }, applicationCode);
        return jdbc.query(LIVE_PAGES, (rs, i) -> {
            int id = rs.getInt("id");
            return new ApplicationPageDTO(rs.getString(PAGE_KEY), rs.getString("path"),
                    required.getOrDefault(id, List.of()), optional.getOrDefault(id, List.of()));
        }, applicationCode);
    }

    /**
     * For the newest version, before any page is written: marks the live pages
     * it no longer declares DELETED, and parks the live pages whose path changes
     * on a placeholder, so two pages may swap paths without breaking the
     * one-live-page-per-path rule.
     *
     * @return how many pages were deleted
     */
    private int clearTheWay(Map<String, Stored> stored, Map<String, PageManifestDTO> declared) {
        int deleted = 0;
        for (Stored page : stored.values()) {
            PageManifestDTO manifest = declared.get(page.key());
            if (!page.live()) {
                continue;
            }
            if (manifest == null) {
                jdbc.update("""
                        UPDATE t_application_page SET status = 'DELETED', last_modified_date = now()
                         WHERE id = ?""", page.id());
                deleted++;
            } else if (!manifest.path().equals(page.path())) {
                jdbc.update("UPDATE t_application_page SET path = ? WHERE id = ?", "#" + page.id(), page.id());
            }
        }
        return deleted;
    }

    /** Whether no live page but {@code self} has the path. */
    private static boolean pathFree(String path, Stored self, Map<String, Stored> stored) {
        return stored.values().stream().noneMatch(s -> s.live() && s != self && s.path().equals(path));
    }

    private void insert(int applicationId, String key, Definition definition, String version) {
        Integer id = jdbc.queryForObject("""
                INSERT INTO t_application_page (page_key, application_id, path, last_seen_version)
                VALUES (?, ?, ?, ?) RETURNING id""", Integer.class, key, applicationId, definition.path(), version);
        writePermissions(Objects.requireNonNull(id), definition);
    }

    /** Makes the page live, as the definition says. */
    private void write(int id, Definition definition, String version) {
        jdbc.update("""
                UPDATE t_application_page SET status = 'ACTIVE', path = ?, last_seen_version = ?,
                       last_modified_date = now() WHERE id = ?""", definition.path(), version, id);
        jdbc.update("DELETE FROM t_application_page_permission WHERE page_id = ?", id);
        writePermissions(id, definition);
    }

    private void writePermissions(int pageId, Definition definition) {
        for (String key : definition.required()) {
            jdbc.update("""
                    INSERT INTO t_application_page_permission (page_id, permission_key, required)
                    VALUES (?, ?, true)""", pageId, key);
        }
        definition.optional().forEach((key, label) -> jdbc.update("""
                INSERT INTO t_application_page_permission (page_id, permission_key, required, label)
                VALUES (?, ?, false, ?)""", pageId, key, label));
    }

    private Map<String, Stored> stored(int applicationId) {
        Map<Integer, Set<String>> required = new HashMap<>();
        Map<Integer, Map<String, String>> optional = new HashMap<>();
        jdbc.query(PAGE_PERMISSIONS, rs -> {
            int pageId = rs.getInt("page_id");
            if (rs.getBoolean("required")) {
                required.computeIfAbsent(pageId, id -> new HashSet<>()).add(rs.getString(PERMISSION_KEY));
            } else {
                optional.computeIfAbsent(pageId, id -> new TreeMap<>())
                        .put(rs.getString(PERMISSION_KEY), rs.getString("label"));
            }
        }, applicationId);
        Map<String, Stored> pages = new LinkedHashMap<>();
        jdbc.query(PAGES, rs -> {
            int id = rs.getInt("id");
            String path = rs.getString("path");
            pages.put(rs.getString(PAGE_KEY), new Stored(id, rs.getString(PAGE_KEY), path,
                    "ACTIVE".equals(rs.getString("status")), rs.getString("last_seen_version"),
                    new Definition(path, required.getOrDefault(id, Set.of()), optional.getOrDefault(id, Map.of()))));
        }, applicationId);
        return pages;
    }

    /** Checks every manifest; returns them by page key. */
    private static Map<String, PageManifestDTO> validated(String applicationCode, List<PageManifestDTO> manifests) {
        String prefix = "app." + applicationCode + ".";
        Map<String, PageManifestDTO> pages = new LinkedHashMap<>();
        Set<String> paths = new HashSet<>();
        for (PageManifestDTO manifest : manifests) {
            String problem = problem(applicationCode, prefix, manifest);
            if (problem == null && pages.containsKey(manifest.page())) {
                problem = "page " + manifest.page() + " is declared twice";
            }
            if (problem == null && !paths.add(manifest.path())) {
                problem = "path " + manifest.path() + " is declared by two pages";
            }
            if (problem != null) {
                throw IgrpResponseStatusException.badRequest("Invalid page manifest: " + problem);
            }
            pages.put(manifest.page(), manifest);
        }
        return pages;
    }

    private static String problem(String applicationCode, String prefix, PageManifestDTO manifest) {
        if (!applicationCode.equals(manifest.application())) {
            return "page " + manifest.page() + " names application " + manifest.application()
                    + ", not the caller's " + applicationCode;
        }
        String name = manifest.page().startsWith(prefix) ? manifest.page().substring(prefix.length()) : "";
        if (!isPageName(name)) {
            return "page key " + manifest.page() + " is not " + prefix + "<page>";
        }
        if (!isSafePath(manifest.path())) {
            return "path " + manifest.path() + " of " + manifest.page() + " is not a valid page path";
        }
        Set<String> keys = new HashSet<>();
        List<String> all = new ArrayList<>(manifest.requiredKeys());
        manifest.optionalKeys().forEach(o -> all.add(o.key()));
        for (String key : all) {
            String keyProblem = keyProblem(key);
            if (keyProblem != null) {
                return keyProblem + " (page " + manifest.page() + ")";
            }
            if (!keys.add(key)) {
                return "key " + key + " appears twice in " + manifest.page();
            }
        }
        return null;
    }

    /** Dot-separated segments of letters, digits, '-' or '_'. */
    private static boolean isPageName(String name) {
        if (name.isEmpty()) {
            return false;
        }
        for (String segment : name.split("\\.", -1)) {
            if (!PAGE_NAME.matcher(segment).matches()) {
                return false;
            }
        }
        return true;
    }

    /** An absolute App Router path, catch-alls last, with no segment a router could resolve elsewhere. */
    static boolean isSafePath(String path) {
        if (!PagePaths.isValid(path) || path.contains("\\") || path.contains("%") || path.contains("//")) {
            return false;
        }
        for (String segment : path.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    /** A permission key of any service: {@code <service>.<resource...>.<action>}. */
    static String keyProblem(String key) {
        if (key == null) {
            return "a key is missing";
        }
        int dot = key.indexOf('.');
        if (dot <= 0) {
            return "key " + key + " is not <service>.<resource>.<action>";
        }
        String service = key.substring(0, dot);
        if (!RouteKeys.isValidService(service)) {
            return "key " + key + " has an invalid service";
        }
        return RouteKeys.problem(service, key);
    }

    private ApplicationEntity callerApplication() {
        Jwt jwt = authenticationHelper.getJwtToken();
        if (!ServiceAccountTokenClaims.PRINCIPAL_TYPE_SERVICE_ACCOUNT.equals(
                jwt.getClaimAsString(ServiceAccountTokenClaims.CLAIM_PRINCIPAL_TYPE))) {
            throw IgrpResponseStatusException.forbidden("Manifest registration needs a service account token");
        }
        String clientId = jwt.getClaimAsString(ServiceAccountTokenClaims.CLAIM_CLIENT_ID);
        return clients.findByClientId(clientId == null ? "" : clientId)
                .map(OAuthClientEntity::getApplication)
                .orElseThrow(() -> IgrpResponseStatusException.of(HttpStatus.CONFLICT,
                        "Client " + clientId + " is not linked to an application, so its pages could not be "
                                + "registered. Link the client to its application in App Center, then restart "
                                + "the frontend."));
    }
}
