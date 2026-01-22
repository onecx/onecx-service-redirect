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
        assertThat(body).contains("custom");
        assertThat(body).contains("some/unknown/path");
    }

    @Test
    void usesFallbackWhenNoRuleMatchesAndCustomTemplateFailed() throws IOException {
        Path tmp = Files.createTempFile("tpl", ".html");
        Files.writeString(tmp, "custom {reqPath}", StandardCharsets.UTF_8);

        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of());
        Mockito.when(redirectConfig.customFallbackTemplatePath()).thenReturn(Optional.of("not/existing/path.html"));

        var body = given()
                .accept(TEXT_HTML)
                .get("/some/unknown/path")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body).contains("/some/unknown/path");
        assertThat(body).doesNotContain("custom");
    }

    @Test
    void appliesBestMatchingRule() {
        var ruleMap = new HashMap<String, RedirectConfig.RewriteRule>();

        ruleMap.put(".*test-ui.*", new RedirectConfig.RewriteRule() {
            @Override
            public String pattern() {
                return ".*test-ui.*";
            }

            @Override
            public String replacePattern() {
                return "/new/path";
            }
        });

        ruleMap.put(".*test-ui/subTest.*", new RedirectConfig.RewriteRule() {
            @Override
            public String pattern() {
                return ".*test-ui/subTest.*";
            }

            @Override
            public String replacePattern() {
                return "/new/path/subTest";
            }
        });

        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(ruleMap);
        Mockito.when(redirectConfig.customRedirectTemplatePath()).thenReturn(Optional.empty());

        var body = given()
                .accept(TEXT_HTML)
                .get("/test-ui/subTest")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body).contains(".*test-ui/subTest.*");
        assertThat(body).contains("/new/path/subTest");
    }

    @Test
    void appliesDefaultTemplateWhenRuleMatches() {
        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of(".*test-ui.*", new RedirectConfig.RewriteRule() {
            @Override
            public String pattern() {
                return ".*test-ui.*";
            }

            @Override
            public String replacePattern() {
                return "/new/path";
            }
        }));
        Mockito.when(redirectConfig.customRedirectTemplatePath()).thenReturn(Optional.empty());

        var body = given()
                .accept(TEXT_HTML)
                .get("/test-ui/old")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body).contains(".*test-ui.*");
        assertThat(body).contains("/new/path");
    }

    @Test
    void usesCustomTemplateWhenConfigured() throws IOException {
        Path tmp = Files.createTempFile("tpl", ".html");
        Files.writeString(tmp, "custom {p1}-{p2}", StandardCharsets.UTF_8);

        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of(".*custom-test.*", new RedirectConfig.RewriteRule() {
            @Override
            public String pattern() {
                return ".*custom-test.*";
            }

            @Override
            public String replacePattern() {
                return "/custom/replaced";
            }
        }));
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
    void continuesWithDefaultTemplateWhenCustomTemplateFails() {
        Mockito.when(redirectConfig.urlRewriteRules()).thenReturn(Map.of(".*fallback-test.*", new RedirectConfig.RewriteRule() {
            @Override
            public String pattern() {
                return ".*fallback-test.*";
            }

            @Override
            public String replacePattern() {
                return "/fallback/replaced";
            }
        }));
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
}
