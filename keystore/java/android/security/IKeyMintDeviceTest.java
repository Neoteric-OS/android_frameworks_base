//package android.security;
//
//import android.hardware.security.keymint.IKeyMintDevice;
//import android.hardware.security.keymint.SecurityLevel;
//import android.os.ServiceManager;
//import android.os.SystemService;
//import android.security.keystore.IKeyMintDeviceTest;
//import android.security.maintenance.IKeystoreMaintenance;
//
//public class IKeyMintDeviceTestService extends IKeyMintDeviceTest.Stub {
//
//    private static final String TAG = "IKeyMintDeviceTest";
//    private static IKeyMintDevice mKeyMintDevice;
//
//    private static IKeymintDeviceTest getService() {
//        return IKeymintDeviceTest.Stub.asInterface(
//                ServiceManager.checkService("android.security.keystore"));
//    }
//
//    public void IKeyMintDeviceTest(IKeyMintDevice keyMintDevice) {
//        this.mKeyMintDevice = keyMintDevice;
//    }
//
//    @Override
//    public IKeyMintDevice getKeyMintDevice(SecurityLevel securityLevel) {
//        return new AndroidKeyMintTestDevice(mKeyMintDevice).getKeyMintDevice();
//    }
//}
