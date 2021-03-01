/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com.
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

package android.bluetooth;

import android.annotation.SystemApi;

/**
 * @hide
 */
@SystemApi
public class BluetoothMcpService {
    private BluetoothMcpService() {
        // not called
    }

    /**
     * Service features definition
     * @hide
     */
    public final static class ServiceFeature {
        private ServiceFeature() {
            // not called
        }
        // LS word is used for the characteristic support bits
        public static final long PLAYER_NAME = 0x00000001;
        public static final long PLAYER_ICON_OBJ_ID = 0x00000002;
        public static final long PLAYER_ICON_URL = 0x00000004;
        public static final long TRACK_CHANGED = 0x00000008;
        public static final long TRACK_TITLE = 0x00000010;
        public static final long TRACK_DURATION = 0x00000020;
        public static final long TRACK_POSITION = 0x00000040;
        public static final long PLAYBACK_SPEED = 0x00000080;
        public static final long SEEKING_SPEED = 0x00000100;
        public static final long CURRENT_TRACK_SEGMENT_OBJ_ID = 0x00000200;
        public static final long CURRENT_TRACK_OBJ_ID = 0x00000400;
        public static final long NEXT_TRACK_OBJ_ID = 0x00000800;
        public static final long CURRENT_GROUP_OBJ_ID = 0x00001000;
        public static final long PARENT_GROUP_OBJ_ID = 0x00002000;
        public static final long PLAYING_ORDER = 0x00004000;
        public static final long PLAYING_ORDER_SUPPORTED = 0x00008000;
        public static final long MEDIA_STATE = 0x00010000;
        public static final long MEDIA_CONTROL_POINT = 0x00020000;
        public static final long MEDIA_CONTROL_POINT_OPCODES_SUPPORTED = 0x00040000;
        public static final long SEARCH_RESULT_OBJ_ID = 0x00080000;
        public static final long SEARCH_CONTROL_POINT = 0x00100000;
        public static final long CONTENT_CONTROL_ID = 0x00200000;

        // MS word is used for the optional notification support bits
        public static final long PLAYER_NAME_NOTIFY = PLAYER_NAME << 32;
        public static final long TRACK_TITLE_NOTIFY = TRACK_TITLE << 32;
        public static final long TRACK_DURATION_NOTIFY = TRACK_DURATION << 32;
        public static final long TRACK_POSITION_NOTIFY = TRACK_POSITION << 32;
        public static final long PLAYBACK_SPEED_NOTIFY = PLAYBACK_SPEED << 32;
        public static final long SEEKING_SPEED_NOTIFY = SEEKING_SPEED << 32;
        public static final long CURRENT_TRACK_OBJ_ID_NOTIFY = CURRENT_TRACK_OBJ_ID << 32;
        public static final long NEXT_TRACK_OBJ_ID_NOTIFY = NEXT_TRACK_OBJ_ID << 32;
        public static final long CURRENT_GROUP_OBJ_ID_NOTIFY = CURRENT_GROUP_OBJ_ID << 32;
        public static final long PARENT_GROUP_OBJ_ID_NOTIFY = PARENT_GROUP_OBJ_ID << 32;
        public static final long PLAYING_ORDER_NOTIFY = PLAYING_ORDER << 32;
        public static final long MEDIA_CONTROL_POINT_OPCODES_SUPPORTED_NOTIFY =
                MEDIA_CONTROL_POINT_OPCODES_SUPPORTED << 32;

        // This is set according to the Media Control Service Specification, v1.0, Section 3,
        // Table 3.1.
        public static final long ALL_MANDATORY_SERVICE_FEATURES = PLAYER_NAME | TRACK_CHANGED
                | TRACK_TITLE | TRACK_DURATION | TRACK_POSITION | MEDIA_STATE | CONTENT_CONTROL_ID;
    }

    /**
     * Service status definition
     * @hide
     */
    public final static class ServiceStatus {
        private ServiceStatus() {
            // not called
        }
        public static final int OK = 0x00;
        public static final int INVALID_FEATURE_FLAGS = 0x01;
        public static final int SERVICE_DIED = 0x02;
        public static final int SERVICE_UNAVAILABLE = 0x03;
    }

