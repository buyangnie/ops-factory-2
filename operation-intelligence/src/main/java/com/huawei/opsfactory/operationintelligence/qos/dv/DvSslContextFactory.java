/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.dv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

/**
 * Dv Ssl Context Factory.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@Component
public class DvSslContextFactory {

    private static final Logger log = LoggerFactory.getLogger(DvSslContextFactory.class);

    private final ConcurrentHashMap<String, SSLContext> sslContextCache = new ConcurrentHashMap<>();

    /**
     * create Ssl Context.
     *
     * @param crtContent the crtContent
     * @param fileName the fileName
     * @param strictSsl the strictSsl
     * @return the result
     */
    public SSLContext createSslContext(String crtContent, String fileName, boolean strictSsl) {
        if (crtContent == null || crtContent.isBlank()) {
            if (strictSsl) {
                throw new IllegalStateException("No SSL certificate configured and strict-ssl is enabled");
            }
            log.warn("INSECURE SSL MODE: No certificate configured and strict-ssl=false. "
                + "Using insecure trust manager that accepts all certificates. "
                + "WARNING: This is acceptable ONLY for development environment. "
                + "Production environment MUST have strict-ssl=true and valid certificate.");
            return createInsecureSslContext();
        }

        return sslContextCache.computeIfAbsent(crtContent, k -> doCreateSslContext(k, fileName, strictSsl));
    }

    /**
     * create Ssl Context.
     *
     * @param crtContent the crtContent
     * @param fileName the fileName
     * @return the result
     */
    public SSLContext createSslContext(String crtContent, String fileName) {
        return createSslContext(crtContent, fileName, true);
    }

    private SSLContext doCreateSslContext(String crtContent, String fileName, boolean strictSsl) {
        try {
            byte[] certBytes = java.util.Base64.getDecoder().decode(crtContent);
            String type = (fileName != null && fileName.endsWith(".p12")) ? "PKCS12" : "JKS";
            KeyStore keyStore = KeyStore.getInstance(type);
            try (InputStream is = new ByteArrayInputStream(certBytes)) {
                keyStore.load(is, new char[0]);
            }

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, new char[0]);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);

            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

            return sslContext;
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException | IOException
            | CertificateException | UnrecoverableKeyException e) {
            if (strictSsl) {
                throw new IllegalStateException("Failed to create SSL context with certificate (strict-ssl enabled)",
                    e);
            }
            log.error(
                "SSL context creation failed and strict-ssl=false. " + "FALLING BACK TO INSECURE MODE. "
                    + "This accepts ALL certificates and should ONLY be used in development. "
                    + "Production environment requires strict-ssl=true and valid certificates. " + "Error: {}",
                e.getMessage());
            return createInsecureSslContext();
        }
    }

    /**
     * create Insecure Ssl Context.
     *
     * @return the result
     */
    private SSLContext createInsecureSslContext() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] {new javax.net.ssl.X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[0];
                }

                public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                }
            }}, new SecureRandom());
            return sslContext;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalStateException("Failed to create insecure SSL context", e);
        }
    }
}