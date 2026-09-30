package com.gaskony.python3.gateway;

import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.gson.JsonParser;
import com.inductiveautomation.ignition.gateway.dataroutes.RequestContext;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles REST API endpoints for script CRUD operations and package management.
 *
 * Covers: scripts/save, scripts/load, scripts/list, scripts/delete, scripts/available,
 * packages/catalog, packages/status, packages/install, packages/uninstall, packages/verify,
 * packages/search-pypi, packages/pypi-info.
 *
 * Created in v3.6.15 as part of Phase 2 refactoring of Python3RestEndpoints.
 */
class ScriptAndPackageHandlers {

    private static final Logger logger = LoggerFactory.getLogger(ScriptAndPackageHandlers.class);

    private final EndpointContext ctx;

    ScriptAndPackageHandlers(EndpointContext ctx) {
        this.ctx = ctx;
    }

    // -------------------------------------------------------------------------
    // Script Management
    // -------------------------------------------------------------------------

    /**
     * Handle POST /scripts/save - Save a Python script.
     *
     * Request body: {"name": "...", "code": "...", "description": "...", "author": "...", "folderPath": "...", "version": "..."}
     * Response: {"success": true/false, "script": {...}}
     */
    JsonObject handleSaveScript(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("scripts/save", res, () -> {
            Python3RestEndpoints.validateCSRFIfSession(req);

            if (ctx.scriptRepository == null) {
                return ApiResponse.error("Script repository not initialized");
            }

            JsonObject requestBody = Python3RestEndpoints.parseJsonBody(req);
            String name = requestBody.has("name") ? requestBody.get("name").getAsString() : null;
            String code = requestBody.has("code") ? requestBody.get("code").getAsString() : "";
            String description = requestBody.has("description") ? requestBody.get("description").getAsString() : null;
            String author = requestBody.has("author") ? requestBody.get("author").getAsString() : "Unknown";
            String folderPath = requestBody.has("folderPath") ? requestBody.get("folderPath").getAsString() : "";
            String version = requestBody.has("version") ? requestBody.get("version").getAsString() : "1.0";

            if (name == null || name.trim().isEmpty()) {
                return ApiResponse.error("Script name is required");
            }

            Python3RestEndpoints.validateScriptName(name);
            Python3RestEndpoints.validateCode(code);
            Python3RestEndpoints.validateFolderPath(folderPath);

            Python3RestEndpoints.auditLog("SCRIPT_SAVE", "name=" + name + ", folder=" + folderPath);

            Python3ScriptRepository.SavedScript script = ctx.scriptRepository.saveScript(
                name, code, description, author, folderPath, version
            );

            JsonObject response = new JsonObject();
            response.addProperty("success", true);

            JsonObject scriptJson = new JsonObject();
            scriptJson.addProperty("id", script.getId());
            scriptJson.addProperty("name", script.getName());
            scriptJson.addProperty("description", script.getDescription());
            scriptJson.addProperty("author", script.getAuthor());
            scriptJson.addProperty("createdDate", script.getCreatedDate());
            scriptJson.addProperty("lastModified", script.getLastModified());
            scriptJson.addProperty("folderPath", script.getFolderPath());
            scriptJson.addProperty("version", script.getVersion());
            response.add("script", scriptJson);

            logger.info("REST API: Script saved: {} in folder: {}", name, folderPath);
            return response;
        });
    }

