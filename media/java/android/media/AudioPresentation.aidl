/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.media;

/*
 * The AudioPresentation class encapsulates the information that describes an audio presentation
 * which is available in next generation audio content.
 *
 * Used by {@link android.media.tv.TvInputManager}
 * {@link android.media.tv.TvInputManager#getAudioPresentations()} and
 * {@link android.media.tv.TvInputManager#selectAudioPresentation(int presentationId, int programId)}
 * to query available presentations and to select an audio presentation, respectively.
 *
 * A list of available audio presentations in a media source can be queried using
 * {@link android.media.tv.TvInputManager#getAudioPresentations()}. This list can be presented to a
 * user for selection.
 * A AudioPresentation information can be passed to an offloaded audio decoder via
 * {@link android.media.tv.TvInputManager#selectAudioPresentation(int presentationId, int programId)}
 * to request decoding of the selected presentation. An audio stream may contain multiple
 * presentations that differ by language, accessibility, end point mastering and dialogue
 * enhancement. An audio presentation may also have a set of description labels in different
 * languages to help the user make an informed selection.
 */

parcelable AudioPresentation;
