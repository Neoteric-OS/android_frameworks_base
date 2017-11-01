/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.telephony.ims.stub;

import android.os.RemoteException;
import android.util.Log;

import com.android.ims.ImsConfig;
import com.android.ims.ImsConfigListener;
import com.android.ims.internal.IImsConfig;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.ref.WeakReference;
import java.util.HashMap;


/**
 * Base implementation of ImsConfig, which implements stub versions of the methods
 * in the IImsConfig AIDL. Override the methods that your implementation of ImsConfig supports.
 *
 * DO NOT remove or change the existing APIs, only add new ones to this Base implementation or you
 * will break other implementations of ImsConfig maintained by other ImsServices.
 *
 * Provides APIs to get/set the IMS service feature/capability/parameters.
 * The config items include:
 * 1) Items provisioned by the operator.
 * 2) Items configured by user. Mainly service feature class.
 *
 * @hide
 */

public class ImsConfigImplBase {

    static final private String TAG = "ImsConfigImplBase";

    ImsConfigStub mImsConfigStub = new ImsConfigStub(this);

    /**
     * Gets the value for ims service/capabilities parameters from the provisioned
     * value storage. Synchronous blocking call.
     *
     * @param item, as defined in com.android.ims.ImsConfig#ConfigConstants.
     * @return value in Integer format.
     */
    public int getProvisionedValue(int item) throws RemoteException {
        return -1;
    }

    /**
     * Gets the value for ims service/capabilities parameters from the provisioned
     * value storage. Synchronous blocking call.
     *
     * @param item, as defined in com.android.ims.ImsConfig#ConfigConstants.
     * @return value in String format.
     */
    public String getProvisionedStringValue(int item) throws RemoteException {
        return null;
    }

    /**
     * Sets the value for IMS service/capabilities parameters by the operator device
     * management entity. It sets the config item value in the provisioned storage
     * from which the master value is derived. Synchronous blocking call.
     *
     * @param item, as defined in com.android.ims.ImsConfig#ConfigConstants.
     * @param value in Integer format.
     * @return as defined in com.android.ims.ImsConfig#OperationStatusConstants.
     */
    public int setProvisionedValue(int item, int value) throws RemoteException {
        return ImsConfig.OperationStatusConstants.FAILED;
    }

    /**
     * Sets the value for IMS service/capabilities parameters by the operator device
     * management entity. It sets the config item value in the provisioned storage
     * from which the master value is derived.  Synchronous blocking call.
     *
     * @param item as defined in com.android.ims.ImsConfig#ConfigConstants.
     * @param value in String format.
     * @return as defined in com.android.ims.ImsConfig#OperationStatusConstants.
     */
    public int setProvisionedStringValue(int item, String value) throws RemoteException {
        return ImsConfig.OperationStatusConstants.FAILED;
    }

    /**
     * Gets the value of the specified IMS feature item for specified network type.
     * This operation gets the feature config value from the master storage (i.e. final
     * value). Asynchronous non-blocking call.
     *
     * @param feature as defined in com.android.ims.ImsConfig#FeatureConstants.
     * @param network as defined in android.telephony.TelephonyManager#NETWORK_TYPE_XXX.
     * @param listener feature value returned asynchronously through listener.
     */
    public void getFeatureValue(int feature, int network, ImsConfigListener listener)
            throws RemoteException {
    }

    /**
     * Sets the value for IMS feature item for specified network type.
     * This operation stores the user setting in setting db from which master db
     * is derived.
     *
     * @param feature as defined in com.android.ims.ImsConfig#FeatureConstants.
     * @param network as defined in android.telephony.TelephonyManager#NETWORK_TYPE_XXX.
     * @param value as defined in com.android.ims.ImsConfig#FeatureValueConstants.
     * @param listener, provided if caller needs to be notified for set result.
     */
    public void setFeatureValue(int feature, int network, int value, ImsConfigListener listener)
            throws RemoteException {
    }

    /**
     * Gets the value for IMS VoLTE provisioned.
     * This should be the same as the operator provisioned value if applies.
     */
    public boolean getVolteProvisioned() throws RemoteException {
        return false;
    }

    /**
     * Gets the value for IMS feature item video quality.
     *
     * @param listener Video quality value returned asynchronously through listener.
     */
    public void getVideoQuality(ImsConfigListener listener) throws RemoteException {
    }

    /**
     * Sets the value for IMS feature item video quality.
     *
     * @param quality, defines the value of video quality.
     * @param listener, provided if caller needs to be notified for set result.
     */
    public void setVideoQuality(int quality, ImsConfigListener listener) throws RemoteException {
    }

    public IImsConfig getIImsConfig() { return mImsConfigStub; }

    /**
     * Implements stub versions of the methods in the IImsConfig AIDL. It's a wrapper class of
     * ImsConfigImplBase which caches provisioned values from ImsConfigImpl layer so that it doesn't
     * always query it from modem.
     *
     * Provides APIs to get/set the IMS service feature/capability/parameters.
     * The config items include:
     * 1) Items provisioned by the operator.
     * 2) Items configured by user. Mainly service feature class.
     *
     * @hide
     */
    @VisibleForTesting
    static public class ImsConfigStub extends IImsConfig.Stub {
        WeakReference<ImsConfigImplBase> mImsConfigImplBaseWeakReference;
        private HashMap<Integer, Integer> mProvisionedIntValue = new HashMap<>();
        private HashMap<Integer, String> mProvisionedStringValue = new HashMap<>();

