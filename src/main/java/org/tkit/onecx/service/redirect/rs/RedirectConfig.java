package org.tkit.onecx.service.redirect.rs;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocFilename;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

/**
 * Redirect & Replace configuration
 */
@ConfigDocFilename("onecx-service-redirect.adoc")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "onecx.redirect")
public interface RedirectConfig {

    /**
     * Url Redirect Rules
     */
    @WithName("url-rewrite-rules")
    Map<String, RewriteRule> urlRewriteRules();

    /**
     * File path to custom template
     */
    @WithName("custom-template-path")
    Optional<String> customTemplatePath();

    /**
     * Url Redirect Rule
     */
    interface RewriteRule {

        /**
         * Pattern to match an incoming request
         */
        @WithName("pattern")
        String pattern();

        /**
         * Replace-pattern to rewrite the incoming request
         */
        @WithName("replace-pattern")
        String replacePattern();
    }
}
