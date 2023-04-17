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

package com.android.server.security.rkp;

import android.content.Context;
import android.hardware.security.keymint.IRemotelyProvisionedComponent;
import android.hardware.security.keymint.MacedPublicKey;
import android.hardware.security.keymint.RpcHardwareInfo;
import android.os.Binder;
import android.os.OutcomeReceiver;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ShellCommand;
import android.security.rkp.IGetRegistrationCallback;
import android.security.rkp.IRemoteProvisioning;
import android.security.rkp.service.RegistrationProxy;
import android.util.IndentingPrintWriter;
import android.util.Log;

import com.android.internal.util.DumpUtils;
import com.android.server.SystemService;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.Executor;

/**
 * Implements the remote provisioning system service. This service is backed by a mainline
 * module, allowing the underlying implementation to be updated. The code here is a thin
 * proxy for the code in android.security.rkp.service.
 *
 * @hide
 */
public class RemoteProvisioningService extends SystemService {
    public static final String TAG = "RemoteProvisionSysSvc";
    private static final Duration CREATE_REGISTRATION_TIMEOUT = Duration.ofSeconds(10);

    private final RemoteProvisioningImpl mBinderImpl = new RemoteProvisioningImpl();

    private static class RegistrationReceiver implements
            OutcomeReceiver<RegistrationProxy, Exception> {
        private final Executor mExecutor;
        private final IGetRegistrationCallback mCallback;

        RegistrationReceiver(Executor executor, IGetRegistrationCallback callback) {
            mExecutor = executor;
            mCallback = callback;
        }

        @Override
        public void onResult(RegistrationProxy registration) {
            try {
                mCallback.onSuccess(new RemoteProvisioningRegistration(registration, mExecutor));
            } catch (RemoteException e) {
                Log.e(TAG, "Error calling success callback " + mCallback.hashCode(), e);
            }
        }

        @Override
        public void onError(Exception error) {
            try {
                mCallback.onError(error.toString());
            } catch (RemoteException e) {
                Log.e(TAG, "Error calling error callback " + mCallback.hashCode(), e);
            }
        }
    }

    /** @hide */
    public RemoteProvisioningService(Context context) {
        super(context);
    }

    @Override
    public void onStart() {
        publishBinderService(Context.REMOTE_PROVISIONING_SERVICE, mBinderImpl);
    }

    private final class RemoteProvisioningImpl extends IRemoteProvisioning.Stub {
        @Override
        public void getRegistration(String irpcName, IGetRegistrationCallback callback)
                throws RemoteException {
            final int callerUid = Binder.getCallingUidOrThrow();
            final long callingIdentity = Binder.clearCallingIdentity();
            final Executor executor = getContext().getMainExecutor();
            try {
                Log.i(TAG, "getRegistration(" + irpcName + ")");
                RegistrationProxy.createAsync(getContext(), callerUid, irpcName,
                        CREATE_REGISTRATION_TIMEOUT, executor,
                        new RegistrationReceiver(executor, callback));
            } finally {
                Binder.restoreCallingIdentity(callingIdentity);
            }
        }

        @Override
        protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            if (!DumpUtils.checkDumpPermission(getContext(), TAG, pw)) return;
            IndentingPrintWriter ipw = new IndentingPrintWriter(pw);
            String irpcInterface = IRemotelyProvisionedComponent.DESCRIPTOR;
            for (String name : ServiceManager.getDeclaredInstances(irpcInterface)) {
                String irpc = irpcInterface + "/" + name;
                ipw.println(name);
                ipw.increaseIndent();
                dumpRpcInstance(ipw, irpc);
                ipw.decreaseIndent();
            }
        }

        private void dumpRpcInstance(PrintWriter pw, String irpc) {
            IRemotelyProvisionedComponent binder = IRemotelyProvisionedComponent.Stub.asInterface(
                    ServiceManager.waitForDeclaredService(irpc));
            if (binder == null) {
                pw.println("error: failed to find service");
                return;
            }

            try {
                RpcHardwareInfo info = binder.getHardwareInfo();
                pw.println("version=" + info.versionNumber);
                pw.println("rpcAuthorName=" + info.rpcAuthorName);
                if (info.versionNumber < 3) {
                    pw.println("supportedEekCurve=" + info.supportedEekCurve);
                }
                pw.println("uniqueId=" + info.uniqueId);
                pw.println("supportedNumKeysInCsr=" + info.supportedNumKeysInCsr);
            } catch (RemoteException e) {
                pw.println("error: failed to get hardware info");
            }
        }

