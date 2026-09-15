package com.gaskony.python3;

import com.inductiveautomation.ignition.common.rpc.RpcInterface;

/**
 * Module RPC interface for authenticated Designer &rarr; Gateway communication.
 *
 * <p>Introduced in v4.2.0 to replace the Designer's cold-HTTP REST client, which
 * could no longer authenticate to the Gateway after the C13/C14 security
 * hardening removed the {@code X-Source} header bypass and the self-asserted
 * {@code client_id} session-token grant. A stand-alone {@code java.net.http}
 * client carries none of the Designer's authenticated Gateway session, so every
 * REST call was rejected (401) and the Project Browser showed
 * "(Gateway unavailable)".</p>
 *
 * <p>Module RPC calls instead travel over the Designer's existing authenticated
 * Gateway channel. The Gateway can therefore resolve the real caller
 * ({@link com.inductiveautomation.ignition.gateway.rpc.RpcDelegate#session()})
 * and no session-token dance is required.</p>
 *
 * <p>Every method returns the <em>same JSON string</em> the equivalent REST
 * endpoint returns, so the Designer-side JSON parsing is unchanged. Method names
 * map to {@code RpcCall.function()} and {@link #packageId} to
 * {@code RpcCall.packageId()}; the module id is supplied by the caller.</p>
 *
 * @since v4.2.0
 */
@RpcInterface(packageId = "python3")
public interface Python3Rpc {

    /** @return JSON {@code {"success":true,"scripts":[...]}} — see REST GET /scripts/list. */
    String listScripts() throws Exception;

    /** @return JSON {@code {"success":true,"script":{...}}} — see REST GET /scripts/load/:name. */
    String loadScript(String name) throws Exception;

    /** @return JSON {@code {"success":true}} — see REST POST /scripts/save. */
    String saveScript(String name, String code, String description,
                      String author, String folderPath, String version) throws Exception;

    /** @return JSON {@code {"success":...,"message":"..."}} — see REST POST /scripts/delete/:name. */
    String deleteScript(String name) throws Exception;

    /**
     * Execute Python statements.
     *
     * @param variablesJson JSON object of variables (may be {@code null}/blank)
     * @param pythonVersion target Python version (may be {@code null}/blank for default)
     * @return JSON {@code {"success":...,"result":...,"error":...}} — see REST POST /exec.
     */
    String exec(String code, String variablesJson, String pythonVersion) throws Exception;

    /**
     * Evaluate a Python expression.
     *
     * @param variablesJson JSON object of variables (may be {@code null}/blank)
     * @param pythonVersion target Python version (may be {@code null}/blank for default)
     * @return JSON {@code {"success":...,"result":...,"error":...}} — see REST POST /eval.
     */
    String eval(String expression, String variablesJson, String pythonVersion) throws Exception;

    /** @return JSON with {@code version}/{@code pythonVersion} fields — see REST GET /version. */
    String getVersion() throws Exception;

    /** @return JSON pool statistics — see REST GET /pool-stats. */
    String getPoolStats() throws Exception;

    /** @return JSON {@code {"healthy":...,"available":...}} — see REST GET /health. */
    String health() throws Exception;

    // -------------------------------------------------------------------------
    // Diagnostics, environment visibility (v4.3.0 — charter workflows 4/5)
    // -------------------------------------------------------------------------

    /**
     * @return JSON {@code {"available":...,"poolStats":{...},"versionInfo":{...},
     *         "distributionInfo":{...},"timestamp":...}} — see REST GET /diagnostics.
     */
    String getDiagnostics() throws Exception;

    /**
     * Fetch recent Gateway log entries filtered to this module (hardcoded
     * {@code filter=Python3}, matching the Designer's prior REST call).
     *
     * @param maxLines maximum number of log lines to return (clamped 1-500)
     * @return JSON {@code {"success":...,"entries":[...],"count":...,"total":...}} — see
     *         REST GET /logs?lines=N&amp;filter=Python3.
     */
    String getModuleLogs(int maxLines) throws Exception;

    /** @return JSON gateway impact assessment — see REST GET /gateway-impact. */
    String getGatewayImpact() throws Exception;

    /**
     * @return JSON {@code {"success":...,"versions":[...],"default":"...","details":{...}}}
     *         — see REST GET /versions.
     */
    String getVersions() throws Exception;

    /** @return JSON {@code {"success":...,"distributions":[...],"os":"...","installedCount":...}} — see REST GET /distributions. */
    String getDistributions() throws Exception;

    /**
     * Read-only package environment view (includes installed flags), per charter
     * &sect;3 ("Environment visibility ... Designer read-only").
     *
     * @return JSON {@code {"success":...,"packages":{...},"count":...}} — see REST GET /packages/catalog.
     */
    String getPackageCatalog() throws Exception;

    /**
     * @param code Python source to check
     * @return JSON {@code {"success":true,"errors":[{line,column,message,severity},...]}}
     *         — see REST POST /check-syntax.
     */
    String checkSyntax(String code) throws Exception;

    /**
     * @param code   Python source
     * @param line   cursor line (1-based)
     * @param column cursor column (0-based)
     * @return JSON {@code {"success":true,"completions":[{text,type,description,signature},...]}}
     *         — see REST POST /completions.
     */
    String getCompletions(String code, int line, int column) throws Exception;
}
