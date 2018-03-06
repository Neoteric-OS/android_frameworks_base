/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.iris;

import android.content.Context;
import android.hardware.iris.Iris;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.AtomicFile;
import android.util.Slog;
import android.util.Xml;

import com.android.internal.annotations.GuardedBy;

import libcore.io.IoUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Class managing the set of iris per user across device reboots.
 */
class IrisUserState {

    private static final String TAG = "IrisState";
    private static final String IRIS_FILE = "settings_iris.xml";

    private static final String TAG_IRISES = "irises";
    private static final String TAG_IRIS = "iris";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_GROUP_ID = "groupId";
    private static final String ATTR_IRIS_ID = "irisId";
    private static final String ATTR_DEVICE_ID = "deviceId";

    private final File mFile;

    @GuardedBy("this")
    private final ArrayList<Iris> mIrises = new ArrayList<Iris>();
    private final Context mCtx;

    public IrisUserState(Context ctx, int userId) {
        mFile = getFileForUser(userId);
        mCtx = ctx;
        synchronized (this) {
            readStateSyncLocked();
        }
    }

    public void addIris(int irisId, int groupId) {
        synchronized (this) {
            mIrises.add(new Iris(getUniqueName(), groupId, irisId, 0));
            scheduleWriteStateLocked();
        }
    }

    public void removeIris(int irisId) {
        synchronized (this) {
            for (int i = 0; i < mIrises.size(); i++) {
                if (mIrises.get(i).getIrisId() == irisId) {
                    mIrises.remove(i);
                    scheduleWriteStateLocked();
                    break;
                }
            }
        }
    }

    public void renameIris(int irisId, CharSequence name) {
        synchronized (this) {
            for (int i = 0; i < mIrises.size(); i++) {
                if (mIrises.get(i).getIrisId() == irisId) {
                    Iris old = mIrises.get(i);
                    mIrises.set(i, new Iris(name, old.getGroupId(), old.getIrisId(),
                            old.getDeviceId()));
                    scheduleWriteStateLocked();
                    break;
                }
            }
        }
    }

    public List<Iris> getIrises() {
        synchronized (this) {
            return getCopy(mIrises);
        }
    }

    /**
     * Finds a unique name for the given iris
     * @return unique name
     */
    private String getUniqueName() {
        int guess = 1;
        while (true) {
            // Not the most efficient algorithm in the world, but there shouldn't be more than 10
            String name = mCtx.getString(com.android.internal.R.string.iris_name_template,
                    guess);
            if (isUnique(name)) {
                return name;
            }
            guess++;
        }
    }

    private boolean isUnique(String name) {
        for (Iris iris : mIrises) {
            if (iris.getName().equals(name)) {
                return false;
            }
        }
        return true;
    }

    private static File getFileForUser(int userId) {
        return new File(Environment.getUserSystemDirectory(userId), IRIS_FILE);
    }

    private final Runnable mWriteStateRunnable = new Runnable() {
        @Override
        public void run() {
            doWriteState();
        }
    };

    private void scheduleWriteStateLocked() {
        AsyncTask.execute(mWriteStateRunnable);
    }

    private ArrayList<Iris> getCopy(ArrayList<Iris> array) {
        ArrayList<Iris> result = new ArrayList<Iris>(array.size());
        for (int i = 0; i < array.size(); i++) {
            Iris iris = array.get(i);
            result.add(new Iris(iris.getName(), iris.getGroupId(), iris.getIrisId(),
                    iris.getDeviceId()));
        }
        return result;
    }

    private void doWriteState() {
        AtomicFile destination = new AtomicFile(mFile);

        ArrayList<Iris> irises;

        synchronized (this) {
            irises = getCopy(mIrises);
        }

        FileOutputStream out = null;
        try {
            out = destination.startWrite();

            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(out, "utf-8");
            serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            serializer.startDocument(null, true);
            serializer.startTag(null, TAG_IRISES);

            final int count = irises.size();
            for (int i = 0; i < count; i++) {
                Iris iris = irises.get(i);
                serializer.startTag(null, TAG_IRIS);
                serializer.attribute(null, ATTR_IRIS_ID, Integer.toString(iris.getIrisId()));
                serializer.attribute(null, ATTR_NAME, iris.getName().toString());
                serializer.attribute(null, ATTR_GROUP_ID, Integer.toString(iris.getGroupId()));
                serializer.attribute(null, ATTR_DEVICE_ID, Long.toString(iris.getDeviceId()));
                serializer.endTag(null, TAG_IRIS);
            }

            serializer.endTag(null, TAG_IRISES);
            serializer.endDocument();
            destination.finishWrite(out);

            // Any error while writing is fatal.
        } catch (Throwable t) {
            Slog.wtf(TAG, "Failed to write settings, restoring backup", t);
            destination.failWrite(out);
            throw new IllegalStateException("Failed to write irises", t);
        } finally {
            IoUtils.closeQuietly(out);
        }
    }

    private void readStateSyncLocked() {
        FileInputStream in;
        if (!mFile.exists()) {
            return;
        }
        try {
            in = new FileInputStream(mFile);
        } catch (FileNotFoundException fnfe) {
            Slog.i(TAG, "No iris state");
            return;
        }
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(in, null);
            parseStateLocked(parser);

        } catch (XmlPullParserException | IOException e) {
            throw new IllegalStateException("Failed parsing settings file: "
                    + mFile , e);
        } finally {
            IoUtils.closeQuietly(in);
        }
    }

    private void parseStateLocked(XmlPullParser parser)
            throws IOException, XmlPullParserException {
        final int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            String tagName = parser.getName();
            if (tagName.equals(TAG_IRISES)) {
                parseIrisesLocked(parser);
            }
        }
    }

    private void parseIrisesLocked(XmlPullParser parser)
            throws IOException, XmlPullParserException {

        final int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            String tagName = parser.getName();
            if (tagName.equals(TAG_IRIS)) {
                String name = parser.getAttributeValue(null, ATTR_NAME);
                String groupId = parser.getAttributeValue(null, ATTR_GROUP_ID);
                String irisId = parser.getAttributeValue(null, ATTR_IRIS_ID);
                String deviceId = parser.getAttributeValue(null, ATTR_DEVICE_ID);
                mIrises.add(new Iris(name, Integer.parseInt(groupId),
                        Integer.parseInt(irisId), Integer.parseInt(deviceId)));
            }
        }
    }

}
