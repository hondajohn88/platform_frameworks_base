/*
 * Copyright (C) 2006 The Android Open Source Project
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

package android.webkit;

import android.net.ParseException;
import android.net.WebAddress;
import android.os.AsyncTask;
import android.util.Log;


/**
 * Manages the cookies used by an application's {@link WebView} instances.
 * Cookies are manipulated according to RFC2109.
 */
public class CookieManager {

    private static CookieManager sRef;

    private static final String LOGTAG = "webkit";

    private int mPendingCookieOperations = 0;

    private CookieManager() {
    }

    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException("doesn't implement Cloneable");
    }

    /**
     * Gets the singleton CookieManager instance. If this method is used
     * before the application instantiates a {@link WebView} instance,
     * {@link CookieSyncManager#createInstance(Context)} must be called
     * first.
     * 
     * @return The singleton CookieManager instance
     */
    public static synchronized CookieManager getInstance() {
        if (sRef == null) {
            sRef = new CookieManager();
        }
        return sRef;
    }

    /**
     * Sets whether the application's {@link WebView} instances should send and
     * accept cookies.
     * @param accept Whether {@link WebView} instances should send and accept
     *               cookies
     */
    public synchronized void setAcceptCookie(boolean accept) {
        nativeSetAcceptCookie(accept);
    }

    /**
     * Gets whether the application's {@link WebView} instances send and accept
     * cookies.
     * @return True if {@link WebView} instances send and accept cookies
     */
    public synchronized boolean acceptCookie() {
        return nativeAcceptCookie();
    }

    /**
     * Sets a cookie for the given URL. Any existing cookie with the same host,
     * path and name will be replaced with the new cookie. The cookie being set
     * must not have expired and must not be a session cookie, otherwise it
     * will be ignored.
     * @param url The URL for which the cookie is set
     * @param value The cookie as a string, using the format of the
     *              'Set-Cookie' HTTP response header
     */
    public void setCookie(String url, String value) {
        setCookie(url, value, false);
    }

    /**
     * See {@link setCookie(String, String)}
     * @param url The URL for which the cookie is set
     * @param value The value of the cookie, as a string, using the format of
     *              the 'Set-Cookie' HTTP response header
     * @param privateBrowsing Whether to use the private browsing cookie jar
     */
    void setCookie(String url, String value, boolean privateBrowsing) {
        WebAddress uri;
        try {
            uri = new WebAddress(url);
        } catch (ParseException ex) {
            Log.e(LOGTAG, "Bad address: " + url);
            return;
        }

        nativeSetCookie(uri.toString(), value, privateBrowsing);
    }

    /**
     * Gets the cookies for the given URL.
     * @param url The URL for which the cookies are requested
     * @return value The cookies as a string, using the format of the 'Cookie'
     *               HTTP request header
     */
    public String getCookie(String url) {
        return getCookie(url, false);
    }

    /**
     * See {@link getCookie(String)}
     * @param url The URL for which the cookies are requested
     * @param privateBrowsing Whether to use the private browsing cookie jar
     * @return value The cookies as a string, using the format of the 'Cookie'
     *               HTTP request header
     * @hide Used by Browser, no intention to publish.
     */
    public String getCookie(String url, boolean privateBrowsing) {
        WebAddress uri;
        try {
            uri = new WebAddress(url);
        } catch (ParseException ex) {
            Log.e(LOGTAG, "Bad address: " + url);
            return null;
        }

        return nativeGetCookie(uri.toString(), privateBrowsing);
    }

    /**
     * Get cookie(s) for a given uri so that it can be set to "cookie:" in http
     * request header.
     * @param uri The WebAddress for which the cookies are requested
     * @return value The cookies as a string, using the format of the 'Cookie'
     *               HTTP request header
     * @hide Used by RequestHandle, no intention to publish.
     */
    public synchronized String getCookie(WebAddress uri) {
        return nativeGetCookie(uri.toString(), false);
    }

    /**
     * Waits for pending operations to completed.
     */
    void waitForCookieOperationsToComplete() {
        // Note that this function is applicable for both the java
        // and native http stacks, and works correctly with either.
        synchronized (this) {
            while (mPendingCookieOperations > 0) {
                try {
                    wait();
                } catch (InterruptedException e) { }
            }
        }
    }

    private synchronized void signalCookieOperationsComplete() {
        mPendingCookieOperations--;
        assert mPendingCookieOperations > -1;
        notify();
    }

    private synchronized void signalCookieOperationsStart() {
        mPendingCookieOperations++;
    }

    /**
     * Removes all session cookies, which are cookies without an expiration
     * date.
     */
    public void removeSessionCookie() {
        signalCookieOperationsStart();
        new AsyncTask<Void, Void, Void>() {
            protected Void doInBackground(Void... none) {
                nativeRemoveSessionCookie();
                signalCookieOperationsComplete();
                return null;
            }
        }.execute();
    }

    /**
     * Removes all cookies.
     */
    public void removeAllCookie() {
        nativeRemoveAllCookie();
    }

    /**
     * Gets whether there are stored cookies.
     * @return True if there are stored cookies.
     */
    public synchronized boolean hasCookies() {
        return hasCookies(false);
    }

    /**
     * See {@link hasCookies()}.
     * @param privateBrowsing Whether to use the private browsing cookie jar
     * @hide Used by Browser, no intention to publish.
     */
    public synchronized boolean hasCookies(boolean privateBrowsing) {
        return nativeHasCookies(privateBrowsing);
    }

    /**
     * Removes all expired cookies.
     */
    public void removeExpiredCookie() {
        nativeRemoveExpiredCookie();
    }

    /**
     * Package level api, called from CookieSyncManager
     *
     * Flush all cookies managed by the Chrome HTTP stack to flash.
     */
    void flushCookieStore() {
        nativeFlushCookieStore();
    }

    /**
     * Gets whether the application's {@link WebView} instances send and accept
     * cookies for file scheme URLs.
     * @return True if {@link WebView} instances send and accept cookies for
     *         file scheme URLs
     */
    public static boolean allowFileSchemeCookies() {
        return nativeAcceptFileSchemeCookies();
    }

    /**
     * Sets whether the application's {@link WebView} instances should send and
     * accept cookies for file scheme URLs.
     * Use of cookies with file scheme URLs is potentially insecure. Do not use
     * this feature unless you can be sure that no unintentional sharing of
     * cookie data can take place.
     * <p>
     * Note that calls to this method will have no effect if made after a
     * {@link WebView} or CookieManager instance has been created.
     */
    public static void setAcceptFileSchemeCookies(boolean accept) {
        nativeSetAcceptFileSchemeCookies(accept);
    }

    // Native functions
    private static native boolean nativeAcceptCookie();
    private static native String nativeGetCookie(String url, boolean privateBrowsing);
    private static native boolean nativeHasCookies(boolean privateBrowsing);
    private static native void nativeRemoveAllCookie();
    private static native void nativeRemoveExpiredCookie();
    private static native void nativeRemoveSessionCookie();
    private static native void nativeSetAcceptCookie(boolean accept);
    private static native void nativeSetCookie(String url, String value, boolean privateBrowsing);
    private static native void nativeFlushCookieStore();
    private static native boolean nativeAcceptFileSchemeCookies();
    private static native void nativeSetAcceptFileSchemeCookies(boolean accept);
}