    /**
     * Handle GET /scripts/load/:name - Load a saved script.
     *
     * Response: {"success": true/false, "script": {...}}
     */
    JsonObject handleLoadScript(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("scripts/load", res, () -> {
            if (ctx.scriptRepository == null) {
                return ApiResponse.error("Script repository not initialized");
            }

            String requestPath = req.getRequest().getRequestURI();
            String name = URLDecoder.decode(
                    requestPath.substring(requestPath.lastIndexOf('/') + 1),
                    StandardCharsets.UTF_8);

            if (name == null || name.trim().isEmpty()) {
                return ApiResponse.error("Script name is required");
            }

            Python3ScriptRepository.SavedScript script = ctx.scriptRepository.loadScript(name);

            if (script == null) {
                return ApiResponse.error("Script not found: " + name);
            }

            JsonObject response = new JsonObject();
            response.addProperty("success", true);

            JsonObject scriptJson = new JsonObject();
            scriptJson.addProperty("id", script.getId());
            scriptJson.addProperty("name", script.getName());
            scriptJson.addProperty("code", script.getCode());
            scriptJson.addProperty("description", script.getDescription());
            scriptJson.addProperty("author", script.getAuthor());
            scriptJson.addProperty("createdDate", script.getCreatedDate());
            scriptJson.addProperty("lastModified", script.getLastModified());
            scriptJson.addProperty("folderPath", script.getFolderPath());
            scriptJson.addProperty("version", script.getVersion());
            response.add("script", scriptJson);

            logger.debug("REST API: Script loaded: {}", name);
            return response;
        });
    }

    /**
     * Handle GET /scripts/list - List all saved scripts.
     *
     * Response: {"success": true, "scripts": [...]}
     */
    JsonObject handleListScripts(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("scripts/list", res, () -> {
            if (ctx.scriptRepository == null) {
                return ApiResponse.error("Script repository not initialized");
            }

            List<Python3ScriptRepository.ScriptMetadata> scripts = ctx.scriptRepository.listScripts();

            JsonObject response = new JsonObject();
            response.addProperty("success", true);

            JsonArray scriptsArray = new JsonArray();
            for (Python3ScriptRepository.ScriptMetadata script : scripts) {
                JsonObject scriptJson = new JsonObject();
                scriptJson.addProperty("id", script.getId());
                scriptJson.addProperty("name", script.getName());
                scriptJson.addProperty("description", script.getDescription());
                scriptJson.addProperty("author", script.getAuthor());
                scriptJson.addProperty("createdDate", script.getCreatedDate());
                scriptJson.addProperty("lastModified", script.getLastModified());
                scriptJson.addProperty("folderPath", script.getFolderPath());
                scriptJson.addProperty("version", script.getVersion());
                scriptsArray.add(scriptJson);
            }
            response.add("scripts", scriptsArray);

            logger.debug("REST API: Listed {} scripts", scripts.size());
            return response;
        });
    }

    /**
     * Handle POST /scripts/delete/:name - Delete a saved script.
     *
     * Response: {"success": true/false}
     */
    JsonObject handleDeleteScript(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("scripts/delete", res, () -> {
            Python3RestEndpoints.validateCSRFIfSession(req);

            if (ctx.scriptRepository == null) {
                return ApiResponse.error("Script repository not initialized");
            }

            String requestPath = req.getRequest().getRequestURI();
            String name = URLDecoder.decode(
                    requestPath.substring(requestPath.lastIndexOf('/') + 1),
                    StandardCharsets.UTF_8);

            if (name == null || name.trim().isEmpty()) {
                return ApiResponse.error("Script name is required");
            }

            Python3RestEndpoints.auditLog("SCRIPT_DELETE", "name=" + name);

            boolean deleted = ctx.scriptRepository.deleteScript(name);

            JsonObject response = new JsonObject();
            response.addProperty("success", deleted);

            if (!deleted) {
                response.addProperty("message", "Script not found: " + name);
            } else {
                response.addProperty("message", "Script deleted successfully");
            }

            logger.info("REST API: Script deletion: {} - {}", name, deleted ? "success" : "not found");
            return response;
        });
    }

    /**
     * Handle GET /scripts/available - Get list of available scripts with metadata.
     *
     * Response: {"success": true, "scripts": [{name, description, path, author, version}, ...]}
     *
     * v2.0.24: New endpoint for script autocomplete and discovery
     */
    JsonObject handleGetAvailableScripts(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("scripts/available", res, () -> {
            if (ctx.scriptModule == null) {
                return ApiResponse.error("Script module not initialized");
            }

            List<Map<String, Object>> scripts = ctx.scriptModule.getAvailableScripts();

            JsonObject response = new JsonObject();
            response.addProperty("success", true);

            JsonArray scriptsArray = new JsonArray();
            for (Map<String, Object> script : scripts) {
                scriptsArray.add(Python3RestEndpoints.mapToJson(script));
            }
            response.add("scripts", scriptsArray);
            response.addProperty("count", scriptsArray.size());

            logger.debug("REST API: /scripts/available found {} scripts", scriptsArray.size());
            return response;
        });
    }

