package com.android.server.locksettings;

import android.hardware.weaver.V1_0.IWeaver;
import android.hardware.weaver.V1_0.WeaverConfig;
import android.hardware.weaver.V1_0.WeaverReadResponse;
import android.hardware.weaver.V1_0.WeaverStatus;
import android.hidl.base.V1_0.DebugInfo;
import android.os.IHwBinder;
import android.os.IHwBinder.DeathRecipient;
import android.os.RemoteException;
import android.util.Pair;

import android.util.Log;

import java.util.Arrays;
import java.util.List;

public class MockWeaverService implements IWeaver {

    private static final int MAX_SLOTS = 8;
    private static final int KEY_LENGTH = 256 / 8;
    private static final int VALUE_LENGTH = 256 / 8;

    private Pair<List<Byte>, List<Byte>>[] slots = new Pair[MAX_SLOTS];
    @Override
    public void getConfig(getConfigCallback cb) throws RemoteException {
        WeaverConfig config = new WeaverConfig();
        config.keySize = KEY_LENGTH;
        config.valueSize = VALUE_LENGTH;
        config.slots = MAX_SLOTS;
        cb.onValues(WeaverStatus.OK, config);
    }

    @Override
    public int write(int slotId, List<Byte> key, List<Byte> value)
            throws RemoteException {
        if (slotId < 0 || slotId >= MAX_SLOTS) {
            throw new RuntimeException("Invalid slot id");
        }
        slots[slotId] = Pair.create(key, value);
        return WeaverStatus.OK;
    }

    @Override
    public void read(int slotId, List<Byte> key, readCallback cb) throws RemoteException {
        if (slotId < 0 || slotId >= MAX_SLOTS) {
            throw new RuntimeException("Invalid slot id");
        }

        WeaverReadResponse response = new WeaverReadResponse();
        if (key.equals(slots[slotId].first)) {
            response.value.addAll(slots[slotId].second);
            cb.onValues(WeaverStatus.OK, response);
        } else {
            cb.onValues(WeaverStatus.FAILED, response);
        }
    }

    @Override
    public IHwBinder asBinder() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> interfaceChain() throws RemoteException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String interfaceDescriptor() throws RemoteException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setHALInstrumentation() throws RemoteException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean linkToDeath(DeathRecipient recipient, long cookie) throws RemoteException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void ping() throws RemoteException {
        throw new UnsupportedOperationException();
    }

    @Override
    public DebugInfo getDebugInfo() throws RemoteException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void notifySyspropsChanged() throws RemoteException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean unlinkToDeath(DeathRecipient recipient) throws RemoteException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<byte[]> getHashChain() throws RemoteException {
        throw new UnsupportedOperationException();
    }
}
