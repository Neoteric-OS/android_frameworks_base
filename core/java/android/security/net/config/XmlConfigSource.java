package android.security.net.config;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.ArraySet;
import android.util.Base64;
import android.util.Pair;
import com.android.internal.util.XmlUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * {@link ConfigSource} based on an xml configuration file.
 *
 * @hide
 */
public class XmlConfigSource implements ConfigSource {
    private final Object mLock = new Object();
    private NetworkSecurityConfig mDefaultConfig;
    private Set<Pair<Domain, NetworkSecurityConfig>> mDomainMap;
    private boolean mInitialized = false;
    private Context mContext = null;
    private final int mResourceId;

    public XmlConfigSource(Context context, int resourceId) {
        mResourceId = resourceId;
        mContext = context;
    }

    public Set<Pair<Domain, NetworkSecurityConfig>> getPerDomainConfigs() {
        ensureInitialized();
        return mDomainMap;
    }

    public NetworkSecurityConfig getDefaultConfig() {
        ensureInitialized();
        return mDefaultConfig;
    }

    private void ensureInitialized() {
        synchronized (mLock) {
            if (mInitialized) {
                return;
            }
        }
        XmlResourceParser parser = null;
        try {
            parser = mContext.getResources().getXml(mResourceId);
            parseNetworkSecurityConfig(parser);
            mContext = null;
            mInitialized = true;
        } catch (Resources.NotFoundException | XmlPullParserException | IOException
                | ParserException e) {
            throw new RuntimeException("Failed to parse Xml configuration", e);
        } finally {
            if (parser != null) {
                parser.close();
            }
        }
    }

    private Pin parsePin(XmlResourceParser parser)
            throws IOException, XmlPullParserException, ParserException {
        final String digestAlgorithm = parser.getAttributeValue(null, "digest");
        if (!Pin.isValidDigestAlgorithm(digestAlgorithm)) {
            throw new ParserException(parser, "Invalid pin digest algorithm: " + digestAlgorithm);
        }
        if (parser.next() != XmlPullParser.TEXT) {
            throw new ParserException(parser, "Missing pin digest");
        }
        final String digest = parser.getText();
        byte[] decodedDigest = null;
        try {
            decodedDigest = Base64.decode(digest, 0);
        } catch (IllegalArgumentException e) {
            throw new ParserException(parser, "Invalid pin digest", e);
        }
        if (parser.next() != XmlPullParser.END_TAG) {
            throw new ParserException(parser, "pin contains additional elements");
        }
        return new Pin(digestAlgorithm, decodedDigest);
    }

