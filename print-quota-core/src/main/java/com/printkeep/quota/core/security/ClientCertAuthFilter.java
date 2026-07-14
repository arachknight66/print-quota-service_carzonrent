package com.printkeep.quota.core.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;

/**
 * Servlet filter enforcing client certificate presence for IPP print requests.
 */
public class ClientCertAuthFilter implements Filter {

    public static final String CERTIFICATE_REQUEST_ATTRIBUTE = "jakarta.servlet.request.X509Certificate";
    public static final String VERIFIED_CLIENT_CN_ATTRIBUTE = "verifiedClientCN";

    @Override
    public void doFilter(
            final ServletRequest request,
            final ServletResponse response,
            final FilterChain chain) throws IOException, ServletException {

        final X509Certificate[] certificates =
                (X509Certificate[]) request.getAttribute(CERTIFICATE_REQUEST_ATTRIBUTE);
        if (certificates == null || certificates.length == 0 || certificates[0] == null) {
            reject(response);
            return;
        }

        final X509Certificate clientCertificate = certificates[0];
        final String commonName;
        try {
            clientCertificate.checkValidity();
            commonName = extractCommonName(clientCertificate);
        } catch (final CertificateExpiredException | CertificateNotYetValidException | InvalidNameException
                       | IllegalArgumentException e) {
            reject(response);
            return;
        }

        if (commonName == null || commonName.isBlank()) {
            reject(response);
            return;
        }

        request.setAttribute(VERIFIED_CLIENT_CN_ATTRIBUTE, commonName);
        chain.doFilter(request, response);
    }

    private static String extractCommonName(final X509Certificate certificate) throws InvalidNameException {
        final X500Principal subject = certificate.getSubjectX500Principal();
        final LdapName ldapName = new LdapName(subject.getName());
        for (final Rdn rdn : ldapName.getRdns()) {
            if ("CN".equalsIgnoreCase(rdn.getType())) {
                return rdn.getValue().toString();
            }
        }
        return null;
    }

    private static void reject(final ServletResponse response) throws IOException {
        if (response instanceof HttpServletResponse httpResponse) {
            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        response.getWriter().write("Forbidden");
    }
}
