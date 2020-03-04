package io.jenkins.plugins.aws.global_configuration;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

public abstract class AbstractCheckEndpointConfigurationIT {

    @BeforeClass
    public static void fakeAwsCredentials() {
        System.setProperty("aws.accessKeyId", "test");
        System.setProperty("aws.secretKey", "test");
    }

    /**
     * Check connectivity using the default (implicit) signing region.
     */
    protected abstract Result validate(String serviceEndpoint);

    /**
     * Check connectivity using the specified signing region.
     */
    protected abstract Result validate(String serviceEndpoint, String signingRegion);

    @Test
    public void shouldTestConnection() {
        // When
        final Result result = validate("http://localhost:4584");

        // Then
        assertSoftly(s -> {
            s.assertThat(result.isSuccess).as("Success").isTrue();
            s.assertThat(result.msg).as("Message").isEqualTo("Success");
        });
    }

    @Test
    public void shouldTestConnectionWithSigningRegion() {
        // When
        final Result result = validate("http://localhost:4584", "us-east-1");

        // Then
        assertSoftly(s -> {
            s.assertThat(result.isSuccess).as("Success").isTrue();
            s.assertThat(result.msg).as("Message").isEqualTo("Success");
        });
    }

    @Test
    public void shouldRevealClientErrorsInTestConnection() {
        // When
        final Result result = validate("http://localhost:1", "us-east-1");

        // Then
        assertSoftly(s -> {
            s.assertThat(result.isSuccess).as("Success").isFalse();
            s.assertThat(result.msg).as("Message").startsWith("AWS client error");
        });
    }

    static final class Result {

        private final boolean isSuccess;
        private final String msg;

        private Result(boolean isSuccess, String msg) {
            this.isSuccess = isSuccess;
            this.msg = msg;
        }

        static Result success(String msg) {
            return new Result(true, msg);
        }

        static Result error(String msg) {
            return new Result(false, msg);
        }
    }
}
