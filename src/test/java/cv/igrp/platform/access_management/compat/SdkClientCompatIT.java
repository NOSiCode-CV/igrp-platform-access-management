package cv.igrp.platform.access_management.compat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spring Boot 4 migration gate — backward compatibility with the SDKs already
 * deployed. Each PUBLISHED release of {@code cv.igrp.platform.access:client}
 * (copied into {@code target/sdk-compat} by the build) is loaded in its own
 * class loader, with its own Jackson 2, and driven against this running service
 * exactly as a consumer drives it:
 *
 * <ul>
 *   <li>client-credentials token from {@code /oauth2/token} (the SDK's own token provider);</li>
 *   <li>{@code syncResources} then {@code syncPermissions}, the two calls
 *       {@code core-spring-boot}'s {@code AuthorizationSyncRunner} makes at startup;</li>
 *   <li>{@code getUsers}, {@code getDepartments}, {@code getRoles}, the M2M
 *       lookups notification and process services use;</li>
 *   <li>{@code checkAuthorization} / {@code batchCheckAuthorization} with a user token.</li>
 * </ul>
 *
 * <p>Nothing here links against the SDK at compile time: an old release must
 * keep working against a new server without being rebuilt. Versions covered:
 * 0.2.0-beta.6 (process-management), 0.2.0-beta.10 (pulled by core-spring-boot
 * 0.2.0-beta.5, process-studio), 0.2.0-beta.12 (core-spring-boot 0.2.0-beta.8,
 * notification) and 0.2.0-beta.15 (latest).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SdkClientCompatIT extends AbstractCompatIT {

    private static final Path SDK_DIR = Paths.get("target", "sdk-compat");
    private static final String PKG = "cv.igrp.platform.access.client.";

    @Autowired
    ApplicationContext context;

    CompatSupport api;

    @BeforeAll
    void setUp() {
        api = new CompatSupport(context);
    }

    static Stream<String> sdkVersions() throws IOException {
        try (Stream<Path> dirs = Files.list(SDK_DIR)) {
            List<String> versions = dirs.map(p -> p.getFileName().toString())
                    .filter(name -> name.startsWith("client-"))
                    .sorted()
                    .toList();
            assertFalse(versions.isEmpty(), "no SDK releases in " + SDK_DIR.toAbsolutePath()
                    + "; they are copied by maven-dependency-plugin (sdk-compat-clients)");
            return versions.stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sdkVersions")
    @DisplayName("A deployed SDK release works against this server: token, sync, lookups, authorization checks")
    void deployedSdkWorks(String sdk) throws Exception {
        // As in production, the client id is the application name: the server
        // files synced permissions under the resource named after the calling client.
        String service = "compat-" + sdk.replace('.', '-');
        CompatSupport.M2MClient m2m = api.registerM2MClient(service);

        try (URLClassLoader loader = isolatedLoader(sdk)) {
            Object apiClient = newInstance(loader, "ApiClient");
            invoke(apiClient, "setBaseUrl", CompatEnvironment.ISSUER);
            invoke(apiClient, "setClientCredentials", m2m.clientId(), m2m.clientSecret(), m2m.scope());

            Object m2mApi = loader.loadClass(PKG + "api.M2MApi")
                    .getConstructor(loader.loadClass(PKG + "IApiClient"))
                    .newInstance(apiClient);

            // AuthorizationSyncRunner: resources first, then permissions.
            Object resource = newInstance(loader, "model.ResourceDTO");
            invoke(resource, "setName", service);
            invoke(resource, "setType", "API");
            invoke(resource, "setDescription", "Resource for application: " + service);
            invoke(m2mApi, "syncResources", resource, service);

            Object permission = newInstance(loader, "model.PermissionDTO");
            invoke(permission, "setName", "compat.sdk.permission");
            invoke(permission, "setDescription", "Synced by " + sdk);
            invoke(permission, "setStatus", enumConstant(loader, "constants.Status", "ACTIVE"));
            invoke(m2mApi, "syncPermissions", new ArrayList<>(List.of(permission)), service);

            // M2M lookups, with the response DTOs parsed by the SDK's own Jackson 2.
            // Lookups take explicit codes, as consumers pass them.
            api.ensureBusinessData();
            String departmentCode = CompatSupport.BUSINESS_DEPARTMENT;
            List<?> departments = (List<?>) invoke(m2mApi, "getDepartments",
                    true, null, false, new ArrayList<>(List.of(departmentCode)), service);
            assertEquals(1, departments.size(), sdk + ": the default department is found by code");
            assertEquals(departmentCode, invoke(departments.getFirst(), "getCode"));

            String roleCode = CompatSupport.BUSINESS_ROLE;
            List<?> roles = (List<?>) invoke(m2mApi, "getRoles",
                    true, null, false, new ArrayList<>(List.of(roleCode)), service);
            assertEquals(1, roles.size(), sdk + ": the default role is found by code");
            assertEquals(roleCode, invoke(roles.getFirst(), "getCode"));

            // The server requires at least one filter on user lookups.
            List<?> users = (List<?>) invoke(m2mApi, "getUsers",
                    true, null, null, roleCode, null, false, false, new ArrayList<String>(), service);
            assertFalse(users.isEmpty(), sdk + ": the superadmin holds the business role");
            assertNotNull(invoke(users.getFirst(), "getId"));

            // Authorization checks, as a frontend backend-for-frontend does with a user token.
            Object userClient = newInstance(loader, "ApiClient");
            invoke(userClient, "setBaseUrl", CompatEnvironment.ISSUER);
            invoke(userClient, "setAuthToken", api.superadminToken());
            Object authorizeApi = loader.loadClass(PKG + "api.AuthorizeApi")
                    .getConstructor(loader.loadClass(PKG + "IApiClient"))
                    .newInstance(userClient);

            Object check = loader.loadClass(PKG + "model.PermissionCheckRequestDTO")
                    .getConstructor(String.class, String.class)
                    .newInstance("igrp-access-management", "igrp.users.list");
            Object decision = invoke(authorizeApi, "checkAuthorization", check);
            assertEquals(Boolean.TRUE, invoke(decision, "isAllowed"), sdk + ": superadmin is allowed");

            List<?> decisions = (List<?>) invoke(authorizeApi, "batchCheckAuthorization",
                    new ArrayList<>(List.of(check, check)));
            assertEquals(2, decisions.size());
        }
    }

    // ---- reflection over an isolated SDK ----

    private static URLClassLoader isolatedLoader(String sdk) throws IOException {
        List<URL> urls = new ArrayList<>();
        for (Path dir : List.of(SDK_DIR.resolve(sdk), SDK_DIR.resolve("lib"))) {
            try (Stream<Path> jars = Files.list(dir)) {
                for (Path jar : jars.filter(p -> p.toString().endsWith(".jar")).toList()) {
                    urls.add(toUrl(jar));
                }
            }
        }
        // Parent is the platform loader: nothing from this application, its
        // Jackson or its Spring leaks into the SDK under test.
        return new URLClassLoader(sdk, urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
    }

    private static URL toUrl(Path jar) {
        try {
            return jar.toAbsolutePath().toUri().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object newInstance(ClassLoader loader, String simpleName) throws ReflectiveOperationException {
        return loader.loadClass(PKG + simpleName).getConstructor().newInstance();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumConstant(ClassLoader loader, String simpleName, String constant) throws ClassNotFoundException {
        return Enum.valueOf((Class) loader.loadClass(PKG + simpleName), constant);
    }

    /** Invokes the single public method of that name and arity; unwraps SDK exceptions. */
    private static Object invoke(Object target, String name, Object... args) throws Exception {
        Method match = null;
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                if (match != null) {
                    throw new IllegalStateException("ambiguous SDK method " + name + "/" + args.length);
                }
                match = method;
            }
        }
        if (match == null) {
            throw new NoSuchMethodException(target.getClass().getName() + "#" + name + "/" + args.length);
        }
        try {
            return match.invoke(target, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                throw new AssertionError(target.getClass().getName() + "#" + name + " failed: " + describe(cause), cause);
            }
            throw e;
        }
    }

    private static String describe(Throwable t) {
        StringBuilder out = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            out.append(c.getClass().getSimpleName()).append(": ").append(c.getMessage()).append(" | ");
            if (c.getCause() == c) {
                break;
            }
        }
        return out.toString();
    }
}
