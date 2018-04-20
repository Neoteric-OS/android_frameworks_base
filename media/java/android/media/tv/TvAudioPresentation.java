/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.media.tv;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.icu.util.ULocale;
import android.os.Parcel;
import android.os.Parcelable;
import android.media.AudioPresentation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The TvAudioPresentation class encapsulates the information that describes an audio presentation
 * which is available in next generation audio content.
 *
 * Used by {@link TvInputManager} {@link TvInputManager#getAudioPresentations()} and
 * {@link TvInputManager#setAudioPresentation(presentation)} to query
 * available presentations and to select one, respectively.
 *
 * A list of available audio presentations in a media source can be queried using
 * {@link TvInputManager#getAudioPresentations()}. This list can be presented to a user for
 * selection.
 * A AudioPresentation can be passed to an offloaded audio decoder via
 * {@link TvInputManager#setAudioPresentation(AudioPresentation presentation)} to request
 * decoding of the selected presentation. An audio stream may contain multiple presentations that
 * differ by language, accessibility, end point mastering and dialogue enhancement. An audio
 * presentation may also have a set of description labels in different languages to help the user
 * make an informed selection.
 */
public final class TvAudioPresentation implements Parcelable {

    private AudioPresentation mAudioPresentation = null;

    public TvAudioPresentation(@NonNull Builder builder) {
        super(builder);
        this.mAudioPresentation = builder.mAudioPresentation;
    }
    public static class Builder extends AudioPresentation.Builder {
        public @NonNull TvAudioPresentation build() {
            return new TvAudioPresentation(this);
        }
    }
    // TvAudioPresentation(int presentationId) {
    //     mAudioPresentation = new AudioPresentation.Builder(1).build();
    // }

    // @Override
    // public Builder(int presentationId) {
    //     super.Builder(presentationId);
    // }

    // protected TvAudioPresentation(@NonNull Builder builder) {
    //     super(builder);


    // }
    private TvAudioPresentation(Parcel in) {
        int presentationId = in.readInt();
        int programId = in.readInt();
        ULocale language = new ULocale(in.readString());
        int masteringIndication = in.readInt();
        boolean audioDescriptionAvailable = in.readInt() == 0 ? false : true;
        boolean spokenSubtitlesAvailable = in.readInt() == 0 ? false : true;
        boolean dialogueEnhancementAvailable = in.readInt() == 0 ? false : true;

        Map<ULocale, CharSequence> labels = new HashMap<ULocale, CharSequence>();
        for (int i = in.readInt(); i > 0; i--) {
            labels.put(new ULocale(in.readString()), in.readString());
        }
        mAudioPresentation = (new AudioPresentation.Builder(presentationId)
                .setProgramId(programId)
                .setLocale(language)
                .setLabels(labels)
                .setMasteringIndication(masteringIndication)
                .setHasAudioDescription(audioDescriptionAvailable)
                .setHasSpokenSubtitles(spokenSubtitlesAvailable)
                .setHasDialogueEnhancement(dialogueEnhancementAvailable)).build();
    }
    @Override
    public int describeContents() {
        return 0;
    }


    /**
     * Used to package this object into a {@link Parcel}.
     *
     * @param dest The {@link Parcel} to be written.
     * @param flags The flags used for parceling.
     */
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mAudioPresentation.getPresentationId());
        dest.writeInt(mAudioPresentation.getProgramId());
        dest.writeString(mAudioPresentation.getLocale().toLanguageTag());
        dest.writeInt(mAudioPresentation.getMasteringIndication());
        dest.writeInt(mAudioPresentation.hasAudioDescription() ? 1 : 0);
        dest.writeInt(mAudioPresentation.hasSpokenSubtitles() ? 1 : 0);
        dest.writeInt(mAudioPresentation.hasDialogueEnhancement() ? 1 : 0);

        dest.writeInt(mAudioPresentation.getLabels().size());
        for (Map.Entry<ULocale, String> entry : mAudioPresentation.getLabels().entrySet()) {
            dest.writeString(entry.getKey().toString());
            dest.writeString(entry.getValue());
        }
    }

    @NonNull
    public static final Parcelable.Creator<TvAudioPresentation> CREATOR =
        new Parcelable.Creator<TvAudioPresentation>() {
            @Override
            public TvAudioPresentation createFromParcel(@NonNull Parcel in) {
                return new TvAudioPresentation(in);
            }

            @Override
            public TvAudioPresentation[] newArray(int size) {
                return new TvAudioPresentation[size];
            }
        };
}
