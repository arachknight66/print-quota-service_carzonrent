package com.printkeep.quota.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.security.auth.x500.X500Principal;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;

/**
 * Unit tests verifying client certificate servlet filtering for print requests.
 */
class ClientCertAuthFilterTests {

    private ClientCertAuthFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new ClientCertAuthFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @Test
    void testValidCertificateStoresCommonNameAndContinues() throws Exception {
        request.setAttribute(
                ClientCertAuthFilter.CERTIFICATE_REQUEST_ATTRIBUTE,
                new X509Certificate[] { certificateWithSubject("CN=jdoe,OU=PrintKeep") }
        );

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute(ClientCertAuthFilter.VERIFIED_CLIENT_CN_ATTRIBUTE)).isEqualTo("jdoe");
    }

    @Test
    void testMissingCertificateRejectsWithForbidden() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(request.getAttribute(ClientCertAuthFilter.VERIFIED_CLIENT_CN_ATTRIBUTE)).isNull();
    }

    @Test
    void testExpiredCertificateRejectsWithForbidden() throws Exception {
        final X509Certificate certificate = certificateWithSubject("CN=jdoe");
        doThrow(new CertificateExpiredException()).when(certificate).checkValidity();
        request.setAttribute(ClientCertAuthFilter.CERTIFICATE_REQUEST_ATTRIBUTE, new X509Certificate[] { certificate });

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void testNotYetValidCertificateRejectsWithForbidden() throws Exception {
        final X509Certificate certificate = certificateWithSubject("CN=jdoe");
        doThrow(new CertificateNotYetValidException()).when(certificate).checkValidity();
        request.setAttribute(ClientCertAuthFilter.CERTIFICATE_REQUEST_ATTRIBUTE, new X509Certificate[] { certificate });

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void testEmptyCommonNameRejectsWithForbidden() throws Exception {
        request.setAttribute(
                ClientCertAuthFilter.CERTIFICATE_REQUEST_ATTRIBUTE,
                new X509Certificate[] { certificateWithSubject("OU=PrintKeep") }
        );

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void testMalformedCertificateRejectsWithForbidden() throws Exception {
        final X509Certificate certificate = mock(X509Certificate.class);
        when(certificate.getSubjectX500Principal()).thenThrow(new IllegalArgumentException("malformed subject"));
        request.setAttribute(ClientCertAuthFilter.CERTIFICATE_REQUEST_ATTRIBUTE, new X509Certificate[] { certificate });

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
    }

    private static X509Certificate certificateWithSubject(final String subject) {
        final X509Certificate certificate = mock(X509Certificate.class);
        when(certificate.getSubjectX500Principal()).thenReturn(new X500Principal(subject));
        return certificate;
    }
}