    // -------------------------------------------------------------------------
    // Package Management
    // -------------------------------------------------------------------------

    /**
     * Handle GET /packages/catalog - Get available package bundles.
     *
     * Response: {"success": true, "packages": {"jedi": {...}, "web": {...}, "datascience": {...}}}
     *
     * v2.3.0: New endpoint for package management
     */
    JsonObject handleGetPackageCatalog(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("packages/catalog", res, () -> {
            if (ctx.packageManager == null) {
                return ApiResponse.error("Package manager not initialized");
            }

            Map<String, Python3PackageManager.PackageInfo> catalog = ctx.packageManager.getPackageCatalog();

            JsonObject response = new JsonObject();
            response.addProperty("success", true);

            Set<String> installedPackages = ctx.packageManager.getInstalledPackages();

            JsonObject packagesJson = new JsonObject();
            for (Map.Entry<String, Python3PackageManager.PackageInfo> entry : catalog.entrySet()) {
                String packageName = entry.getKey();
                Python3PackageManager.PackageInfo info = entry.getValue();

                JsonObject packageJson = new JsonObject();
                packageJson.addProperty("version", info.version);
                packageJson.addProperty("description", info.description);
                packageJson.addProperty("sizeMb", info.sizeMb);
                packageJson.addProperty("importName", info.importName);
                packageJson.addProperty("installed", installedPackages.contains(packageName));

                JsonArray wheelsArray = new JsonArray();
                for (String wheel : info.wheels) {
                    wheelsArray.add(wheel);
                }
                packageJson.add("wheels", wheelsArray);

                JsonArray pipPackagesArray = new JsonArray();
                for (String pipPkg : info.pipPackages) {
                    pipPackagesArray.add(pipPkg);
                }
                packageJson.add("pipPackages", pipPackagesArray);

                JsonArray requiredForArray = new JsonArray();
                for (String usage : info.requiredFor) {
                    requiredForArray.add(usage);
                }
                packageJson.add("requiredFor", requiredForArray);

                packagesJson.add(packageName, packageJson);
            }

            // Include packages installed from PyPI that aren't in the catalog
            for (String installedPkg : installedPackages) {
                if (!catalog.containsKey(installedPkg)) {
                    JsonObject packageJson = new JsonObject();
                    packageJson.addProperty("version", "");
                    packageJson.addProperty("description", "Installed from PyPI");
                    packageJson.addProperty("sizeMb", 0.0);
                    packageJson.addProperty("importName", installedPkg);
                    packageJson.addProperty("installed", true);
                    packageJson.add("wheels", new JsonArray());
                    packageJson.add("pipPackages", new JsonArray());
                    packageJson.add("requiredFor", new JsonArray());
                    packagesJson.add(installedPkg, packageJson);
                }
            }

            response.add("packages", packagesJson);
            response.addProperty("count", packagesJson.size());

            logger.debug("REST API: /packages/catalog found {} packages", catalog.size());
            return response;
        });
    }

    /**
     * Handle GET /packages/status - Get package installation status.
     *
     * Response: {"success": true, "status": {...}, "installed": ["jedi", ...], "available": ["jedi", "web", ...]}
     *
     * v2.3.0: New endpoint for package management
     */
    JsonObject handleGetPackageStatus(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("packages/status", res, () -> {
            if (ctx.packageManager == null) {
                return ApiResponse.error("Package manager not initialized");
            }

            Map<String, Object> status = ctx.packageManager.getStatus();
            Set<String> installed = ctx.packageManager.getInstalledPackages();
            Map<String, Python3PackageManager.PackageInfo> catalog = ctx.packageManager.getPackageCatalog();

            JsonObject response = new JsonObject();
            response.addProperty("success", true);

            response.add("status", Python3RestEndpoints.mapToJson(status));

            JsonArray installedArray = new JsonArray();
            for (String packageName : installed) {
                installedArray.add(packageName);
            }
            response.add("installed", installedArray);

            JsonArray availableArray = new JsonArray();
            for (String packageName : catalog.keySet()) {
                availableArray.add(packageName);
            }
            response.add("available", availableArray);

            return response;
        });
    }

