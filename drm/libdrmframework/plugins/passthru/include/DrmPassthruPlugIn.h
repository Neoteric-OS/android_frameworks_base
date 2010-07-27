#ifndef __DRM_PASSTHRU_PLUGIN_H__
#define __DRM_PASSTHRU_PLUGIN_H__

#include <DrmEngineBase.h>

namespace android {

class DrmPassthruPlugIn : public DrmEngineBase {

public:
	DrmPassthruPlugIn();
	virtual ~DrmPassthruPlugIn();

protected:
	/**
	 * Get constraint information associated with input content
	 *
	 * @param[in] uniqueId Unique identifier for a session
	 * @param[in] path Path of the protected content 
	 * @param[in] action Actions defined such as, 
	 * 			Action::DEFAULT, Action::PLAY, etc
	 * @return DrmConstraints
	 *			key-value pairs of constraint are embedded in it
	 * @note
	 * 		In case of error, return NULL
	 */
	DrmConstraints* onGetConstraints(
					int uniqueId, 
					const String8* path,
					const int action);

	/**
	 * Initialize plug-in
	 *
 	 * @param[in] uniqueId Unique identifier for a session
	 * @return status_t
	 *			Returns the error code for this API
	 */
	status_t onInitialize(int uniqueId);

	/**
	 * Register a callback to be invoked when the caller required to 
	 * receive necessary information 
	 *
	 * @param[in] uniqueId Unique identifier for a session
	 * @param[in] infoListener Listener
	 * @return status_t
	 *			Returns the error code for this API
	 */
	status_t onSetOnInfoListener(
					int uniqueId,
					const IDrmEngine::OnInfoListener* infoListener);

	/**
	 * Terminate the plug-in
	 * and release resource bound to plug-in
	 *
 	 * @param[in] uniqueId Unique identifier for a session
	 * @return status_t
	 *			Returns the error code for this API
	 */
	status_t onTerminate(int uniqueId);

	/**
	 * Get whether the given content can be handled by this plugin or not 
	 *
	 * @param[in] uniqueId Unique identifier for a session
	 * @param[in] path Path the protected object
	 * @return bool
	 *			Returns true if this plugin can handle , false in case of not able to handle
	 */
	bool onCanHandle(
					int uniqueId,
					const String8& path);

	/**
	 * Executes given drm information based on its type 
	 * 
	 * @param[in] uniqueId Unique identifier for a session
	 * @param[in] drmInfo Information needs to be processed
	 * @return DrmInfoStatus 
	 *			instance as a result of processing given input
	 */
	DrmInfoStatus* onProcessDrmInfo(
					int uniqueId,
					const DrmInfo* drmInfo);

	/**
	 * Save DRM rights to specified rights path 
	 * and make association with content path
	 * 
	 * @param[in] uniqueId Unique identifier for a session
	 * @param[in] drmRights DrmRights to be saved
	 * @param[in] rightsPath File path where rights to be saved
	 * @param[in] contentPath File path where content was saved
	 */
	void onSaveRights(
					int uniqueId,
					const DrmRights& drmRights,
					const String8& rightsPath,
					const String8& contentPath);

	/**
	 * Retrieves necessary information for registration, unregistration or rights
	 * acquisition information.
	 * 
	 * @param[in] uniqueId Unique identifier for a session
	 * @param[in] drmInfoRequest Request information to retrieve drmInfo
	 * @return DrmInfo 
	 *			instance as a result of processing given input
	 */
	DrmInfo* onAcquireDrmInfo(
					int uniqueId,
					const DrmInfoRequest* drmInfoRequest);

	/**
	 * Retrieves the mime type embedded inside the original content
	 *  
	 * @param[in] uniqueId Unique identifier for a session
	 * @param[in] path Path of the protected content
	 * @return String8
	 * 			Returns mime-type of the original content, such as "video/mpeg"
	 */
	String8 onGetOriginalMimeType(
					int uniqueId,
					const String8& path);

