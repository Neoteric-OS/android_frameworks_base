/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.commands.input;

import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.IWindowManager;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

/**
 * Command that sends key events to the device, either by their keycode, or by
 * desired character output.
 */

public class Input {

    /**
     * Command-line entry point.
     *
     * @param args The command-line arguments
     */
    public static void main(String[] args) {
        (new Input()).run(args);
    }

    private void run(String[] args) {
        if (args.length < 1) {
            showUsage();
            return;
        }

        String command = args[0];

        if (command.equals("text")) {
            sendText(collectArguments(args, 1));
        } else if (command.equals("keyevent")) {
            sendKeyEvent(args[1]);
        } else if (command.equals("motionevent")) {
            System.err.println("Error: motionevent not yet supported.");
            return;
        }
        else {
            System.err.println("Error: Unknown command: " + command);
            showUsage();
            return;
        }
    }

    /**
     * Collect remaining arguments into a StringBuffer.
     *
     * @param args The argument array
     * @param from The index of the first argument which should be collected
     */
    private StringBuffer collectArguments(String[] args, int from) {
        StringBuffer buff = new StringBuffer();
        // copy all arguments starting from `from` into the buffer
        for (int i=from; i<args.length; i++) {
            if (buff.length() > 0)
                buff.append(' ');
            buff.append(args[i]);
        }
        return buff;
    }

    /**
     * Convert a text into key events and send them to the device.
     *
     * @param buff is a StringBuffer of characters to send to the device
     */
    private void sendText(StringBuffer buff) {
        boolean escapeFlag = false;
        for (int i=0; i<buff.length(); i++) {
            if (escapeFlag) {
                escapeFlag = false;
                if (buff.charAt(i) == 's') {
                    buff.setCharAt(i, ' ');
                    buff.deleteCharAt(--i);
                }
            } 
            if (buff.charAt(i) == '%') {
                escapeFlag = true;
            }
        }

        char[] chars = buff.toString().toCharArray();

        KeyCharacterMap mKeyCharacterMap = KeyCharacterMap.
            load(KeyCharacterMap.BUILT_IN_KEYBOARD);

        KeyEvent[] events = mKeyCharacterMap.getEvents(chars);

        for(int i = 0; i < events.length; i++) {
            KeyEvent event = events[i];
            Log.i("SendKeyEvent", Integer.toString(event.getKeyCode()));
            try {
                (IWindowManager.Stub
                    .asInterface(ServiceManager.getService("window")))
                    .injectKeyEvent(event, true);
            } catch (RemoteException e) {
                Log.i("Input", "DeadOjbectException");
            }
        }
    }

    /**
     * Send a single key event.
     *
     * @param event is a string representing the keycode of the key event you
     * want to execute.
     */
    private void sendKeyEvent(String event) {
        int eventCode = Integer.parseInt(event);
        long now = SystemClock.uptimeMillis();
        Log.i("SendKeyEvent", event);
        try {
            KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, eventCode, 0);
            KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, eventCode, 0);
            (IWindowManager.Stub
                .asInterface(ServiceManager.getService("window")))
                .injectKeyEvent(down, true);
            (IWindowManager.Stub
                .asInterface(ServiceManager.getService("window")))
                .injectKeyEvent(up, true);
        } catch (RemoteException e) {
            Log.i("Input", "DeadOjbectException");
        }
    }

    private void sendMotionEvent(long downTime, int action, float x, float y, 
            float pressure, float size) {
    }

    private void showUsage() {
        System.err.println("usage: input [text|keyevent]");
        System.err.println("       input text <string>");
        System.err.println("       input keyevent <event_code>");
    }
}
