package org.tkit.onecx.service.redirect.rs;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocFilename;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Redirect & Replace configuration
 */
@ConfigDocFilename("onecx-service-redirect.adoc")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "onecx.redirect")
public interface RedirectConfig {

    /**
     * Host forwarding rules.
     * The map key is a rule id. Each rule contains its own host-pattern and proxy target.
     */
    @WithName("host-forward-rules")
    Map<String, HostForwardRule> hostForwardRules();

    /**
     * Rule evaluation settings.
     */
    @WithName("rules")
    RuleConfig rules();

    /**
     * Url Redirect Rules.
     * The outer map key is a server-side regex matched against the incoming request URL.
     * The inner map key is an integer index (e.g. "0", "1", "2") defining the priority order
     * in which the client-side patterns are tried. Lower index = higher priority.
     */
    @WithName("url-rewrite-rules")
    Map<String, Map<String, ClientRule>> urlRewriteRules();

    /**
     * File path to custom redirect template
     */
    @WithName("custom-redirect-template-path")
    Optional<String> customRedirectTemplatePath();

    /**
     * Bundled redirect template name loaded from classpath templates.
     * Example: {@code redirectWaitTemplate.html}.
     *
     * @return optional bundled redirect template name
     */
    @WithName("bundled-redirect-template-name")
    Optional<String> bundledRedirectTemplateName();

    /**
     * File path to custom fallback template
     */
    @WithName("custom-fallback-template-path")
    Optional<String> customFallbackTemplatePath();

    /**
     * Host forwarding rule.
     */
    interface HostForwardRule {

        /**
         * Regex matched against the incoming request host.
         *
         * @return host matching regex
         */
        @WithName("host-pattern")
        String hostPattern();

        /**
         * Target proxy host (e.g. "new-host.example.com" or "new-host.example.com:8443").
         *
         * @return proxy host
         */
        @WithName("proxy-host")
        String proxyHost();

        /**
         * Optional target protocol (e.g. "https").
         *
         * @return optional proxy protocol
         */
        @WithName("proxy-protocol")
        Optional<String> proxyProtocol();
    }

    /**
     * Rules evaluation mode.
     */
    interface RuleConfig {

        /**
         * combined: host forwarding and path rewrite are applied in one flow.
         * separate: host forwarding and path rewrite are evaluated independently.
         *
         * @return configured rules mode
         */
        @WithName("mode")
        @WithDefault("combined")
        RuleMode mode();

        /**
         * Redirect wait time in seconds.
         *
         * @return redirect wait time in seconds
         */
        @WithName("redirect-wait-seconds")
        @WithDefault("10")
        int redirectWaitSeconds();
    }

    enum RuleMode {
        COMBINED,
        SEPARATE
    }

    /**
     * A single client-side rewrite rule (pattern + replacement).
     * Multiple rules per URL group are tried in index order by the browser.
     */
    interface ClientRule {

        /**
         * Regex pattern tested against window.location.href (including fragment) in the browser.
         */
        @WithName("pattern")
        String pattern();

        /**
         * Replace-pattern to rewrite the URL. Use ($groupName) to reference named capture groups.
         */
        @WithName("replace-pattern")
        String replacePattern();
    }
}