    /**
     * Player state fields definition
     * @hide
     */
    public final static class PlayerStateField {
        private PlayerStateField() {
            // not called
        }
        public static final int PLAYBACK_STATE = 0x00;
        public static final int PLAYBACK_SPEED = 0x01;
        public static final int SEEKING_SPEED = 0x02;
        public static final int PLAYING_ORDER = 0x03;
        public static final int TRACK_POSITION = 0x04;
        public static final int PLAYER_NAME = 0x05;
        public static final int ICON_URL = 0x06;
        public static final int ICON_OBJ_ID = 0x07;
        public static final int PLAYING_ORDER_SUPPORTED = 0x08;
        public static final int OPCODES_SUPPORTED = 0x09;
        public static final int TRACK_TITLE = 0x0A;
        public static final int TRACK_DURATION = 0x0B;
    }

    /**
     * Objects IDs definition
     * @hide
     */
    public final static class ObjectIds {
        private ObjectIds() {
            // not called
        }
        public static final int PLAYER_ICON_OBJ_ID = (int) ServiceFeature.PLAYER_ICON_OBJ_ID;
        public static final int CURRENT_TRACK_SEGMENT_OBJ_ID =
                (int) ServiceFeature.CURRENT_TRACK_SEGMENT_OBJ_ID;
        public static final int CURRENT_TRACK_OBJ_ID = (int) ServiceFeature.CURRENT_TRACK_OBJ_ID;
        public static final int NEXT_TRACK_OBJ_ID = (int) ServiceFeature.NEXT_TRACK_OBJ_ID;
        public static final int CURRENT_GROUP_OBJ_ID = (int) ServiceFeature.CURRENT_GROUP_OBJ_ID;
        public static final int PARENT_GROUP_OBJ_ID = (int) ServiceFeature.PARENT_GROUP_OBJ_ID;
        public static final int SEARCH_RESULT_OBJ_ID = (int) ServiceFeature.SEARCH_RESULT_OBJ_ID;

        public static int GetMatchingServiceFeature(int object_id) { return object_id; }
    }

    /**
     * Track position unavailable definition
     * @hide
     */
    public static final long TRACK_POSITION_UNAVAILABLE = -1L;

    /**
     * Track duration unavailable definition
     * @hide
     */
    public static final long TRACK_DURATION_UNAVAILABLE = -1L;

    /**
     * Playback states definition
     * @hide
     */
    public final static class PlaybackState {
        private PlaybackState() {
            // not called
        }
        public static final int INACTIVE = 0x00;
        public static final int PLAYING = 0x01;
        public static final int PAUSED = 0x02;
        public static final int SEEKING = 0x03;

        public static final int STATE_MIN = INACTIVE;
        public static final int STATE_MAX = SEEKING;
    }

    /**
     * Playing order definition
     * @hide
     */
    public final static class PlayingOrder {
        private PlayingOrder() {
            // not called
        }
        public static final int SINGLE_ONCE = 0x01;
        public static final int SINGLE_REPEAT = 0x02;
        public static final int IN_ORDER_ONCE = 0x03;
        public static final int IN_ORDER_REPEAT = 0x04;
        public static final int OLDEST_ONCE = 0x05;
        public static final int OLDEST_REPEAT = 0x06;
        public static final int NEWEST_ONCE = 0x07;
        public static final int NEWEST_REPEAT = 0x08;
        public static final int SHUFFLE_ONCE = 0x09;
        public static final int SHUFFLE_REPEAT = 0x0A;
    }

    /**
     * Supported playing order definition
     * @hide
     */
    public final static class SupportedPlayingOrder {
        private SupportedPlayingOrder() {
            // not called
        }
        public static final int SINGLE_ONCE = 0x0001;
        public static final int SINGLE_REPEAT = 0x0002;
        public static final int IN_ORDER_ONCE = 0x0004;
        public static final int IN_ORDER_REPEAT = 0x0008;
        public static final int OLDEST_ONCE = 0x0010;
        public static final int OLDEST_REPEAT = 0x0020;
        public static final int NEWEST_ONCE = 0x0040;
        public static final int NEWEST_REPEAT = 0x0080;
        public static final int SHUFFLE_ONCE = 0x0100;
        public static final int SHUFFLE_REPEAT = 0x0200;
    }
}
