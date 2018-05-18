/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.net.captiveportal;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.captiveportal.CaptivePortalProbeResult.Result;
import android.text.TextUtils;
import android.util.Log;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.regex.PatternSyntaxException;

/** @hide */
public abstract class CaptivePortalProbeSpec {
    public static final int HTTP_PROBE_SUCCESS_CODE = 204;
    public static final int HTTP_PROBE_FAILURE_CODE = 599;
    public static final String HTTP_LOCATION_HEADER_NAME = "location";

    private static final String TAG = CaptivePortalProbeSpec.class.getSimpleName();
    private static final String REGEX_SEPARATOR = "@@/@@";

    private final String mEncodedSpec;
    private final URL mUrl;

    CaptivePortalProbeSpec(String encodedSpec, URL url) {
        mEncodedSpec = encodedSpec;
        mUrl = url;
    }

    /**
     * Create a {@link CaptivePortalProbeSpec} that watches the HTTP status code on a URL.
     */
    @NonNull
    public static CaptivePortalProbeSpec makeStatusCheckSpec(@NonNull URL url) {
        return new StatusCodeProbeSpec(url.toString(), url);
    }

    /**
     * Parse a {@link CaptivePortalProbeSpec} from a {@link String}.
     */
    @NonNull
    public static CaptivePortalProbeSpec parseSpec(String spec) throws ParseException,
            MalformedURLException {
        if (TextUtils.isEmpty(spec)) {
            throw new ParseException("Empty probe spec", 0);
        }

        String[] splits = TextUtils.split(spec, REGEX_SEPARATOR);
        if (splits.length == 1) {
            // Status check specs are encoded with just the URL
            return makeStatusCheckSpec(new URL(spec));
        } else if (splits.length != 3) {
            throw new ParseException("Probe spec does not have 3 parts", 0);
        }

        return new RegexMatchProbeSpec(spec, new URL(splits[0]), splits[1], splits[2]);
    }

    /**
     * Parse a {@link CaptivePortalProbeSpec} from a {@link String}, or return a fallback spec
     * based on the status code of the provided URL if the spec cannot be parsed.
     */
    @NonNull
    public static CaptivePortalProbeSpec parseSpecOrUseStatusCodeFallback(
            @Nullable String spec, @NonNull URL url) {
        if (spec != null) {
            try {
                return parseSpec(spec);
            } catch (ParseException | MalformedURLException e) {
                Log.e(TAG, "Invalid probe spec: " + spec, e);
                // Fall through
            }
        }
        return makeStatusCheckSpec(url);
    }

    /**
     * Get the probe result from HTTP status and location header.
     */
    public CaptivePortalProbeResult getResult(int status, @Nullable String locationHeader) {
        return new CaptivePortalProbeResult(
                this, locationHeader, getResultInternal(status, locationHeader));
    }

    abstract Result getResultInternal(int status, @Nullable String locationHeader);

    public String getEncodedSpec() {
        return mEncodedSpec;
    }

    public URL getUrl() {
        return mUrl;
    }

    /**
     * Implementation of {@link CaptivePortalProbeSpec} that is based on the HTTP status code
     * of the fetched page (historical implementation).
     *
     * 204 status code is considered a success (not a portal). Other 200 to 399 status codes are
     * considered as a portal. The probe failed if the HTTP status code is outside of this range.
     */
    public static class StatusCodeProbeSpec extends CaptivePortalProbeSpec {
        StatusCodeProbeSpec(String spec, URL url) {
            super(spec, url);
        }

        @Override
        public Result getResultInternal(int status, String locationHeader) {
            if (status == HTTP_PROBE_SUCCESS_CODE) {
                return Result.SUCCESS;
            }
            if ((status >= 200) && (status <= 399)) {
                return Result.PORTAL;
            }
            return Result.FAILED;
        }
    }

    /**
     * Implementation of {@link CaptivePortalProbeSpec} that is based on configurable regular
     * expressions for the HTTP status code and location header (if any).
     * This probe cannot fail: it always returns {@link Result#SUCCESS} or {@link Result#PORTAL}.
     */
    public static class RegexMatchProbeSpec extends CaptivePortalProbeSpec {
        @Nullable
        final String mStatusRegex;
        @Nullable
        final String mLocationHeaderRegex;

        RegexMatchProbeSpec(String spec, URL url, String statusRegex, String locationHeaderRegex) {
            super(spec, url);
            mStatusRegex = statusRegex;
            mLocationHeaderRegex = locationHeaderRegex;
        }

        @Override
        public Result getResultInternal(int status, String locationHeader) {
            final boolean statusMatch = safeMatch(String.valueOf(status), mStatusRegex);
            final boolean locationMatch = safeMatch(locationHeader, mLocationHeaderRegex);
            return statusMatch && locationMatch ? Result.SUCCESS : Result.PORTAL;
        }

        private boolean safeMatch(@Nullable String value, @Nullable String pattern) {
            // No value is a match (no location header passes the location rule for non-redirects)
            if (pattern == null || value == null) {
                return true;
            }
            try {
                return value.matches(pattern);
            } catch (PatternSyntaxException e) {
                Log.e(TAG, "Invalid probe spec regex in " + getEncodedSpec(), e);
                // Avoid detecting a portal if the configuration is invalid
                return true;
            }
        }
    }
}
