package com.gaskony.python3.gateway;

import com.inductiveautomation.ignition.gateway.dataroutes.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the actual Ignition roles attached to an authenticated request.
 *
 * <p>Used by {@code ExecutionHandlers#handleCreateSession} (C14 fix) to bind the
 * security mode of an issued session token to the caller's <em>real</em> Ignition
 * role membership rather than to a self-asserted {@code client_id} field.
 *
 * <p>Implementation strategy:
 * <ol>
 *   <li>Primary path uses the Ignition 8.3 {@code WebUiSession} SDK API
 *       ({@code com.inductiveautomation.ignition.gateway.web.session.WebUiSession})
 *       via reflection so that:
 *       <ul>
 *         <li>this class still compiles when the SDK adds/changes the API surface,
 *         <li>unit tests can run against a stub without dragging in the full
 *             Gateway-API jar.
 *       </ul>
 *   <li>Returns {@link Optional#empty()} when the request is unauthenticated,
 *       when the SDK class is unavailable, or when no roles can be derived.
 * </ol>
 *
 * <p>The class is package-private and stateless. A default instance is exposed
 * via {@link #getDefault()}; tests substitute their own instance via the
 * {@link ExecutionHandlers#setRoleResolverForTesting(RoleResolver)} hook.
 *
 * @since v3.13.0 (C14 — bind /auth/session token issuance to actual role)
 */
class RoleResolver {

    private static final Logger logger = LoggerFactory.getLogger(RoleResolver.class);

    private static final RoleResolver DEFAULT = new RoleResolver();

    /** Canonical Ignition role name for full administrative access. */
    static final String ADMIN_ROLE = "Administrator";

    /** Canonical Ignition role name commonly granted to Designer-class users. */
    static final String DESIGNER_ROLE = "Designer";

    static RoleResolver getDefault() {
        return DEFAULT;
    }

    /**
     * Resolve the role names attached to the authenticated user behind {@code req}.
     *
     * @param req the request context (may be {@code null})
     * @return immutable, lower-case-normalised set of role names; never {@code null}.
     *         Empty when the request is unauthenticated or roles cannot be resolved.
     */
    Set<String> getRoles(RequestContext req) {
        if (req == null) {
            return Collections.emptySet();
        }

        // Attempt the WebUiSession reflection chain. Any failure → empty set.
        try {
            Class<?> webUiSessionCls;
            try {
                webUiSessionCls = Class.forName(
                    "com.inductiveautomation.ignition.gateway.web.session.WebUiSession");
            } catch (ClassNotFoundException missing) {
                logger.debug("WebUiSession SDK class not on classpath (likely a unit-test environment); "
                    + "no roles available via reflection");
                return Collections.emptySet();
            }

            Method findMethod = webUiSessionCls.getMethod("find", RequestContext.class);
            Object sessionOpt = findMethod.invoke(null, req);
            if (!(sessionOpt instanceof Optional<?>)) {
                return Collections.emptySet();
            }
            Optional<?> session = (Optional<?>) sessionOpt;
            if (session.isEmpty()) {
                return Collections.emptySet();
            }

            Object sessionObj = session.get();
            Object userCtx = sessionObj.getClass().getMethod("getUserContext").invoke(sessionObj);
            if (userCtx == null) {
                return Collections.emptySet();
            }

            Object webAuthUserOpt = userCtx.getClass().getMethod("getWebAuthUser").invoke(userCtx);
            if (!(webAuthUserOpt instanceof Optional<?>)) {
                return Collections.emptySet();
            }
            Optional<?> webAuthUser = (Optional<?>) webAuthUserOpt;
            if (webAuthUser.isEmpty()) {
                return Collections.emptySet();
            }

            Object user = webAuthUser.get();
            Object rolesObj = user.getClass().getMethod("getRoles").invoke(user);
            if (!(rolesObj instanceof Collection<?>)) {
                return Collections.emptySet();
            }

            Set<String> normalised = new LinkedHashSet<>();
            for (Object r : (Collection<?>) rolesObj) {
                if (r != null) {
                    normalised.add(r.toString());
                }
            }
            return Collections.unmodifiableSet(normalised);
        } catch (Exception e) {
            logger.debug("Failed to resolve roles via WebUiSession: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * @return {@code true} iff the caller has the Ignition {@value #ADMIN_ROLE} role.
     */
    boolean isAdministrator(RequestContext req) {
        Set<String> roles = getRoles(req);
        for (String r : roles) {
            if (ADMIN_ROLE.equalsIgnoreCase(r)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return {@code true} iff the caller has the Ignition {@value #DESIGNER_ROLE} role
     *         <em>or</em> the {@value #ADMIN_ROLE} role.
     */
    boolean isDesignerOrAdministrator(RequestContext req) {
        Set<String> roles = getRoles(req);
        for (String r : roles) {
            if (ADMIN_ROLE.equalsIgnoreCase(r) || DESIGNER_ROLE.equalsIgnoreCase(r)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Require the caller of {@code req} to have the Ignition {@value #ADMIN_ROLE}
     * role. Used by REST routes that mint or guard privileged operations.
     *
     * <p>Throws a {@link SecurityException} (rather than returning a boolean) so
     * the caller can let it propagate and the route's standard error wrapper
     * surfaces a 403 with a clear message. Security exceptions intentionally do
     * not include a stack trace in the response (handled by the wrapper).</p>
     *
     * @param req the request context (must not be {@code null})
     * @throws SecurityException if the caller lacks the Administrator role
     * @since v3.13.0 (C13 — Administrator role gate for Python execution)
     */
    void requireAdministrator(RequestContext req) {
        if (!isAdministrator(req)) {
            throw new SecurityException(
                "Administrator role required to execute Python");
        }
    }

    /**
     * Scripting-side access check, used by {@link Python3ScriptModule} to gate
     * {@code system.python3.exec} (and friends) at the Jython entry-point.
     *
     * <p><b>Trust model (charter &sect;2, decided 2026-07-02):</b> "authoring is
     * the boundary." Anyone with Designer access can already run arbitrary
     * Jython (and thus arbitrary code) on the Gateway, so a project Jython
     * script calling {@code system.python3.exec} grants no privilege it did
     * not already have. Python 3 scripting is therefore <b>allowed by
     * default</b>, matching Jython's own trust level — this reverses the
     * C13 opt-in default.</p>
     *
     * <p>A Gateway administrator who wants to disable {@code system.python3.*}
     * fleet-wide (e.g. to shrink the supply-chain surface, not because Jython
     * itself is any safer) can opt OUT by setting the system property
     * {@code ignition.python3.scriptingFunctions.allowed=false} (or the
     * equivalent {@code IGNITION_PYTHON3_SCRIPTING_ALLOWED} environment
     * variable). Setting either to {@code true} is equivalent to leaving it
     * unset.</p>
     *
     * <p>Subclasses (notably the test stub) may override
     * {@link #isScriptingAllowed()} to bypass the property/env-var check.</p>
     *
     * @throws SecurityException if scripting access has been disabled by the administrator
     * @since v4.3.0 (charter &sect;2, 2026-07-02 — flips the C13 opt-in default to opt-out)
     */
    void requireScriptingAllowed() {
        if (!isScriptingAllowed()) {
            throw new SecurityException(
                "Python 3 scripting functions (system.python3.*) have been "
                + "disabled by the gateway administrator "
                + "(ignition.python3.scriptingFunctions.allowed=false).");
        }
    }

    /**
     * Hook for {@link #requireScriptingAllowed()}. Default impl is
     * allow-by-default (opt-out): the system property, then the env var, is
     * consulted to decide; if neither is set, scripting is allowed. Tests
     * substitute their own resolver via
     * {@link Python3ScriptModule#setRoleResolverForTesting(RoleResolver)}.
     *
     * @since v4.3.0 (charter &sect;2, 2026-07-02)
     */
    boolean isScriptingAllowed() {
        String prop = System.getProperty("ignition.python3.scriptingFunctions.allowed");
        if (prop != null) {
            return Boolean.parseBoolean(prop.trim());
        }
        String env = System.getenv("IGNITION_PYTHON3_SCRIPTING_ALLOWED");
        if (env != null) {
            return Boolean.parseBoolean(env.trim());
        }
        return true;
    }
}
