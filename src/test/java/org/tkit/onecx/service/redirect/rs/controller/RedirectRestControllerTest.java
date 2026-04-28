package org.tkit.onecx.service.redirect.rs.controller;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.TEXT_HTML;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.tkit.onecx.service.redirect.rs.RedirectConfig;

import io.quarkus.test.InjectMock;
import io.quarkus.test.Mock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.config.SmallRyeConfig;

@QuarkusTest
class RedirectRestControllerTest {

    @InjectMock
    RedirectConfig redirectConfig;

    public static class ConfigProducer {

        @Inject
        Config config;

        @Produces
        @ApplicationScoped
        @Mock
        RedirectConfig config() {
            return config.unwrap(SmallRyeConfig.class).getConfigMapping(RedirectConfig.class);
        }
    }

    @BeforeEach
    void setUpHostRules() {
        Mockito.when(redirectConfig.hostForwardRules()).thenReturn(Map.of());
        Mockito.when(redirectConfig.rules()).thenReturn(ruleConfig(RedirectConfig.RuleMode.COMBINED, 10));
        Mockito.when(redirectConfig.bundledRedirectTemplateName()).thenReturn(Optional.empty());
    }

    private static RedirectConfig.ClientRule clientRule(String pattern, String replacePattern) {
        return new RedirectConfig.ClientRule() {
            @Override
            public String pattern() {
                return pattern;
            }

            @Override
            public String replacePattern() {
                return replacePattern;
            }
        };
    }

    @Test
    void usesFallbackWhenNoRuleMatches() {
        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of());
        Mockito.when(redirectConfig.customFallbackTemplatePath()).thenReturn(Optional.empty());

