/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net;

import android.util.Log;

import org.apache.http.HttpHost;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;


/**
 * Implementation of {@link java.net.ProxySelector} that chooses the currently active proxy as
 * reportsed by {@link android.net.Proxy}. An application-set proxy set in system propreties
 * (e.g., {@code http.proxyHost} and {@code http.proxyPort}) will trump these settings.
 *
 * {@hide}
 */
class ProxySelectorImpl extends ProxySelector {

    private static final String TAG = "ProxySelectorImpl";

    /** The default selector */
    private ProxySelector mDefaultSelector;

    /**
     * Static block to create the sole instance of this class and set it as the
     * default selector
     */
    static {
        ProxySelector.setDefault(new ProxySelectorImpl(ProxySelector.getDefault()));
    }

    /**
     * Creates a new ProxySelectorImpl object, using the passed {@link
     * ProxySelector} as the previous default selector.
     */
    private ProxySelectorImpl(ProxySelector defaultSelector) {
        mDefaultSelector = defaultSelector;
    }

    /**
     * Returns the proxy to use based on a URI passed. Our implementation first refers to the
     * default implementation. If no proxy is returned there (i.e., there is no app-set system
     * roperty) then we return the proxy as returned by {@link Proxy} for HTTP and HTTPS protocols.
     *
     * @param uri
     *            the target URI object.
     * @return a list containing all applicable proxies. If no proxy is
     *         available, the list contains only the {@code Proxy.NO_PROXY}
     *         element.
     * @throws IllegalArgumentException if {@code uri} is {@code null}.
     */
    public List<Proxy> select(URI uri) {
        List<Proxy> defaultProxyList = mDefaultSelector.select(uri);

        if (!(uri.getScheme().equals("http") || uri.getScheme().equals("https"))) {
            // We don't do non-HTTP
            return defaultProxyList;
        }

        if (defaultProxyList == null ||
            defaultProxyList.size() == 0 ||
            (defaultProxyList.size() == 1 &&
             defaultProxyList.get(0) == java.net.Proxy.NO_PROXY)) {

            // No proxy was returned from mDefaultSelector, let's find our own
            List proxyList = new ArrayList(1);
            HttpHost proxyHost = android.net.Proxy.getPreferredHttpHost(null, uri.toString());
            
            if (proxyHost != null) {
                InetSocketAddress proxyAddress = InetSocketAddress
                    .createUnresolved(proxyHost.getHostName(), proxyHost.getPort());
                java.net.Proxy proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP, proxyAddress);

                proxyList.add(proxy);
                
            } else {
                proxyList.add(java.net.Proxy.NO_PROXY);
            }

            return proxyList;
            
            
        } else {
            // mDefaultSelector returned a proxy, use that
            return defaultProxyList;
        }

    }

    /**
     * Notification that a connection to the proxy server could not be established. We don't do
     * anything with this, just pass on to the default ProxySelector.
     *
     * @throws IllegalArgumentException
     *             if any argument is {@code null}.
     * @see #select(URI)
     */
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        mDefaultSelector.connectFailed(uri, sa, ioe);
    }
}

