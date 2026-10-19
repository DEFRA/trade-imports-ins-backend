package uk.gov.defra.trade.imports.ins.backend.configuration.tls;

import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads the custom X.509 certificate held base64-encoded in {@code cdp.certificate}. Returns
 * {@code null} when none is set or it cannot be parsed, so the application still starts.
 */
@Component
@Slf4j
public class CertificateLoader {

    private final String certificate;
    
    public CertificateLoader(@Value("${cdp.certificate}") String certificate) {
        this.certificate = certificate;
    }
    
    public X509Certificate loadCustomCertificate() {
        X509Certificate cert = null;
        if (certificate == null || certificate.isEmpty()) {
            log.info("No custom certificates to load");
            return cert;
        }
        
        byte[] certData = Base64.getDecoder().decode(certificate);

        CertificateFactory cf;
        try {
            cf = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new IllegalStateException("Cannot initialize certificate factory", e);
        }
        
        try {
            ByteArrayInputStream certStream = new ByteArrayInputStream(certData);
            cert = (X509Certificate) cf.generateCertificate(certStream);

            log.info("Successfully loaded certificate: (Subject: {})",
                cert.getSubjectX500Principal().getName());

        } catch (CertificateException e) {
            log.error("Failed to parse the custom certificate; continuing without it", e);
        }
        
        return cert;
    }
   
}
