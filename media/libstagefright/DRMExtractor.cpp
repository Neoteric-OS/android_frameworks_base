/*
 * Copyright (C) 2009 The Android Open Source Project
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

#include "include/DRMExtractor.h"
#include "include/AMRExtractor.h"
#include "include/MP3Extractor.h"
#include "include/MPEG4Extractor.h"
#include "include/WAVExtractor.h"
#include "include/OggExtractor.h"

#include <arpa/inet.h>
#include <utils/String8.h>
#include <media/stagefright/Utils.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaDebug.h>

#include <drm/drm_framework_common.h>
#include <utils/Errors.h>


namespace android {

DrmManagerClient* gDrmManagerClient = NULL;

uint32_t readSize(off_t offset, sp<DataSource> DataSource, uint8_t *numOfBytes) {
    uint32_t size = 0;
    uint8_t data;
    bool nextByte = true;
    *numOfBytes = 0;

    while (nextByte) {
        if (DataSource->readAt(offset, &data, 1) < 1) {
            return 0;
        }
        offset ++;
        nextByte = (data >= 128) ? true : false;
        size = (size << 7) | (data & 0x7f); // Take last 7 bits
        (*numOfBytes) ++;
    }

    return size;
}

class DRMSource : public MediaSource {
public:
    DRMSource(const sp<MediaSource> &mediaSource,
            DecryptHandle* decryptHandle, int32_t trackId, DrmBuffer* ipmpBox);

    virtual status_t start(MetaData *params = NULL);
    virtual status_t stop();
    virtual sp<MetaData> getFormat();
    virtual status_t read(
            MediaBuffer **buffer, const ReadOptions *options = NULL);

protected:
    virtual ~DRMSource();

private:
    sp<MediaSource> mOriginalMediaSource;
    DecryptHandle* mDecryptHandle;
    size_t mTrackId;
    mutable Mutex mDRMLock;
    size_t mNALLengthSize;
    bool mWantsNALFragments;

    status_t convertMediaBufferToDrmBuffer(
            MediaBuffer *mediaBuffer, DrmBuffer **drmBuffer);
    status_t convertDrmBufferToMediaBuffer(
            DrmBuffer *drmBuffer, MediaBuffer **mediaBuffer);
};

////////////////////////////////////////////////////////////////////////////////

DRMSource::DRMSource(const sp<MediaSource> &mediaSource,
        DecryptHandle* decryptHandle, int32_t trackId, DrmBuffer* ipmpBox)
    : mOriginalMediaSource(mediaSource),
      mDecryptHandle(decryptHandle),
      mTrackId(trackId),
      mNALLengthSize(0),
      mWantsNALFragments(false) {
    gDrmManagerClient->initializeDecryptUnit(
            mDecryptHandle, trackId, ipmpBox);

    getFormat()->setInt32(kKeyIsDRM, 1);
    const char *mime;
    bool success = getFormat()->findCString(kKeyMIMEType, &mime);
    CHECK(success);

    if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_AVC)) {
        uint32_t type;
        const void *data;
        size_t size;
        CHECK(getFormat()->findData(kKeyAVCC, &type, &data, &size));

        const uint8_t *ptr = (const uint8_t *)data;

        CHECK(size >= 7);
        CHECK_EQ(ptr[0], 1);  // configurationVersion == 1

        // The number of bytes used to encode the length of a NAL unit.
        mNALLengthSize = 1 + (ptr[4] & 3);
    }
}

DRMSource::~DRMSource() {
    Mutex::Autolock autoLock(mDRMLock);
    gDrmManagerClient->finalizeDecryptUnit(mDecryptHandle, mTrackId);
}

status_t DRMSource::start(MetaData *params) {
    int32_t val;
    if (params && params->findInt32(kKeyWantsNALFragments, &val)
        && val != 0) {
        mWantsNALFragments = true;
    } else {
        mWantsNALFragments = false;
    }

   return mOriginalMediaSource->start(params);
}

status_t DRMSource::stop() {
    return mOriginalMediaSource->stop();
}

sp<MetaData> DRMSource::getFormat() {
    return mOriginalMediaSource->getFormat();
}

status_t DRMSource::read(MediaBuffer **buffer, const ReadOptions *options) {
    Mutex::Autolock autoLock(mDRMLock);
    status_t err;
    if ((err = mOriginalMediaSource->read(buffer, options)) != OK) {
        return err;
    }

    size_t len = (*buffer)->range_length();

    char *src = (char *)(*buffer)->data() + (*buffer)->range_offset();
    DrmBuffer* encryptedDrmBuffer = new DrmBuffer(src, len);

    DrmBuffer* decryptedDrmBuffer = new DrmBuffer();
    decryptedDrmBuffer->length = len;
    decryptedDrmBuffer->data = new char[len];

    if ((err = gDrmManagerClient->decrypt(mDecryptHandle, mTrackId,
            encryptedDrmBuffer, &decryptedDrmBuffer)) != DRM_NO_ERROR) {
        if (err == DRM_ERROR_LICENSE_EXPIRED) {
            return ERROR_NO_LICENSE;
        } else {
            return ERROR_IO;
        }
    }

    const char *mime;
    CHECK(getFormat()->findCString(kKeyMIMEType, &mime));

   if (!strncasecmp(mime, "video/", 6) && !mWantsNALFragments) {
        uint8_t *dstData = (uint8_t*)src;
        size_t srcOffset = 0;
        size_t dstOffset = 0;

        len = decryptedDrmBuffer->length;
        while (srcOffset < len) {
            CHECK(srcOffset + mNALLengthSize <= len);
            size_t nalLength;
            const uint8_t* data = (const uint8_t*)(&decryptedDrmBuffer->data[srcOffset]);

            switch (mNALLengthSize) {
                case 1:
                    nalLength = *data;
                case 2:
                    nalLength = U16_AT(data);
                case 3:
                    nalLength = ((size_t)data[0] << 16) | U16_AT(&data[1]);
                case 4:
                    nalLength = U32_AT(data);
            }

            srcOffset += mNALLengthSize;

            if (srcOffset + nalLength > len) {
                LOGE("err");
                return ERROR_MALFORMED;
            }

            if (nalLength == 0) {
                continue;
            }

            CHECK(dstOffset + 4 <= (*buffer)->size());

            dstData[dstOffset++] = 0;
            dstData[dstOffset++] = 0;
            dstData[dstOffset++] = 0;
            dstData[dstOffset++] = 1;
            memcpy(&dstData[dstOffset], &decryptedDrmBuffer->data[srcOffset], nalLength);
            srcOffset += nalLength;
            dstOffset += nalLength;
        }

        CHECK_EQ(srcOffset, len);
        (*buffer)->set_range((*buffer)->range_offset(), dstOffset);

    } else {
        memcpy(src, decryptedDrmBuffer->data, decryptedDrmBuffer->length);
        (*buffer)->set_range((*buffer)->range_offset(), decryptedDrmBuffer->length);
    }

    if (decryptedDrmBuffer->data) {
        delete [] decryptedDrmBuffer->data;
        decryptedDrmBuffer->data = NULL;
    }
    delete decryptedDrmBuffer;

    delete encryptedDrmBuffer;

    return OK;
}

////////////////////////////////////////////////////////////////////////////////

DRMExtractor::DRMExtractor(const sp<DataSource> &source, const char* mime)
    : mDataSource(source),
      mDecryptHandle(NULL),
      mFirstSINF(NULL) {
    mOriginalExtractor = MediaExtractor::Create(source, mime);

    DrmManagerClient *client;
    source->getDrmInfo(&mDecryptHandle, &client);
}

DRMExtractor::~DRMExtractor() {
    SINF *sinf = mFirstSINF;
    while (sinf) {
        SINF *next = sinf->next;
        delete sinf->IPMPData;
        delete sinf;
        sinf = next;
    }
    mFirstSINF = NULL;
}

size_t DRMExtractor::countTracks() {
    return mOriginalExtractor->countTracks();
}

sp<MediaSource> DRMExtractor::getTrack(size_t index) {
    sp<MediaSource> originalMediaSource = mOriginalExtractor->getTrack(index);

    int32_t trackID;
    getTrackMetaData(index, 0)->findInt32(kKeyTrackID, &trackID);

    DrmBuffer ipmpBox;
    ipmpBox.data = getSINF(trackID, &(ipmpBox.length));

    return new DRMSource(originalMediaSource, mDecryptHandle, trackID, &ipmpBox);
}

sp<MetaData> DRMExtractor::getTrackMetaData(size_t index, uint32_t flags) {
    return mOriginalExtractor->getTrackMetaData(index, flags);
}

sp<MetaData> DRMExtractor::getMetaData() {
    return mOriginalExtractor->getMetaData();
}

status_t DRMExtractor::parseSINF() {
    off_t offset = 0;
    uint64_t chunk_size;
    uint32_t chunk_type;
    off_t data_offset;
    uint32_t hdr[2];

    off_t fileSize = 0;
    if (OK != mDataSource->getSize(&fileSize)) {
        return ERROR_IO;
    }

    while (fileSize > 0)
    {
        if (mDataSource->readAt(offset, hdr, 8) < 8) {
            return ERROR_IO;
        }
        chunk_size = ntohl(hdr[0]);
        chunk_type = ntohl(hdr[1]);
        data_offset = offset + 8;
        fileSize -= 8;

        if (chunk_size == 1) {
            if (mDataSource->readAt(offset + 8, &chunk_size, 8) < 8) {
                return ERROR_IO;
            }
            fileSize -= 8;

            chunk_size = ntoh64(chunk_size);
            data_offset += 8;
        }

        if (FOURCC('m', 'd', 'a', 't') == chunk_type) {

            if (chunk_size < 8) {
                return ERROR_MALFORMED;
            }

            uint8_t updateIdTag;
            if (mDataSource->readAt(data_offset, &updateIdTag, 1) < 1) {
                return ERROR_IO;
            }
            fileSize -= 1;
            data_offset ++;

            if (0x01/*OBJECT_DESCRIPTOR_UPDATE_ID_TAG*/ == updateIdTag) {
                uint8_t numOfBytes;
                uint32_t size = readSize(data_offset, mDataSource, &numOfBytes);
                uint32_t classSize = size;
                data_offset += numOfBytes;

                while(size > 0) {
                    uint8_t descriptorTag;
                    if (mDataSource->readAt(data_offset, &descriptorTag, 1) < 1) {
                        return ERROR_IO;
                    }
                    data_offset ++;

                    if (0x11/*OBJECT_DESCRIPTOR_ID_TAG*/ != descriptorTag) {
                        return ERROR_MALFORMED;
                    }

                    uint8_t buffer[8];
                    //ObjectDescriptorID and ObjectDescriptor url flag
                    if (mDataSource->readAt(data_offset, buffer, 2) < 2) {
                        return ERROR_IO;
                    }
                    data_offset += 2;

                    if ((buffer[1] >> 5) & 0x0001) { //url flag is set
                        offset += chunk_size;
                        break;
                    }

                    if (mDataSource->readAt(data_offset, buffer, 8) < 8) {
                        return ERROR_IO;
                    }
                    data_offset += 8;

                    if ((buffer[1] != 0x0F/*ES_ID_REF_TAG*/)
                            || (buffer[5] != 0x0A/*IPMP_DESCRIPTOR_POINTER_ID_TAG*/)) {
                        return ERROR_MALFORMED;
                    }

                    SINF *sinf = new SINF;
                    sinf->trackID = U16_AT(&buffer[3]);
                    sinf->IPMPDescriptorID = buffer[7];
                    sinf->next = mFirstSINF;
                    mFirstSINF = sinf;

                    size -= (8 + 2 + 1);
                }
                fileSize -= classSize;

            } else {
                fileSize -= chunk_size;
                offset += chunk_size;
                break;
            }

            if (mDataSource->readAt(data_offset, &updateIdTag, 1) < 1) {
                return ERROR_IO;
            }
            fileSize -= 1;
            data_offset ++;

            if(0x05/*IPMP_DESCRIPTOR_UPDATE_ID_TAG*/ == updateIdTag) {

                uint8_t numOfBytes;
                uint32_t size = readSize(data_offset, mDataSource, &numOfBytes);
                uint32_t classSize = size;
                data_offset += numOfBytes;

                while (size > 0) {
                    uint8_t tag;
                    uint32_t dataLen;
                    if (mDataSource->readAt(data_offset, &tag, 1) < 1) {
                        return ERROR_IO;
                    }
                    data_offset ++;

                    if (tag == 0x0B/*IPMP_DESCRIPTOR_ID_TAG*/) {
                        uint8_t id;
                        dataLen = readSize(data_offset, mDataSource, &numOfBytes);
                        data_offset += numOfBytes;

                        if (mDataSource->readAt(data_offset, &id, 1) < 1) {
                            return ERROR_IO;
                        }
                        data_offset ++;

                        SINF *sinf = mFirstSINF;
                        while (sinf && (sinf->IPMPDescriptorID != id)) {
                            sinf = sinf->next;
                        }
                        if (sinf == NULL) {
                            return ERROR_MALFORMED;
                        }
                        sinf->len = dataLen - 3;
                        sinf->IPMPData = new char[sinf->len];
                        if (mDataSource->readAt(data_offset + 2, sinf->IPMPData, sinf->len) < sinf->len) {
                            return ERROR_IO;
                        }
                        data_offset += sinf->len;

                        size -= (dataLen + numOfBytes + 1);
                    }
                }
                fileSize -= classSize;
            }

            fileSize -= chunk_size;
            offset += chunk_size;
            break;
        } else {
            fileSize -= chunk_size;
            offset += chunk_size;
        }
    }
    return OK;
}

char *DRMExtractor::getSINF(int32_t index, int *len) {
    if (mFirstSINF == NULL) {
        if (parseSINF() != OK) {
            return NULL;
        }
    }

    SINF *sinf = mFirstSINF;
    while (sinf && (index != sinf->trackID)) {
        sinf = sinf->next;
    }

    if (sinf == NULL) {
        return NULL;
    }

    *len = sinf->len;
    return sinf->IPMPData;
}

bool SniffDRM(
    const sp<DataSource> &source, String8 *mimeType, float *confidence) {

    if (gDrmManagerClient == NULL) {
        gDrmManagerClient = new DrmManagerClient();
    }

    DecryptHandle *decryptHandle = source->DrmInitialization(gDrmManagerClient);
    if (decryptHandle != NULL) {
        *mimeType = decryptHandle->mimeType;

        *confidence = 10.0f;
        return true;
    }
    return false;
}
} //namespace android

