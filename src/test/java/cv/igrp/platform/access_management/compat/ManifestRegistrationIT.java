package cv.igrp.platform.access_management.compat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v3-27 (#35): a frontend registers its page manifests with its service
 * account, with the create / keep / delete rules of route registration.
 */
class ManifestRegistrationIT extends AbstractCompatIT {

    private static final String REGISTER = "/api/manifests/register";

    @Autowired
    ApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;

    private CompatSupport api;
    private String admin;
    private String application;
    private String serviceToken;

    @BeforeEach
    void frontendWithServiceAccount() {
        api = new CompatSupport(context);
        admin = api.superadminToken();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        application = "MF_APP_" + suffix;
        int applicationId = api.createApplication(application);
        serviceToken = api.serviceToken(api.registerM2MClient("mf-" + suffix.toLowerCase() + "-client", applicationId));
    }

    @Test
    @DisplayName("Register creates the pages, then keeps them; the page check reads their required keys")
    void createsThenKeeps() {
        String pages = page("faturas", "/faturas", List.of("svc.faturas.list"), Map.of())
                + "," + page("fatura", "/faturas/[id]", List.of("svc.faturas.list", "svc.faturas.read"),
                Map.of("svc.faturas.margem.read", "Margem"));

        assertThat(register("1.0.0", pages)).containsEntry("created", 2).containsEntry("unchanged", 0);
        assertThat(register("1.0.0", pages)).containsEntry("created", 0).containsEntry("unchanged", 2)
                .containsEntry("updated", 0).containsEntry("deleted", 0);

        assertThat(livePages()).containsOnlyKeys(key("faturas"), key("fatura"));
        assertThat(jdbc.queryForList("""
                SELECT pp.permission_key || ':' || pp.required || ':' || coalesce(pp.label, '')
                  FROM t_application_page_permission pp JOIN t_application_page p ON p.id = pp.page_id
                 WHERE p.page_key = ? ORDER BY 1""", String.class, key("fatura")))
                .containsExactly("svc.faturas.list:true:", "svc.faturas.margem.read:false:Margem",
                        "svc.faturas.read:true:");

        // Root is allowed any registered page: the page check finds them.
        assertThat(CompatSupport.jsonObject(api.postJson("/api/authorize/page", admin,
                "{\"applicationCode\":" + CompatSupport.q(application) + ",\"path\":\"/faturas/7\"}").body()))
                .containsEntry("allowed", true).containsEntry("page", key("fatura"));
    }

    @Test
    @DisplayName("The newest version updates pages and deletes the ones it no longer declares; a page declared again comes back")
    void newestVersionUpdatesAndDeletes() {
        register("1.0.0", page("faturas", "/faturas", List.of("svc.faturas.list"), Map.of())
                + "," + page("relatorios", "/relatorios", List.of(), Map.of()));

        assertThat(register("1.1.0", page("faturas", "/faturas/lista", List.of("svc.faturas.read"), Map.of())))
                .containsEntry("updated", 1).containsEntry("deleted", 1).containsEntry("created", 0);
        assertThat(livePages()).containsExactly(Map.entry(key("faturas"), "/faturas/lista"));
        assertThat(jdbc.queryForList("""
                SELECT pp.permission_key FROM t_application_page_permission pp
                  JOIN t_application_page p ON p.id = pp.page_id WHERE p.page_key = ?""", String.class, key("faturas")))
                .containsExactly("svc.faturas.read");

        assertThat(register("1.2.0", page("faturas", "/faturas/lista", List.of("svc.faturas.read"), Map.of())
                + "," + page("relatorios", "/relatorios", List.of(), Map.of())))
                .containsEntry("created", 1).containsEntry("unchanged", 1);
        assertThat(livePages()).containsOnlyKeys(key("faturas"), key("relatorios"));
    }

    @Test
    @DisplayName("An older version creates the pages it is missing and changes nothing else")
    void olderVersionOnlyCreates() {
        register("2.0.0", page("faturas", "/faturas", List.of("svc.faturas.list"), Map.of()));

        Map<String, Object> result = register("1.9.0", page("faturas", "/old-faturas", List.of(), Map.of())
                + "," + page("hotfix", "/hotfix", List.of(), Map.of()));

        assertThat(result).containsEntry("created", 1).containsEntry("updated", 0).containsEntry("deleted", 0);
        assertThat(livePages()).containsEntry(key("faturas"), "/faturas").containsEntry(key("hotfix"), "/hotfix");
    }

    @Test
    @DisplayName("The pages of an application are listed with their required and optional keys")
    void listsPages() {
        register("1.0.0", page("fatura", "/faturas/[id]", List.of("svc.faturas.read"),
                Map.of("svc.faturas.margem.read", "Margem")) + "," + page("inicio", "/", List.of(), Map.of()));

        HttpResponse<String> listed = api.get("/api/applications/" + application + "/pages", admin);

        assertThat(listed.statusCode()).as(listed.body()).isEqualTo(200);
        List<Object> pages = CompatSupport.jsonArray(listed.body());
        assertThat(pages).hasSize(2);
        assertThat(entry(pages, 0)).containsEntry("page", key("inicio")).containsEntry("path", "/");
        assertThat(entry(pages, 1)).containsEntry("page", key("fatura"))
                .containsEntry("required", List.of("svc.faturas.read"))
                .containsEntry("optional", List.of(Map.of("key", "svc.faturas.margem.read", "label", "Margem")));
    }

    @Test
    @DisplayName("Bad manifests are refused: another application's page, duplicate path or key, an unsafe path")
    void refusesBadManifests() {
        assertThat(post("1.0.0", "{\"application\":\"OTHER_APP\",\"page\":\"app.OTHER_APP.x\",\"path\":\"/x\","
                + "\"required\":[],\"optional\":[]}").statusCode()).isEqualTo(400);
        assertThat(post("1.0.0", page("a", "/x", List.of(), Map.of()) + "," + page("b", "/x", List.of(), Map.of()))
                .statusCode()).isEqualTo(400);
        assertThat(post("1.0.0", page("a", "/x", List.of(), Map.of()) + "," + page("a", "/y", List.of(), Map.of()))
                .statusCode()).isEqualTo(400);
        assertThat(post("1.0.0", page("a", "/x/../y", List.of(), Map.of())).statusCode()).isEqualTo(400);
        assertThat(post("1.0.0", page("a", "x", List.of(), Map.of())).statusCode()).isEqualTo(400);
        assertThat(post("1.0.0", page("a", "/x", List.of("Not A Key"), Map.of())).statusCode()).isEqualTo(400);
        assertThat(livePages()).isEmpty();
    }

    @Test
    @DisplayName("Only a service account linked to an application may register")
    void onlyServiceAccounts() {
        String body = "{\"version\":\"1.0.0\",\"pages\":[" + page("a", "/a", List.of(), Map.of()) + "]}";
        assertThat(api.postJson(REGISTER, admin, body).statusCode()).isEqualTo(403);
        assertThat(api.postJson(REGISTER, null, body).statusCode()).isEqualTo(401);

        String unlinked = api.serviceToken(api.registerM2MClient("mf-unlinked-" + UUID.randomUUID().toString()
                .substring(0, 8), null));
        assertThat(api.postJson(REGISTER, unlinked, body).statusCode()).isEqualTo(409);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> entry(List<Object> pages, int index) {
        return (Map<String, Object>) pages.get(index);
    }

    private Map<String, Object> register(String version, String pages) {
        HttpResponse<String> response = post(version, pages);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return CompatSupport.jsonObject(response.body());
    }

    private HttpResponse<String> post(String version, String pages) {
        return api.postJson(REGISTER, serviceToken,
                "{\"version\":" + CompatSupport.q(version) + ",\"pages\":[" + pages + "]}");
    }

    private Map<String, String> livePages() {
        Map<String, String> pages = new java.util.TreeMap<>();
        jdbc.query("""
                SELECT p.page_key, p.path FROM t_application_page p JOIN t_application a ON a.id = p.application_id
                 WHERE a.code = ? AND p.status = 'ACTIVE'""",
                rs -> {
                    pages.put(rs.getString(1), rs.getString(2));
                }, application);
        return pages;
    }

    private String page(String name, String path, List<String> required, Map<String, String> optional) {
        StringBuilder opt = new StringBuilder();
        optional.forEach((k, label) -> opt.append(opt.isEmpty() ? "" : ",")
                .append("{\"key\":").append(CompatSupport.q(k)).append(",\"label\":").append(CompatSupport.q(label))
                .append("}"));
        return "{\"application\":" + CompatSupport.q(application) + ",\"page\":" + CompatSupport.q(key(name))
                + ",\"path\":" + CompatSupport.q(path) + ",\"required\":["
                + String.join(",", required.stream().map(CompatSupport::q).toList()) + "],\"optional\":[" + opt + "]}";
    }

    private String key(String name) {
        return "app." + application + "." + name;
    }
}