	/**
	 * Retrieves the type of the protected object (content, rights, etc..)
	 * using specified path or mimetype. At least one parameter should be non null
	 * to retrieve DRM object type
	 *
	 * @param[in] uniqueId Unique identifier for a session
	 * @param[in] path Path of the content or null.
	 * @param[in] mimeType Mime type of the content or null.
	 * @return type of the DRM content, such as DrmObjectType::CONTENT, DrmObjectType::RIGHTS_OBJECT
	 */
	int onGetDrmObjectType(
					int uniqueId,
					const String8& path,
					const String8& mimeType);

	/**
	 * Check whether the given content has valid rights or not
	 *
	 * @param[in] uniqueId Unique identifier for a session
	 * @param[in] path Path of the protected content
	 * @param[in] action Action to perform (Action::DEFAULT, Action::PLAY, etc)
	 * @return the status of the rights for the protected content, such as RightsStatus::RIGHTS_VALID,
	 *					RightsStatus::RIGHTS_EXPIRED, etc.
	 */
	int onCheckRightsStatus(
					int uniqueId,
					const String8& path,
					int action);

	/**
	 * Consumes the rights for a content.
	 * If the reserve parameter is true the rights is reserved until the same
	 * application calls this api again with the reserve parameter set to false.
	 *
	 * @param[in] uniqueId Unique identifier for a session
	 * @param[in] decryptHandle Handle for the decryption session
	 * @param[in] action Action to perform. (Action::DEFAULT, Action::PLAY, etc)
	 * @param[in] reserve True if the rights should be reserved.
	 */
	void onConsumeRights(
					int uniqueId,
					DecryptHandle* decryptHandle,
					int action,
					bool reserve);

	/**
	 * Informs the DRM Engine about the playback actions performed on the DRM files.
	 *
	 * @param[in] uniqueId Unique identifier for a session
	 * @param[in] decryptHandle Handle for the decryption session
	 * @param[in] playbackStatus Playback action (Playback::START, Playback::STOP, Playback::PAUSE)
	 * @param[in] position Position in the file (in milliseconds) where the start occurs.
	 *					 Only valid together with Playback::START.
	 */
	void onSetPlaybackStatus(
					int uniqueId,
					DecryptHandle* decryptHandle,
					int playbackStatus,
					int position);

	/**
	 *  Validates whether an action on the DRM content is allowed or not.
	 *
	 * @param[in] uniqueId Unique identifier for a session
	 * @param[in] path Path of the protected content
	 * @param[in] action Action to validate (Action::PLAY, Action::TRANSFER, etc)
	 * @param[in] description Detailed description of the action
	 * @return true if the action is allowed.
	 */
	bool onValidateAction(
					int uniqueId,
					const String8& path,
					int action,
					const ActionDescription& description);

	/**
	 * Removes the rights associated with the given protected content
	 *
	 * @param[in] uniqueId Unique identifier for a session
	 * @param[in] path Path of the protected content
	 */
	void onRemoveRights(
					int uniqueId,
					const String8& path);
	
	/**
	 * Removes all the rights information of each plug-in associated with 
	 * DRM framework. Will be used in master reset
	 *
	 * @param[in] uniqueId Unique identifier for a session
	 */
	void onRemoveAllRights(int uniqueId);

	/**
	 * This API is for Forward Lock based DRM scheme.
	 * Each time the application tries to download a new DRM file 
	 * which needs to be converted, then the application has to 
	 * begin with calling this API.
	 *
	 * @param[in] uniqueId Unique identifier for a session
	 * @param[in] convertId Handle for the convert session
	 */
	void onOpenConvertSession(
					int uniqueId,
					int convertId);

	/**
	 * Accepts and converts the input data which is part of DRM file. 
	 * The resultant converted data and the status is returned in the DrmConvertedInfo 
	 * object. This method will be called each time there are new block 
	 * of data received by the application.
	 *
	 * @param[in] uniqueId Unique identifier for a session
	 * @param[in] convertId Handle for the convert session
	 * @param[in] inputData Input Data which need to be converted 
	 * @return Return object contains the status of the data conversion, 
	 * 			the output converted data and offset. In this case the
	 *			application will ignore the offset information.
	 */
	DrmConvertedStatus* onConvertData(
					int uniqueId,
					int convertId,
					const DrmBuffer* inputData);