    private PinSet parsePinSet(XmlResourceParser parser)
            throws IOException, XmlPullParserException, ParserException {
        final String expirationDate = parser.getAttributeValue(null, "expiration");
        long expirationMilis = Long.MAX_VALUE;
        if (expirationDate != null) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                sdf.setLenient(false);
                Date date = sdf.parse(expirationDate);
                if (date == null) {
                    throw new ParserException(parser, "Invalid expiration date in pin-set");
                }
                expirationMilis = date.getTime();
            } catch (ParseException e) {
                    throw new ParserException(parser, "Invalid expiration date in pin-set", e);
            }
        }

        int outerDepth = parser.getDepth();
        Set<Pin> pins = new ArraySet<Pin>();
        while (XmlUtils.nextElementWithin(parser, outerDepth)) {
            final String tagName = parser.getName();
            if (tagName.equals("pin")) {
                pins.add(parsePin(parser));
            } else {
                throw new ParserException(parser, "Unknown tag:" + tagName + " in pin-set");
            }
        }
        return new PinSet(pins, expirationMilis);
    }

    private Domain parseDomain(XmlResourceParser parser, Set<String> seenDomains)
            throws IOException, XmlPullParserException, ParserException {
        boolean includeSubdomains = parser.getAttributeBooleanValue(null, "includeSubdomains",
                false);
        if (parser.next() != XmlPullParser.TEXT) {
            throw new ParserException(parser, "Domain name missing");
        }
        final String domain = parser.getText();
        if (parser.next() != XmlPullParser.END_TAG) {
            throw new ParserException(parser, "domain contains additional elements");
        }
        if (seenDomains.contains(domain)) {
            throw new ParserException(parser, "Domain " + domain + " has already been specified");
        }
        seenDomains.add(domain);
        return new Domain(domain, includeSubdomains);
    }

    private CertificatesEntryRef parseCertificatesEntry(XmlResourceParser parser)
            throws IOException, XmlPullParserException, ParserException {
        boolean overridePins = parser.getAttributeBooleanValue(null, "overridePins", false);
        int sourceId = parser.getAttributeResourceValue(null, "src", -1);
        final String sourceString = parser.getAttributeValue(null, "src");
        CertificateSource source = null;
        if (sourceString == null) {
            throw new ParserException(parser, "certificates element missing src attribute");
        }
        if (sourceId != -1) {
            source = new ResourceCertificateSource(sourceId, mContext);
        } else if ("system".equals(sourceString)) {
            source = SystemCertificateSource.getInstance();
        } else if ("user".equals(sourceString)) {
            source = UserCertificateSource.getInstance();
        } else {
            throw new ParserException(parser, "Unknown certificates src. "
                    + "Should be one of system|user|@resourceVal");
        }
        XmlUtils.skipCurrentTag(parser);
        return new CertificatesEntryRef(source, overridePins);
    }

    private Collection<CertificatesEntryRef> parseTrustAnchors(XmlResourceParser parser)
            throws IOException, XmlPullParserException, ParserException {
        int outerDepth = parser.getDepth();
        Set<CertificatesEntryRef> anchors = new ArraySet<CertificatesEntryRef>();
        while (XmlUtils.nextElementWithin(parser, outerDepth)) {
            final String tagName = parser.getName();
            if (tagName.equals("certificates")) {
                anchors.add(parseCertificatesEntry(parser));
            } else {
                throw new ParserException(parser, "Unknown element:" + tagName);
            }
        }
        return anchors;
    }

    private Pair<NetworkSecurityConfig.Builder, Set<Domain>> parseConfigEntry(
            XmlResourceParser parser, Set<String> seenDomains, boolean global)
            throws IOException, XmlPullParserException, ParserException {
        NetworkSecurityConfig.Builder builder = new NetworkSecurityConfig.Builder();
        Set<Domain> domains = new ArraySet<Domain>();
        boolean seenPinSet = false;
        boolean seenTrustAnchors = false;
        int outerDepth = parser.getDepth();
        // Parse config attributes. Only set values that are present, config inheritence will
        // handle the rest.
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            final String name = parser.getAttributeName(i);
            if ("hstsEnforced".equals(name)) {
                builder.setHstsEnforced(
                        parser.getAttributeBooleanValue(i,
                            NetworkSecurityConfig.DEFAULT_HSTS_ENFORCED));
            } else if ("cleartextTrafficPermitted".equals(name)) {
                builder.setCleartextTrafficPermitted(
                        parser.getAttributeBooleanValue(i,
                            NetworkSecurityConfig.DEFAULT_CLEARTEXT_TRAFFIC_PERMITTED));
            } else {
                throw new ParserException(parser, "Unknown config attribute: " + name);
            }
        }
        // Parse the config elements.
        while (XmlUtils.nextElementWithin(parser, outerDepth)) {
            final String tagName = parser.getName();
            // TODO: Support nested domain-config entries.
            if ("domain".equals(tagName)) {
                if (global) {
                    throw new ParserException(parser, "domain element not allowed in base-config");
                }
                Domain domain = parseDomain(parser, seenDomains);
                domains.add(domain);
            } else if ("trust-anchors".equals(tagName)) {
                if (seenTrustAnchors) {
                    throw new ParserException(parser,
                            "Multiple trust-anchor elements not allowed");
                }
                builder.addCertificatesEntryRefs(parseTrustAnchors(parser));
                seenTrustAnchors = true;
            } else if ("pin-set".equals(tagName)) {
                if (seenPinSet) {
                    throw new ParserException(parser, "Multiple pin-set element not allowed");
                }
                if (global) {
                    throw new ParserException(parser,
                            "pin-set element not allowed in base-config");
                }
                builder.setPinSet(parsePinSet(parser));
                seenPinSet = true;
            } else {
                throw new ParserException(parser, "Unknown element: " + tagName);
            }
        }
        if (!global && domains.isEmpty()) {
            throw new ParserException(parser, "domain-config lacks domains");
        }
        return new Pair<NetworkSecurityConfig.Builder, Set<Domain>>(builder, domains);
    }

    private void parseNetworkSecurityConfig(XmlResourceParser parser)
            throws IOException, XmlPullParserException, ParserException {
        Set<String> seenDomains = new ArraySet<String>();
        List<Pair<NetworkSecurityConfig.Builder, Set<Domain>>> builders =
                new ArrayList<Pair<NetworkSecurityConfig.Builder, Set<Domain>>>();
        NetworkSecurityConfig.Builder baseConfigBuilder = null;
        boolean seenDebugOverrides = false;
        boolean seenBaseConfig = false;

        XmlUtils.beginDocument(parser, "network-security-config");
        int outerDepth = parser.getDepth();
        while (XmlUtils.nextElementWithin(parser, outerDepth)) {
            // TODO: support debug-override.
            if ("base-config".equals(parser.getName())) {
                if (seenBaseConfig) {
                    throw new ParserException(parser, "Only one base-config allowed");
                }
                seenBaseConfig = true;
                baseConfigBuilder =
                        parseConfigEntry(parser, seenDomains, true).first;
            } else if ("domain-config".equals(parser.getName())) {
                builders.add(parseConfigEntry(parser, seenDomains, false));
            } else {
                throw new ParserException(parser, "Unexpected element: " + parser.getName());
            }
        }

        // Use the platform default as the parent of the base config for any values not provided
        // there. If there is no base config use the platform default.
        if (baseConfigBuilder != null) {
            baseConfigBuilder.setParent(NetworkSecurityConfig.getDefaultBuilder());
        } else {
            baseConfigBuilder = NetworkSecurityConfig.getDefaultBuilder();
        }
        // Build the per-domain config mapping.
        Set<Pair<Domain, NetworkSecurityConfig>> configs =
                new ArraySet<Pair<Domain, NetworkSecurityConfig>>();

        for (Pair<NetworkSecurityConfig.Builder, Set<Domain>> entry : builders) {
            NetworkSecurityConfig.Builder builder = entry.first;
            Set<Domain> domains = entry.second;
            // Use the base-config for inhereting any unset values in the domain-config entry.
            builder.setParent(baseConfigBuilder);
            NetworkSecurityConfig config = builder.build();
            for (Domain domain : domains) {
                configs.add(new Pair<Domain, NetworkSecurityConfig>(domain, config));
            }
        }
        mDefaultConfig = baseConfigBuilder.build();
        mDomainMap = configs;
    }

    public class ParserException extends Exception {

        public ParserException(XmlPullParser parser, String message, Throwable cause) {
            super(message + " at: " + parser.getPositionDescription(), cause);
        }

        public ParserException(XmlPullParser parser, String message) {
            this(parser, message, null);
        }
    }
}