        @VisibleForTesting
        public ImsConfigStub(ImsConfigImplBase imsConfigImplBase) {
            mImsConfigImplBaseWeakReference =
                    new WeakReference<ImsConfigImplBase>(imsConfigImplBase);
        }

        /**
         * Gets the value for ims service/capabilities parameters. It first checks its local cache,
         * if missed, it will call ImsConfigImplBase.getProvisionedValue.
         * Synchronous blocking call.
         *
         * @param item, as defined in com.android.ims.ImsConfig#ConfigConstants.
         * @return value in Integer format.
         */
        @Override
        public synchronized int getProvisionedValue(int item) throws RemoteException {
            if (mProvisionedIntValue.containsKey(item)) {
                return mProvisionedIntValue.get(item);
            } else {
                int retVal = getImsConfigImplBase().getProvisionedValue(item);
                if (retVal != ImsConfig.OperationStatusConstants.UNKNOWN) {
                    mProvisionedIntValue.put(item, retVal);
                }
                return retVal;
            }
        }

        /**
         * Gets the value for ims service/capabilities parameters. It first checks its local cache,
         * if missed, it will call #ImsConfigImplBase.getProvisionedValue.
         * Synchronous blocking call.
         *
         * @param item, as defined in com.android.ims.ImsConfig#ConfigConstants.
         * @return value in String format.
         */
        @Override
        public synchronized String getProvisionedStringValue(int item) throws RemoteException {
            if (mProvisionedIntValue.containsKey(item)) {
                return mProvisionedStringValue.get(item);
            } else {
                String retVal = getImsConfigImplBase().getProvisionedStringValue(item);
                if (retVal != null) {
                    mProvisionedStringValue.put(item, retVal);
                }
                return retVal;
            }
        }

        /**
         * Sets the value for IMS service/capabilities parameters by the operator device
         * management entity. It sets the config item value in the provisioned storage
         * from which the master value is derived, and write it into local cache.
         * Synchronous blocking call.
         *
         * @param item, as defined in com.android.ims.ImsConfig#ConfigConstants.
         * @param value in Integer format.
         * @return as defined in com.android.ims.ImsConfig#OperationStatusConstants.
         */
        @Override
        public synchronized int setProvisionedValue(int item, int value) throws RemoteException {
            mProvisionedIntValue.remove(item);
            int retVal = getImsConfigImplBase().setProvisionedValue(item, value);
            if (retVal == ImsConfig.OperationStatusConstants.SUCCESS) {
                mProvisionedIntValue.put(item, value);
            } else {
                Log.d(TAG, "Set provision value of " + item +
                        " to " + value + " failed with error code " + retVal);
            }

            return retVal;
        }

        /**
         * Sets the value for IMS service/capabilities parameters by the operator device
         * management entity. It sets the config item value in the provisioned storage
         * from which the master value is derived, and write it into local cache.
         * Synchronous blocking call.
         *
         * @param item as defined in com.android.ims.ImsConfig#ConfigConstants.
         * @param value in String format.
         * @return as defined in com.android.ims.ImsConfig#OperationStatusConstants.
         */
        @Override
        public synchronized int setProvisionedStringValue(int item, String value)
                throws RemoteException {
            mProvisionedStringValue.remove(item);
            int retVal = getImsConfigImplBase().setProvisionedStringValue(item, value);
            if (retVal == ImsConfig.OperationStatusConstants.SUCCESS) {
                mProvisionedStringValue.put(item, value);
            }

            return retVal;
        }

        /**
         * Wrapper function to call ImsConfigImplBase.getFeatureValue.
         */
        @Override
        public void getFeatureValue(int feature, int network, ImsConfigListener listener)
                throws RemoteException {
            getImsConfigImplBase().getFeatureValue(feature, network, listener);
        }

        /**
         * Wrapper function to call ImsConfigImplBase.setFeatureValue.
         */
        @Override
        public void setFeatureValue(int feature, int network, int value, ImsConfigListener listener)
                throws RemoteException {
            getImsConfigImplBase().setFeatureValue(feature, network, value, listener);
        }

        /**
         * Wrapper function to call ImsConfigImplBase.getVolteProvisioned.
         */
        @Override
        public boolean getVolteProvisioned() throws RemoteException {
            return getImsConfigImplBase().getVolteProvisioned();
        }

        /**
         * Wrapper function to call ImsConfigImplBase.getVideoQuality.
         */
        @Override
        public void getVideoQuality(ImsConfigListener listener) throws RemoteException {
            getImsConfigImplBase().getVideoQuality(listener);
        }

        /**
         * Wrapper function to call ImsConfigImplBase.setVideoQuality.
         */
        @Override
        public void setVideoQuality(int quality, ImsConfigListener listener)
                throws RemoteException {
            getImsConfigImplBase().setVideoQuality(quality, listener);
        }

        private ImsConfigImplBase getImsConfigImplBase() throws RemoteException {
            ImsConfigImplBase ref = mImsConfigImplBaseWeakReference.get();
            if (ref == null) {
                throw new RemoteException("Fail to get ImsConfigImpl");
            } else {
                return ref;
            }
        }
    }
}
