/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.hardware.security.keymint.IRemotelyProvisionedComponent;
import android.hardware.security.keymint.MacedPublicKey;
import android.hardware.security.keymint.RpcHardwareInfo;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ShellCommand;
import android.util.IndentingPrintWriter;

import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.util.Base64;

class RemoteProvisioningShellCommand extends ShellCommand {
    private static final String USAGE = "usage: cmd remote_provisioning SUBCOMMAND [ARGS]\n"
            + "help\n"
            + "  Show this message.\n"
            + "dump\n"
            + "  Dump service diagnostics.\n"
            + "list [--min-version MIN_VERSION]\n"
            + "  List the names of the IRemotelyProvisionedComponent instances.\n"
            + "csr [--challenge CHALLENGE] NAME\n"
            + "  Generate and print a base64-encoded CSR from the named\n"
            + "  IRemotelyProvisionedComponent. A base64-encoded challenge can be provided,\n"
            + "  or else it defaults to an empty challenge.\n";

    private static final int ERROR = -1;
    private static final int SUCCESS = 0;

    private final Injector mInjector;

    RemoteProvisioningShellCommand() {
        this(new Injector());
    }

    @VisibleForTesting
    RemoteProvisioningShellCommand(Injector injector) {
        mInjector = injector;
    }

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

    void dump(PrintWriter pw) {
        try {
            IndentingPrintWriter ipw = new IndentingPrintWriter(pw);
            for (String name : mInjector.getIrpcNames()) {
                ipw.println(name + ":");
                ipw.increaseIndent();
                dumpRpcInstance(ipw, name);
                ipw.decreaseIndent();
            }
        } catch (Exception e) {
            e.printStackTrace(pw);
        }
    }

    private void dumpRpcInstance(PrintWriter pw, String name) throws RemoteException {
        RpcHardwareInfo info = mInjector.getIrpcBinder(name).getHardwareInfo();
        pw.println("versionNumber=" + info.versionNumber);
        pw.println("rpcAuthorName=" + info.rpcAuthorName);
        if (info.versionNumber < 3) {
            pw.println("supportedEekCurve=" + info.supportedEekCurve);
        }
        pw.println("uniqueId=" + info.uniqueId);
        pw.println("supportedNumKeysInCsr=" + info.supportedNumKeysInCsr);
    }

    private int list() throws RemoteException {
        for (String name : mInjector.getIrpcNames()) {
            getOutPrintWriter().println(name);
        }
        return SUCCESS;
    }

    private int csr() throws RemoteException {
        byte[] challenge = {};
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

        IRemotelyProvisionedComponent binder = mInjector.getIrpcBinder(name);
        RpcHardwareInfo info = binder.getHardwareInfo();
        if (info.versionNumber < 3) {
            // TODO: call the V1 method
            getErrPrintWriter().println("error: only available from version 3");
            return ERROR;
        }

        MacedPublicKey[] macedKeysToSign = new MacedPublicKey[] {};
        byte[] csrBytes = binder.generateCertificateRequestV2(macedKeysToSign, challenge);
        getOutPrintWriter().println(Base64.getEncoder().encodeToString(csrBytes));
        return SUCCESS;
    }

    @VisibleForTesting
    static class Injector {
        String[] getIrpcNames() {
            return ServiceManager.getDeclaredInstances(IRemotelyProvisionedComponent.DESCRIPTOR);
        }

        IRemotelyProvisionedComponent getIrpcBinder(String name) {
            String irpc = IRemotelyProvisionedComponent.DESCRIPTOR + "/" + name;
            IRemotelyProvisionedComponent binder =
                    IRemotelyProvisionedComponent.Stub.asInterface(
                            ServiceManager.waitForDeclaredService(irpc));
            if (binder == null) {
                throw new IllegalArgumentException("failed to find " + irpc);
            }
            return binder;
        }
    }
}