    /**
     * Handle POST /packages/install/:name - Install a package.
     *
     * Tries catalog (bundled wheels) first, then falls back to PyPI direct install.
     *
     * Response: {"success": true/false, "message": "...", "installedWheels": [...]}
     *
     * v2.3.0: New endpoint for package management
     * v3.6.1: Falls back to PyPI if not in catalog
     */
    JsonObject handleInstallPackage(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("packages/install", res, () -> {
            // Installing a package runs the package's own build hooks as the
            // Gateway's OS user, so this is code execution and must be gated
            // exactly like /exec — not by the weaker checkManagePermission the
            // route carries, which only asks "is anyone logged in?".
            Python3RestEndpoints.determineSecurityMode(req);
            Python3RestEndpoints.validateCSRFIfSession(req);

            if (ctx.packageManager == null) {
                return ApiResponse.error("Package manager not initialized");
            }

            String requestPath = req.getRequest().getRequestURI();
            String packageName = URLDecoder.decode(
                    requestPath.substring(requestPath.lastIndexOf('/') + 1),
                    StandardCharsets.UTF_8);

            if (packageName == null || packageName.trim().isEmpty()) {
                return ApiResponse.error("Package name is required");
            }

            Python3RestEndpoints.auditLog("PACKAGE_INSTALL", "package=" + packageName);

            Python3PackageManager.InstallResult result = ctx.packageManager.installPackage(packageName);

            if (!result.success && result.message != null && result.message.contains("not found in catalog")) {
                logger.info("Package {} not in catalog, installing from PyPI", packageName);
                result = ctx.packageManager.pipInstallFromPyPI(packageName);
            }

            JsonObject response = new JsonObject();
            response.addProperty("success", result.success);
            response.addProperty("message", result.message);

            JsonArray wheelsArray = new JsonArray();
            for (String wheel : result.installedWheels) {
                wheelsArray.add(wheel);
            }
            response.add("installedWheels", wheelsArray);

            logger.info("REST API: Package installation: {} - {}", packageName, result.success ? "success" : "failed");
            return response;
        });
    }

    /**
     * Handle POST /packages/uninstall/:name - Uninstall a package bundle.
     *
     * Response: {"success": true/false, "message": "..."}
     *
     * v2.3.0: New endpoint for package management
     */
    JsonObject handleUninstallPackage(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("packages/uninstall", res, () -> {
            // Same gate as install: removing a package changes what executed
            // code will import, so it is not a read-only operation.
            Python3RestEndpoints.determineSecurityMode(req);
            Python3RestEndpoints.validateCSRFIfSession(req);

            if (ctx.packageManager == null) {
                return ApiResponse.error("Package manager not initialized");
            }

            String requestPath = req.getRequest().getRequestURI();
            String packageName = URLDecoder.decode(
                    requestPath.substring(requestPath.lastIndexOf('/') + 1),
                    StandardCharsets.UTF_8);

            if (packageName == null || packageName.trim().isEmpty()) {
                return ApiResponse.error("Package name is required");
            }

            Python3RestEndpoints.auditLog("PACKAGE_UNINSTALL", "package=" + packageName);

            boolean success = ctx.packageManager.uninstallPackage(packageName);

            JsonObject response = new JsonObject();
            response.addProperty("success", success);
            response.addProperty("message", success ? "Package uninstalled successfully" : "Failed to uninstall package");

            logger.info("REST API: Package uninstallation: {} - {}", packageName, success ? "success" : "failed");
            return response;
        });
    }