        var body = given()
                .accept(TEXT_HTML)
                .get("/some/unknown/path")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body).contains("/some/unknown/path");
    }

    @Test
    void usesCustomFallbackWhenNoRuleMatches() throws IOException {
        Path tmp = Files.createTempFile("tpl", ".html");
        Files.writeString(tmp, "custom {reqPath}", StandardCharsets.UTF_8);

        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of());
        Mockito.when(redirectConfig.customFallbackTemplatePath()).thenReturn(Optional.of(tmp.toString()));

        var body = given()
                .accept(TEXT_HTML)
                .get("/some/unknown/path")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body).contains("custom").contains("some/unknown/path");
    }

    @Test
    void usesFallbackWhenNoRuleMatchesAndCustomTemplateFailed() {
        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of());
        Mockito.when(redirectConfig.customFallbackTemplatePath()).thenReturn(Optional.of("not/existing/path.html"));

        var body = given()
                .accept(TEXT_HTML)
                .get("/some/unknown/path")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body).contains("/some/unknown/path").doesNotContain("custom");
    }

    @Test
    void appliesBestMatchingRule() {
        var ruleMap = new HashMap<String, Map<String, RedirectConfig.ClientRule>>();

        ruleMap.put(".*test-ui.*", Map.of(
                "0", clientRule(".*test-ui.*", "/new/path")));

        ruleMap.put(".*test-ui/subTest.*", Map.of(
                "0", clientRule(".*test-ui/subTest.*", "/new/path/subTest")));

        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(ruleMap);
        Mockito.when(redirectConfig.customRedirectTemplatePath()).thenReturn(Optional.empty());

        var body = given()
                .accept(TEXT_HTML)
                .get("/test-ui/subTest")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body).contains(".*test-ui/subTest.*").contains("/new/path/subTest");
    }

    @Test
    void appliesDefaultTemplateWhenRuleMatches() {
        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of(
                ".*test-ui.*", Map.of(
                        "0", clientRule(".*test-ui.*", "/new/path"))));
        Mockito.when(redirectConfig.customRedirectTemplatePath()).thenReturn(Optional.empty());

        var body = given()
                .accept(TEXT_HTML)
                .get("/test-ui/old")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body).contains(".*test-ui.*");
        assertThat(body).contains("/new/path");
        assertThat(body).doesNotContain("new-host.example.com");
    }

    @Test
    void usesCustomTemplateWhenConfigured() throws IOException {
        Path tmp = Files.createTempFile("tpl", ".html");
        Files.writeString(tmp, "custom {rules}", StandardCharsets.UTF_8);

        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of(
                ".*custom-test.*", Map.of(
                        "0", clientRule(".*custom-test.*", "/custom/replaced"))));
        Mockito.when(redirectConfig.customRedirectTemplatePath()).thenReturn(Optional.of(tmp.toString()));

        var body = given()
                .accept(TEXT_HTML)
                .get("/custom-test/path")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body).contains("custom");
        assertThat(body).contains(".*custom-test.*");
        assertThat(body).contains("/custom/replaced");
    }

    @Test
    void usesRedirectWaitTemplateWithConfiguredWaitSeconds() {
        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of(
                ".*wait-test.*", Map.of(
                        "0", clientRule(".*wait-test.*", "/wait/replaced"))));
        Mockito.when(redirectConfig.customRedirectTemplatePath())
                .thenReturn(Optional.of(Path.of("src/main/resources/templates/redirectWaitTemplate.html")
                        .toAbsolutePath().toString()));
        Mockito.when(redirectConfig.rules()).thenReturn(ruleConfig(RedirectConfig.RuleMode.COMBINED, 7));

        var body = given()
                .accept(TEXT_HTML)
                .get("/wait-test/path")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body)
                .contains("aria-label=\"Redirecting in 7 seconds\"")
                .contains("id=\"ring-number\">7</div>")
                .contains("const TOTAL_SECONDS = 7;");
    }

    @Test
    void usesBundledRedirectTemplateWhenConfigured() {
        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of(
                ".*wait-test.*", Map.of(
                        "0", clientRule(".*wait-test.*", "/wait/replaced"))));
        Mockito.when(redirectConfig.bundledRedirectTemplateName()).thenReturn(Optional.of("redirectWaitTemplate.html"));
        Mockito.when(redirectConfig.rules()).thenReturn(ruleConfig(RedirectConfig.RuleMode.COMBINED, 8));

        var body = given()
                .accept(TEXT_HTML)
                .get("/wait-test/path")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body)
                .contains("aria-label=\"Redirecting in 8 seconds\"")
                .contains("const TOTAL_SECONDS = 8;");
    }

    @Test
    void fallsBackToDefaultTemplateWhenBundledTemplateNameIsInvalid() {
        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of(
                ".*wait-test.*", Map.of(
                        "0", clientRule(".*wait-test.*", "/wait/replaced"))));
        Mockito.when(redirectConfig.bundledRedirectTemplateName()).thenReturn(Optional.of("missing-template.html"));

        var body = given()
                .accept(TEXT_HTML)
                .get("/wait-test/path")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body)
                .contains("/wait/replaced")
                .doesNotContain("aria-label=\"Redirecting in");
    }

    @Test
    void continuesWithDefaultTemplateWhenCustomTemplateFails() {
        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of(
                ".*fallback-test.*", Map.of(
                        "0", clientRule(".*fallback-test.*", "/fallback/replaced"))));
        Mockito.when(redirectConfig.customRedirectTemplatePath()).thenReturn(Optional.of("/non/existing/path.html"));

        var body = given()
                .accept(TEXT_HTML)
                .get("/fallback-test/path")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body).contains(".*fallback-test.*");
        assertThat(body).contains("/fallback/replaced");
    }

    @Test
    void appliesMultipleClientRulesInOrder() {
        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of(
                ".*multi-rule.*", Map.of(
                        "0", clientRule(".*multi-rule.*#/task/(?<woId>.+)", "/workorder/($woId)"),
                        "1", clientRule(".*multi-rule.*#/testOrder/(?<orderId>.+)", "/testorder/($orderId)"),
                        "2", clientRule(".*multi-rule.*", "/overview"))));
        Mockito.when(redirectConfig.customRedirectTemplatePath()).thenReturn(Optional.empty());

        var body = given()
                .accept(TEXT_HTML)
                .get("/multi-rule/page")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body).contains("/workorder/($woId)").contains("/testorder/($orderId)").contains("/overview");
    }

    @Test
    void appliesSimpleRuleWithoutNamedGroups() {
        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of(
                ".*test-ui.*", Map.of(
                        "0", clientRule("old", "new"))));
        Mockito.when(redirectConfig.customRedirectTemplatePath()).thenReturn(Optional.empty());

        var body = given()
                .accept(TEXT_HTML)
                .get("/test-ui/old")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body).contains("\"pattern\":\"old\"").contains("\"replacePattern\":\"new\"");
    }

    @Test
    void appliesRuleWithNonNumericKeyWithoutThrowing() {
        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of(
                ".*non-numeric.*", Map.of(
                        "not-a-number", clientRule(".*non-numeric.*", "/some/path"))));
        Mockito.when(redirectConfig.customRedirectTemplatePath()).thenReturn(Optional.empty());

        var body = given()
                .accept(TEXT_HTML)
                .get("/non-numeric/page")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body).contains("/some/path");
    }

    @Test
    void redirectsToProxyHostWhenHostRuleMatches() {
        Mockito.when(redirectConfig.hostForwardRules()).thenReturn(Map.of(
                "rule-1", hostForwardRule("localhost", "new-host.example.com", Optional.empty())));
        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of());

        var body = given()
                .accept(TEXT_HTML)
                .get("/some/path")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body).contains("new-host.example.com");
    }

    @Test
    void redirectsToProxyHostWithExplicitProtocol() {
        Mockito.when(redirectConfig.hostForwardRules()).thenReturn(Map.of(
                "rule-1", hostForwardRule("localhost", "new-host.example.com", Optional.of("https"))));
        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of());

        var body = given()
                .accept(TEXT_HTML)
                .get("/some/path")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body).contains("new-host.example.com").contains("https");
    }

    @Test
    void appliesHostAndPathRewriteInCombinedModeWhenBothRulesMatch() {
        Mockito.when(redirectConfig.hostForwardRules()).thenReturn(Map.of(
                "rule-1", hostForwardRule("localhost", "new-host.example.com", Optional.empty())));
        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of(
                ".*some/path.*", Map.of(
                        "0", clientRule(".*some/path.*", "/rewritten/path"))));
        Mockito.when(redirectConfig.rules()).thenReturn(ruleConfig(RedirectConfig.RuleMode.COMBINED, 10));

        var body = given()
                .accept(TEXT_HTML)
                .get("/some/path")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body)
                .contains("new-host.example.com")
                .contains("/rewritten/path");
    }

    @Test
    void appliesOnlyHostForwardInSeparateModeWhenBothRulesMatch() {
        Mockito.when(redirectConfig.hostForwardRules()).thenReturn(Map.of(
                "rule-1", hostForwardRule("localhost", "new-host.example.com", Optional.empty())));
        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of(
                ".*some/path.*", Map.of(
                        "0", clientRule(".*some/path.*", "/rewritten/path"))));
        Mockito.when(redirectConfig.rules()).thenReturn(ruleConfig(RedirectConfig.RuleMode.SEPARATE, 10));

        var body = given()
                .accept(TEXT_HTML)
                .get("/some/path")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body)
                .contains("new-host.example.com")
                .doesNotContain("/rewritten/path");
    }

    @Test
    void fallsBackToUrlRewriteRuleWhenNoHostRuleMatches() {
        Mockito.when(redirectConfig.hostForwardRules()).thenReturn(Map.of(
                "rule-1", hostForwardRule("other-host", "new-host.example.com", Optional.empty())));
        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of(
                ".*some/path.*", Map.of(
                        "0", clientRule(".*some/path.*", "/rewritten/path"))));
        Mockito.when(redirectConfig.customRedirectTemplatePath()).thenReturn(Optional.empty());

        var body = given()
                .accept(TEXT_HTML)
                .get("/some/path")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body)
                .contains("/rewritten/path")
                .doesNotContain("new-host.example.com");
    }

    @Test
    void appliesPathRewriteInSeparateModeWhenNoHostRuleMatches() {
        Mockito.when(redirectConfig.hostForwardRules()).thenReturn(Map.of(
                "rule-1", hostForwardRule("other-host", "new-host.example.com", Optional.empty())));
        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of(
                ".*some/path.*", Map.of(
                        "0", clientRule(".*some/path.*", "/rewritten/path"))));
        Mockito.when(redirectConfig.rules()).thenReturn(ruleConfig(RedirectConfig.RuleMode.SEPARATE, 10));

        var body = given()
                .accept(TEXT_HTML)
                .get("/some/path")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body)
                .contains("/rewritten/path")
                .doesNotContain("new-host.example.com");
    }

    private static RedirectConfig.HostForwardRule hostForwardRule(String hostPattern, String proxyHost,
            Optional<String> proxyProtocol) {
        return new RedirectConfig.HostForwardRule() {
            @Override
            public String hostPattern() {
                return hostPattern;
            }

            @Override
            public String proxyHost() {
                return proxyHost;
            }

            @Override
            public Optional<String> proxyProtocol() {
                return proxyProtocol;
            }
        };
    }

    private static RedirectConfig.RuleConfig ruleConfig(RedirectConfig.RuleMode mode, int redirectWaitSeconds) {
        return new RedirectConfig.RuleConfig() {
            @Override
            public RedirectConfig.RuleMode mode() {
                return mode;
            }

            @Override
            public int redirectWaitSeconds() {
                return redirectWaitSeconds;
            }
        };
    }

}