	/**
	 * Informs the Drm Agent when there is no more data which need to be converted 
	 * or when an error occurs. Upon successful conversion of the complete data, 
	 * the agent will inform that where the header and body signature 
	 * should be added. This signature appending is needed to integrity
	 * protect the converted file.
	 *
	 * @param[in] uniqueId Unique identifier for a session
	 * @param[in] convertId Handle for the convert session
	 * @return Return object contains the status of the data conversion, 
	 *			the header and body signature data. It also informs 
	 *			the application on which offset these signature data 
	 *			should be appended.
	 */
	DrmConvertedStatus* onCloseConvertSession(
					int uniqueId,
					int convertId);

	/**
	 * Returns the information about the Drm Engine capabilities which includes
	 * supported MimeTypes and file suffixes.
	 * 
	 * @param[in] uniqueId Unique identifier for a session
	 * @return DrmSupportInfo 
	 *			instance which holds the capabilities of a plug-in
	 */
	DrmSupportInfo* onGetSupportInfo(int uniqueId);

	/**
	 * Open the decrypt session to decrypt the given protected content
	 *
	 * @param[in] fd file descriptor of the protected content to be decrypted
	 * @param[in] offset start position of the content
	 * @param[in] length the length of the protected content
	 * @return
	 *			Handle for the decryption session
	 */
	DecryptHandle* onOpenDecryptSession(
					int uniqueId,
					int fd,
					int offset,
					int length);

	/**
	 * Close the decrypt session for the given handle
	 *
	 * @param[in] uniqueId Unique identifier for a session
	 * @param[in] decryptHandle Handle for the decryption session
	 */
	void onCloseDecryptSession(
					int uniqueId,
					DecryptHandle* decryptHandle);

	/**
	 * Initialize decryption for the given unit of the protected content
	 *
	 * @param[in] uniqueId Unique identifier for a session
	 * @param[in] decryptHandle Handle for the decryption session
	 * @param[in] decryptUnitId ID Specifies decryption unit, such as track ID
	 * @param[in] headerInfo information for initializing decryption of this decrypUnit
	 */
	 void onInitializeDecryptUnit(
					int uniqueId,
					DecryptHandle* decryptHandle,
					int decryptUnitId,
					const DrmBuffer* headerInfo);

	/**
	 * Decrypt the protected content buffers for the given unit
	 * This method will be called any number of times, based on number of
	 * encrypted streams received from application.
	 *
	 * @param[in] uniqueId Unique identifier for a session
	 * @param[in] decryptHandle Handle for the decryption session
	 * @param[in] decryptUnitId ID Specifies decryption unit, such as track ID
	 * @param[in] encBuffer encrypted data block
	 * @param[out] decBuffer decrypted data block
	 * @return status_t
	 *			Returns the error code for this API
	 *			DRM_NO_ERROR for success, corresponding error code for failure.
	 */
	 status_t onDecrypt(
					int uniqueId,
					DecryptHandle* decryptHandle,
					int decryptUnitId,
					const DrmBuffer* encBuffer,
					DrmBuffer** decBuffer);

	/**
	 * Finalize decryption for the given unit of the protected content
	 *
	 * @param[in] uniqueId Unique identifier for a session
	 * @param[in] decryptHandle Handle for the decryption session
	 * @param[in] decryptUnitId ID Specifies decryption unit, such as track ID
	 */
	 void onFinalizeDecryptUnit(
					int uniqueId,
					DecryptHandle* decryptHandle,
					int decryptUnitId);

	/**
	 * Reads the specified number of bytes from an open DRM file.
	 *
	 * @param[in] uniqueId Unique identifier for a session
	 * @param[in] decryptHandle Handle for the decryption session
	 * @param[out] buffer Reference to the buffer that should receive the read data.
	 * @param[in] numBytes Number of bytes to read.
	 * @param[in] offset Offset with which to update the file position.
	 *
	 * @return Number of bytes read.
	 * @retval -1 Failure.
	 */
	ssize_t onPread(
					int uniqueId,
					DecryptHandle* decryptHandle,
					void* buffer,
					ssize_t numBytes,
					off_t offset);

	private:
		DecryptHandle* openDecryptSessionImpl();
		
};

};

#endif /* __DRM_PASSTHRU_PLUGIN_H__ */

