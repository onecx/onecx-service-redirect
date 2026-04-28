package org.tkit.onecx.service.redirect.rs.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import org.tkit.onecx.service.redirect.rs.RedirectConfig;
import org.tkit.onecx.service.redirect.rs.RedirectUtils;

import io.quarkus.logging.Log;
import io.quarkus.qute.Engine;
import io.quarkus.qute.RawString;
import io.quarkus.qute.Template;

@Path("/{path:.*}")
@ApplicationScoped
@SuppressWarnings("java:S3655")
public class RedirectRestController {

    @Inject
    Template redirectTemplate;

    @Inject
    Template fallbackTemplate;

    @Inject
    RedirectConfig redirectConfig;

    @Inject
    Engine engine;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response redirectIncomingRequest(@Context UriInfo uriInfo) {
        String fullPath = uriInfo.getRequestUri().toString();

        RedirectConfig.RuleConfig ruleConfig = redirectConfig.rules();
        RedirectConfig.RuleMode rulesMode = ruleConfig.mode();
        int redirectWaitSeconds = ruleConfig.redirectWaitSeconds();

        RedirectConfig.HostForwardRule matchedHostForwardRule = findMatchedHostForwardRule(uriInfo.getRequestUri().getHost());
        Map<String, RedirectConfig.ClientRule> clientRules = findMatchedClientRules(fullPath, rulesMode,
                matchedHostForwardRule);

        // If neither host nor path rules matched, use fallback template
        if (matchedHostForwardRule == null && clientRules.isEmpty()) {
            return Response.ok(loadFallbackTemplate().data("reqPath", fullPath).render()).build();
        }

        Template tpl = loadRedirectTemplate();

        // Pass host-forward rule and path rules to the template.
        String rulesJson = clientRules.isEmpty() ? "[]" : RedirectUtils.rulesToJson(clientRules);

        io.quarkus.qute.TemplateInstance instance = tpl
                .data("hostForwardRule", matchedHostForwardRule)
                .data("rules", new RawString(rulesJson))
                .data("redirectWaitSeconds", redirectWaitSeconds);

        return Response.ok(instance.render()).build();
    }

    private RedirectConfig.HostForwardRule findMatchedHostForwardRule(String host) {
        return redirectConfig.hostForwardRules().values().stream()
                .filter(rule -> host.matches(rule.hostPattern()))
                .findFirst()
                .orElse(null);
    }

    private Map<String, RedirectConfig.ClientRule> findMatchedClientRules(String fullPath,
            RedirectConfig.RuleMode rulesMode,
            RedirectConfig.HostForwardRule matchedHostForwardRule) {
        if (rulesMode == RedirectConfig.RuleMode.SEPARATE && matchedHostForwardRule != null) {
            return Map.of();
        }

        return redirectConfig.urlRewriteRules().entrySet().stream()
                .filter(entry -> fullPath.matches(entry.getKey()))
                .max((e1, e2) -> {
                    int matchLength1 = e1.getKey().replace("\\.\\*", "").length();
                    int matchLength2 = e2.getKey().replace("\\.\\*", "").length();
                    return Integer.compare(matchLength1, matchLength2);
                })
                .map(Map.Entry::getValue)
                .orElse(Map.of());
    }

    private Template loadFallbackTemplate() {
        if (redirectConfig.customFallbackTemplatePath().isPresent()) {
            return parseTemplateFromPath(redirectConfig.customFallbackTemplatePath().get(), fallbackTemplate,
                    "Failed to load custom fallback template from path: " + redirectConfig.customFallbackTemplatePath());
        }
        return fallbackTemplate;
    }

    private Template loadRedirectTemplate() {
        if (redirectConfig.customRedirectTemplatePath().isPresent()) {
            return parseTemplateFromPath(redirectConfig.customRedirectTemplatePath().get(), redirectTemplate,
                    "Failed to load custom redirect template from path: " + redirectConfig.customRedirectTemplatePath());
        }

        if (redirectConfig.bundledRedirectTemplateName().isPresent()) {
            String templateName = redirectConfig.bundledRedirectTemplateName().get();
            Template bundledTemplate = engine.getTemplate(templateName);
            if (bundledTemplate != null) {
                return bundledTemplate;
            }
            Log.warn("Bundled redirect template not found: " + templateName + ", using default redirectTemplate");
        }

        return redirectTemplate;
    }

    private Template parseTemplateFromPath(String templatePath, Template defaultTemplate, String errorMessage) {
        try {
            String content = Files.readString(Paths.get(templatePath), StandardCharsets.UTF_8);
            return engine.parse(content);
        } catch (IOException e) {
            Log.error(errorMessage, e);
            return defaultTemplate;
        }
    }
}
