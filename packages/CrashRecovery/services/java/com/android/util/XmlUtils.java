/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.util;


import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import android.annotation.NonNull;
import android.system.Os;
import android.system.ErrnoException;

import com.android.modules.utils.TypedXmlPullParser;

import libcore.util.XmlObjectFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @hide
 */
public class XmlUtils {

  private static final String STRING_ARRAY_SEPARATOR = ":";

  //@UnsupportedAppUsage
  public static final void beginDocument(XmlPullParser parser, String firstElementName) throws XmlPullParserException, IOException
  {
    int type;
    while ((type=parser.next()) != parser.START_TAG
        && type != parser.END_DOCUMENT) {
      ;
    }

    if (type != parser.START_TAG) {
      throw new XmlPullParserException("No start tag found");
    }

    if (!parser.getName().equals(firstElementName)) {
      throw new XmlPullParserException("Unexpected start tag: found " + parser.getName() +
          ", expected " + firstElementName);
    }
  }

  public static boolean nextElementWithin(XmlPullParser parser, int outerDepth)
      throws IOException, XmlPullParserException {
    for (;;) {
      int type = parser.next();
      if (type == XmlPullParser.END_DOCUMENT
          || (type == XmlPullParser.END_TAG && parser.getDepth() == outerDepth)) {
        return false;
      }
      if (type == XmlPullParser.START_TAG
          && parser.getDepth() == outerDepth + 1) {
        return true;
      }
    }
  }

  private static XmlPullParser newPullParser() {
    try {
      XmlPullParser parser = XmlObjectFactory.newXmlPullParser();
      parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, true);
      parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
      return parser;
    } catch (XmlPullParserException e) {
      throw new AssertionError();
    }
  }
  public static @NonNull TypedXmlPullParser resolvePullParser(@NonNull InputStream in)
      throws IOException {
    final byte[] magic = new byte[4];
    if (in instanceof FileInputStream) {
      try {
        Os.pread(((FileInputStream) in).getFD(), magic, 0, magic.length, 0);
      } catch (ErrnoException e) {
        throw e.rethrowAsIOException();
      }
    } else {
      if (!in.markSupported()) {
        in = new BufferedInputStream(in);
      }
      in.mark(8);
      in.read(magic);
      in.reset();
    }

    final TypedXmlPullParser xml;
    xml = (TypedXmlPullParser) newPullParser();
    try {
      xml.setInput(in, "UTF_8");
    } catch (XmlPullParserException e) {
      throw new IOException(e);
    }
    return xml;
  }

}