        @Override
        public int handleShellCommand(ParcelFileDescriptor in, ParcelFileDescriptor out,
                ParcelFileDescriptor err, String[] args) {
            return new RemoteProvisioningShellCommand().exec(this, in.getFileDescriptor(),
                    out.getFileDescriptor(), err.getFileDescriptor(), args);
        }

        private static class RemoteProvisioningShellCommand extends ShellCommand {
            private static final String USAGE = "usage: cmd remote_provisioning SUBCOMMAND [ARGS]\n"
                    + "help\n"
                    + "  Show this message.\n"
                    + "dump\n"
                    + "  Dump service diagnostics.\n"
                    + "list [--min-version MIN_VERSION]\n"
                    + "  List the names of the IRemotelyProvisionedComponent instances.\n"
                    + "csr [--challenge CHALLENGE] NAME\n"
                    + "  Generate and print a base64-encoded CSR from the named \n"
                    + "  IRemotelyProvisionedComponent. A base64-encoded challenge can be\n"
                    + "  provided, or else it defaults to 16 zero bytes.\n";

            private static final int ERROR = -1;
            private static final int SUCCESS = 0;

            @Override
            public void onHelp() {
                getOutPrintWriter().print(USAGE);
            }

            @Override
            public int onCommand(String cmd) {
                if (cmd == null) {
                    return handleDefaultCommands(cmd);
                }
                try {
                    switch (cmd) {
                        case "list":
                            return list();
                        case "csr":
                            return csr();
                        default:
                            return handleDefaultCommands(cmd);
                    }
                } catch (Exception e) {
                    e.printStackTrace(getErrPrintWriter());
                    return ERROR;
                }
            }

            private int list() throws RemoteException {
                int minVersion = 1;
                String opt;
                while ((opt = getNextOption()) != null) {
                    switch (opt) {
                        case "--min-version":
                            minVersion = Integer.parseInt(getNextArgRequired());
                            break;
                        default:
                            getErrPrintWriter().println("error: unknown option");
                            return ERROR;
                    }
                }

                String irpcInterface = IRemotelyProvisionedComponent.DESCRIPTOR;
                for (String name : ServiceManager.getDeclaredInstances(irpcInterface)) {
                    String irpc = irpcInterface + "/" + name;
                    IRemotelyProvisionedComponent binder =
                            IRemotelyProvisionedComponent.Stub.asInterface(
                                    ServiceManager.waitForDeclaredService(irpc));
                    if (binder == null) {
                        getErrPrintWriter().println("error: failed to find " + irpc);
                        return ERROR;
                    }

                    RpcHardwareInfo info = binder.getHardwareInfo();
                    if (info.versionNumber < minVersion) {
                        continue;
                    }
                    getOutPrintWriter().println(name);
                }
                return SUCCESS;
            }

            private int csr() throws RemoteException {
                byte[] challenge = new byte[16];
                String opt;
                while ((opt = getNextOption()) != null) {
                    switch (opt) {
                        case "--challenge":
                            challenge = Base64.getDecoder().decode(getNextArgRequired());
                            break;
                        default:
                            getErrPrintWriter().println("error: unknown option");
                            return ERROR;
                    }
                }
                String name = getNextArgRequired();

                String irpc = IRemotelyProvisionedComponent.DESCRIPTOR + "/" + name;
                IRemotelyProvisionedComponent binder =
                        IRemotelyProvisionedComponent.Stub.asInterface(
                                ServiceManager.waitForDeclaredService(irpc));
                if (binder == null) {
                    getErrPrintWriter().println("error: failed to find " + irpc);
                    return ERROR;
                }

                RpcHardwareInfo info = binder.getHardwareInfo();
                if (info.versionNumber < 3) {
                    getErrPrintWriter().println("error: only available from version 3");
                    return ERROR;
                }

                MacedPublicKey[] macedKeysToSign = new MacedPublicKey[] {};
                byte[] csrBytes = binder.generateCertificateRequestV2(macedKeysToSign, challenge);
                getOutPrintWriter().println(Base64.getEncoder().encodeToString(csrBytes));
                return SUCCESS;
            }
        }
    }
}
