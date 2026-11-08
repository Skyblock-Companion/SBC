// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.core;

import dev.zmuzy.sbc.SbcMod;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

// trusts what java trusts + what the pc trusts, like a browser. an antivirus that scans https (avast,
// kaspersky...) signs sites with its own cert which it adds to windows but not java, so without this every
// call fails
public final class Tls {
    private static SSLContext context;

    private Tls() {}

    private static X509TrustManager managerFor(KeyStore store) throws Exception {
        TrustManagerFactory f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        f.init(store);
        for (TrustManager m : f.getTrustManagers()) if (m instanceof X509TrustManager x) return x;
        throw new IllegalStateException("no trust manager");
    }

    public static synchronized SSLContext context() {
        if (context != null) return context;
        try {
            List<X509TrustManager> managers = new ArrayList<>();
            managers.add(managerFor(null));
            try {
                KeyStore windows = KeyStore.getInstance("Windows-ROOT");
                windows.load(null, null);
                managers.add(managerFor(windows));
            } catch (Exception notWindows) {
                SbcMod.LOG.debug("No Windows certificate store", notWindows);
            }
            X509TrustManager both = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String auth) throws CertificateException {
                    managers.getFirst().checkClientTrusted(chain, auth);
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String auth) throws CertificateException {
                    CertificateException last = null;
                    for (X509TrustManager m : managers) {
                        try {
                            m.checkServerTrusted(chain, auth);
                            return;
                        } catch (CertificateException e) {
                            last = e;
                        }
                    }
                    throw last;
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    List<X509Certificate> all = new ArrayList<>();
                    for (X509TrustManager m : managers) all.addAll(List.of(m.getAcceptedIssuers()));
                    return all.toArray(new X509Certificate[0]);
                }
            };
            SSLContext c = SSLContext.getInstance("TLS");
            c.init(null, new TrustManager[]{both}, null);
            context = c;
        } catch (Exception e) {
            SbcMod.LOG.warn("Using Java's certificates only", e);
            try {
                context = SSLContext.getDefault();
            } catch (Exception impossible) {
                throw new IllegalStateException(impossible);
            }
        }
        return context;
    }
}
