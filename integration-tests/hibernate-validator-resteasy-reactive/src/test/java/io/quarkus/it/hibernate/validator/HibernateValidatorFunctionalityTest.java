package io.quarkus.it.hibernate.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogManager;

import javax.validation.ConstraintViolationException;
import javax.ws.rs.core.Response;

import org.jboss.logmanager.formatters.PatternFormatter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.hibernate.validator.runtime.jaxrs.ResteasyReactiveViolationException;
import io.quarkus.test.InMemoryLogHandler;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
public class HibernateValidatorFunctionalityTest {
    private static final Formatter LOG_FORMATTER = new PatternFormatter("%s");
    private static final java.util.logging.Logger rootLogger = LogManager.getLogManager().getLogger("io.quarkus");
    private static final InMemoryLogHandler inMemoryLogHandler = new InMemoryLogHandler(
            record -> record.getLevel().intValue() >= Level.WARNING.intValue());

    @BeforeEach
    public void setLogHandler() {
        inMemoryLogHandler.getRecords().clear();
        rootLogger.addHandler(inMemoryLogHandler);
    }

    @AfterEach
    public void removeLogHandler() {
        rootLogger.removeHandler(inMemoryLogHandler);
    }

    @Test
    public void testBasicFeatures() {
        StringBuilder expected = new StringBuilder();
        expected.append("failed: additionalEmails[0].<list element> (must be a well-formed email address)").append(", ")
                .append("categorizedEmails<K>[a].<map key> (length must be between 3 and 2147483647)").append(", ")
                .append("categorizedEmails[a].<map value>[0].<list element> (must be a well-formed email address)").append(", ")
                .append("email (must be a well-formed email address)").append(", ")
                .append("score (must be greater than or equal to 0)").append("\n");
        expected.append("passed");

        RestAssured.when()
                .get("/hibernate-validator/test/basic-features")
                .then()
                .body(is(expected.toString()));
    }

    @Test
    public void testCDIBeanMethodValidationUncaught() {
        // https://github.com/quarkusio/quarkus/issues/9174
        // Uncaught constraint validation exceptions thrown by user beans
        // are internal errors and should be reported as such.

        // The returned body should be the standard one produced by QuarkusErrorHandler,
        // with all the necessary information (stack trace, ...).
        RestAssured.when()
                .get("/hibernate-validator/test/cdi-bean-method-validation-uncaught")
                .then()
                .body(containsString(ConstraintViolationException.class.getName())) // Exception type
                .body(containsString("message: must not be null")) // Exception message
                .body(containsString("property path: greeting.name"))
                .body(containsString(EnhancedGreetingService.class.getName()))
                .body(containsString(HibernateValidatorTestResource.class.getName())) // Stack trace
                .statusCode(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());

        // There should also be some logs to raise the internal error to the developer's attention.
        assertThat(inMemoryLogHandler.getRecords())
                .extracting(LOG_FORMATTER::formatMessage)
                .hasSize(1);
        assertThat(inMemoryLogHandler.getRecords())
                .element(0).satisfies(record -> {
                    assertThat(record.getLevel()).isEqualTo(Level.SEVERE);
                    assertThat(LOG_FORMATTER.formatMessage(record))
                            .contains(
                                    "HTTP Request to /hibernate-validator/test/cdi-bean-method-validation-uncaught failed, error id:");
                });
    }

    @Test
    public void testRestEndPointValidation() {
        // https://github.com/quarkusio/quarkus/issues/9174
        // Constraint validation exceptions thrown by Resteasy and related to input values
        // are user errors and should be reported as such.

        // Bad request
        RestAssured.when()
                .get("/hibernate-validator/test/rest-end-point-validation/plop/")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .body(containsString("numeric value out of bounds"));

        // There should not be any warning/error logs since user errors do not require the developer's attention.
        assertThat(inMemoryLogHandler.getRecords())
                .extracting(LOG_FORMATTER::formatMessage)
                .isEmpty();

        RestAssured.when()
                .get("/hibernate-validator/test/rest-end-point-validation/42/")
                .then()
                .body(is("42"));
    }

    @Test
    public void testRestEndPointReturnValueValidation() {
        // https://github.com/quarkusio/quarkus/issues/9174
        // Constraint validation exceptions thrown by Resteasy and related to return values
        // are internal errors and should be reported as such.

        // The returned body should be the standard one produced by QuarkusErrorHandler,
        // with all the necessary information (stack trace, ...).
        RestAssured.when()
                .get("/hibernate-validator/test/rest-end-point-return-value-validation/plop/")
                .then()
                .body(containsString(ResteasyReactiveViolationException.class.getName())) // Exception type
                .body(containsString("numeric value out of bounds")) // Exception message
                .body(containsString("testRestEndPointReturnValueValidation.<return value>"))
                .body(containsString(HibernateValidatorTestResource.class.getName())) // Stack trace
                .statusCode(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());

        // There should also be some logs to raise the internal error to the developer's attention.
        assertThat(inMemoryLogHandler.getRecords())
                .extracting(LOG_FORMATTER::formatMessage)
                .hasSize(1);
        assertThat(inMemoryLogHandler.getRecords())
                .element(0).satisfies(record -> {
                    assertThat(record.getLevel()).isEqualTo(Level.SEVERE);
                    assertThat(LOG_FORMATTER.formatMessage(record))
                            .contains(
                                    "HTTP Request to /hibernate-validator/test/rest-end-point-return-value-validation/plop/ failed, error id:");
                });

        RestAssured.when()
                .get("/hibernate-validator/test/rest-end-point-validation/42/")
                .then()
                .body(is("42"));
    }

}