    /**
     * Handle POST /packages/verify - Verify installed packages.
     *
     * Response: {"success": true, "verification": {"jedi": true, "web": false, ...}}
     *
     * v2.3.0: New endpoint for package management
     */
    JsonObject handleVerifyPackages(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("packages/verify", res, () -> {
            if (ctx.packageManager == null) {
                return ApiResponse.error("Package manager not initialized");
            }

            Map<String, Boolean> verification = ctx.packageManager.verifyPackages();

            JsonObject response = new JsonObject();
            response.addProperty("success", true);

            JsonObject verificationJson = new JsonObject();
            for (Map.Entry<String, Boolean> entry : verification.entrySet()) {
                verificationJson.addProperty(entry.getKey(), entry.getValue());
            }
            response.add("verification", verificationJson);

            logger.debug("REST API: /packages/verify verified {} packages", verification.size());
            return response;
        });
    }

    /**
     * Handle GET /packages/search-pypi - Search PyPI for packages (v3.5.0).
     *
     * This is a special-case handler: it calls applySecurityHeaders directly because it
     * does not use withHandler (the happy path writes the response inline and error
     * recovery still needs security headers).
     *
     * Query parameter: q (search query)
     * Response: {"success": true, "query": "...", "count": N, "results": [{name, version, summary}, ...]}
     */
    JsonObject handleSearchPyPI(RequestContext req, HttpServletResponse res) {
        logger.debug("REST API: /packages/search-pypi called");
        Python3RestEndpoints.applySecurityHeaders(res);
        String query = req.getRequest().getParameter("q");
        logger.debug("REST API: /packages/search-pypi query={}", query);

        if (query == null || query.trim().isEmpty()) {
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("query", "");
            response.addProperty("count", 0);
            response.add("results", new JsonArray());
            return response;
        }

        query = query.trim();
        JsonArray results = new JsonArray();

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

        try {
            // Step 1: Try exact match via PyPI JSON API
            JsonObject exactMatch = fetchPyPIPackageInfo(client, query);
            if (exactMatch != null) {
                results.add(exactMatch);
            }

            // Step 2: HTML search fallback for broader results
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            URI uri = URI.create("https://pypi.org/search/?q=" + encoded + "&page=1");

            HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "text/html")
                .header("User-Agent", "IgnitionPython3Module/3.5.0")
                .GET()
                .build();

            HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
            String html = httpResponse.body();

            // v3.5.0: Updated regex patterns to match current PyPI HTML structure
            Pattern[] patterns = {
                // Current PyPI structure (2024+)
                Pattern.compile(
                    "<a[^>]*class=\"[^\"]*package-snippet[^\"]*\"[^>]*>.*?" +
                    "<span[^>]*class=\"[^\"]*package-snippet__name[^\"]*\"[^>]*>\\s*([^<]+?)\\s*</span>.*?" +
                    "<span[^>]*class=\"[^\"]*package-snippet__version[^\"]*\"[^>]*>\\s*([^<]+?)\\s*</span>.*?" +
                    "<p[^>]*class=\"[^\"]*package-snippet__description[^\"]*\"[^>]*>\\s*([^<]*?)\\s*</p>",
                    Pattern.DOTALL
                ),
                // Alternative: h3/span based pattern
                Pattern.compile(
                    "<h3[^>]*class=\"[^\"]*package-snippet__title[^\"]*\"[^>]*>.*?" +
                    "<span[^>]*class=\"[^\"]*package-snippet__name[^\"]*\"[^>]*>\\s*([^<]+?)\\s*</span>.*?" +
                    "<span[^>]*class=\"[^\"]*package-snippet__version[^\"]*\"[^>]*>\\s*([^<]+?)\\s*</span>.*?" +
                    "<p[^>]*class=\"[^\"]*package-snippet__description[^\"]*\"[^>]*>\\s*([^<]*?)\\s*</p>",
                    Pattern.DOTALL
                ),
            };

            java.util.Set<String> addedNames = new java.util.HashSet<>();
            if (exactMatch != null) {
                addedNames.add(exactMatch.get("name").getAsString().toLowerCase());
            }

            int maxResults = 30;
            for (Pattern pattern : patterns) {
                Matcher matcher = pattern.matcher(html);
                while (matcher.find() && results.size() < maxResults) {
                    String name = matcher.group(1).trim();
                    String version = matcher.group(2).trim();
                    String summary = matcher.group(3).trim();

                    if (!addedNames.contains(name.toLowerCase())) {
                        JsonObject pkg = new JsonObject();
                        pkg.addProperty("name", name);
                        pkg.addProperty("version", version);
                        pkg.addProperty("summary", summary);
                        results.add(pkg);
                        addedNames.add(name.toLowerCase());
                    }
                }
                if (results.size() > 1) break;
            }

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("query", query);
            response.addProperty("count", results.size());
            response.add("results", results);

            logger.debug("REST API: /packages/search-pypi found {} results for '{}'", results.size(), query);
            return response;

        } catch (Exception e) {
            logger.error("REST API: /packages/search-pypi failed for query '{}'", query, e);
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("query", query);
            response.addProperty("count", results.size());
            response.add("results", results);
            if (results.size() == 0) {
                response.addProperty("warning", "Search failed: " + e.getMessage());
            }
            return response;
        }
    }

    /**
     * Handle GET /packages/pypi-info/{name} - Get full PyPI package metadata (v3.5.0).
     *
     * Returns detailed package info including all available versions.
     */
    JsonObject handleGetPyPIInfo(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("packages/pypi-info", res, () -> {
            String path = req.getRequest().getRequestURI();
            String name = URLDecoder.decode(
                    path.substring(path.lastIndexOf('/') + 1),
                    StandardCharsets.UTF_8);
            logger.debug("REST API: /packages/pypi-info for '{}'", name);

            if (name.isEmpty()) {
                return ApiResponse.error("Package name is required");
            }

            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

            JsonObject pkg = fetchPyPIPackageInfo(client, name);
            if (pkg == null) {
                JsonObject response = new JsonObject();
                response.addProperty("success", false);
                response.addProperty("error", "Package '" + name + "' not found on PyPI");
                return response;
            }

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.add("package", pkg);
            return response;
        });
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Fetch package info from PyPI JSON API.
     * Returns a result object with name, version, summary, or null if not found.
     */
    private JsonObject fetchPyPIPackageInfo(HttpClient client, String packageName) {
        try {
            String encoded = URLEncoder.encode(packageName, StandardCharsets.UTF_8);
            URI uri = URI.create("https://pypi.org/pypi/" + encoded + "/json");

            HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("User-Agent", "IgnitionPython3Module/3.5.0")
                .GET()
                .build();

            HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (httpResponse.statusCode() != 200) {
                return null;
            }

            JsonObject pypiData = JsonParser.parseString(httpResponse.body()).getAsJsonObject();
            JsonObject info = pypiData.getAsJsonObject("info");
            if (info == null) return null;

            JsonObject pkg = new JsonObject();
            pkg.addProperty("name", info.has("name") ? info.get("name").getAsString() : packageName);
            pkg.addProperty("version", info.has("version") ? info.get("version").getAsString() : "");
            pkg.addProperty("summary", info.has("summary") && !info.get("summary").isJsonNull()
                ? info.get("summary").getAsString() : "");
            pkg.addProperty("author", info.has("author") && !info.get("author").isJsonNull()
                ? info.get("author").getAsString() : "");
            pkg.addProperty("license", info.has("license") && !info.get("license").isJsonNull()
                ? info.get("license").getAsString() : "");
            pkg.addProperty("homepage", info.has("home_page") && !info.get("home_page").isJsonNull()
                ? info.get("home_page").getAsString() : "");

            if (pypiData.has("releases")) {
                JsonArray versions = new JsonArray();
                for (String ver : pypiData.getAsJsonObject("releases").keySet()) {
                    versions.add(ver);
                }
                pkg.add("versions", versions);
            }

            return pkg;
        } catch (Exception e) {
            logger.debug("PyPI JSON API lookup failed for '{}': {}", packageName, e.getMessage());
            return null;
        }
    }
}
