/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.integrity;

import android.annotation.Nullable;
import android.content.integrity.AppInstallMetadata;
import android.content.integrity.Rule;
import android.os.Environment;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.integrity.model.RuleMetadata;
import com.android.server.integrity.parser.RandomAccessObject;
import com.android.server.integrity.parser.RuleBinaryParser;
import com.android.server.integrity.parser.RuleIndexRange;
import com.android.server.integrity.parser.RuleMetadataParser;
import com.android.server.integrity.parser.RuleParseException;
import com.android.server.integrity.parser.RuleParser;
import com.android.server.integrity.serializer.RuleBinarySerializer;
import com.android.server.integrity.serializer.RuleMetadataSerializer;
import com.android.server.integrity.serializer.RuleSerializeException;
import com.android.server.integrity.serializer.RuleSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Abstraction over the underlying storage of rules and other metadata. */
public class IntegrityFileManager {

    private final RuleParser mRuleParser;
    private final RuleSerializer mRuleSerializer;
    private final File mDataDir;

    /** Get the singleton instance of this class. */
    public static synchronized IntegrityFileManager getInstance() {
        return new IntegrityFileManager();
    }

    private IntegrityFileManager() {
        this(
                new RuleBinaryParser(),
                new RuleBinarySerializer(),
                Environment.getDataSystemDirectory());
    }

    @VisibleForTesting
    IntegrityFileManager(RuleParser ruleParser, RuleSerializer ruleSerializer, File dataDir) {
        mRuleParser = ruleParser;
        mRuleSerializer = ruleSerializer;
        mDataDir = dataDir;
    }

    /**
     * Deprecated. Used to return if the rules have been initialized.
     *
     * <p>Used to fail early if there are no rules (so we don't need to parse the apk at all).
     */
    public boolean initialized() {
        return true;
    }

    /** Deprecated. Used to write rules to persistent storage. */
    public void writeRules(String version, String ruleProvider, List<Rule> rules)
            throws IOException, RuleSerializeException {
    }

    /**
     * Deprecated. Used to read rules from persistent storage.
     *
     * @param appInstallMetadata information about the install used to select rules to read. If
     *     null, all rules will be read.
     */
    public List<Rule> readRules(@Nullable AppInstallMetadata appInstallMetadata)
            throws IOException, RuleParseException {
        return Collections.emptyList();
    }

    /**
     * Deprecated. Used to read the metadata of the current rules in storage.
     */
    @Nullable
    public RuleMetadata readMetadata() {
        return null;
    }
}
