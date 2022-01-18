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

package android.net;

import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import android.test.MoreAsserts;

import androidx.test.filters.SmallTest;

import junit.framework.TestCase;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class LocalSocketTest extends TestCase {

    private static final String ADDRESS_PREFIX = "com.android.net.LocalSocketTest";

    @SmallTest
    public void testBasic() throws Exception {
        LocalServerSocket ss;
        LocalSocket ls;
        LocalSocket ls1;

        ss = new LocalServerSocket("android.net.LocalSocketTest");

        ls = new LocalSocket();

        try {
            ls.connect(new LocalSocketAddress(null));
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // pass
        }

        try {
            ls.bind(new LocalSocketAddress(null));
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // pass
        }

        ls.connect(new LocalSocketAddress("android.net.LocalSocketTest"));

        ls1 = ss.accept();

        // Test trivial read and write
        ls.getOutputStream().write(42);

        assertEquals(42, ls1.getInputStream().read());

        // Test getting credentials
        Credentials c = ls1.getPeerCredentials();

        MoreAsserts.assertNotEqual(0, c.getPid());

        // Test sending and receiving file descriptors
        ls.setFileDescriptorsForSend(
                new FileDescriptor[]{FileDescriptor.in});

        ls.getOutputStream().write(42);

        assertEquals(42, ls1.getInputStream().read());

        FileDescriptor[] out = ls1.getAncillaryFileDescriptors();

        assertEquals(1, out.length);

        // Test multible byte write and available()
        ls1.getOutputStream().write(new byte[]{0, 1, 2, 3, 4, 5}, 1, 5);

        assertEquals(1, ls.getInputStream().read());
        assertEquals(4, ls.getInputStream().available());

        byte[] buffer = new byte[16];
        int countRead;

        countRead = ls.getInputStream().read(buffer, 1, 15);

        assertEquals(4, countRead);
        assertEquals(2, buffer[1]);
        assertEquals(3, buffer[2]);
        assertEquals(4, buffer[3]);
        assertEquals(5, buffer[4]);

        // Try various array-out-of-bound cases
        try {
            ls.getInputStream().read(buffer, 1, 16);
            fail("expected exception");
        } catch (ArrayIndexOutOfBoundsException ex) {
            // excpected
        }

        try {
            ls.getOutputStream().write(buffer, 1, 16);
            fail("expected exception");
        } catch (ArrayIndexOutOfBoundsException ex) {
            // excpected
        }

        try {
            ls.getOutputStream().write(buffer, -1, 15);
            fail("expected exception");
        } catch (ArrayIndexOutOfBoundsException ex) {
            // excpected
        }

        try {
            ls.getOutputStream().write(buffer, 0, -1);
            fail("expected exception");
        } catch (ArrayIndexOutOfBoundsException ex) {
            // excpected
        }

        try {
            ls.getInputStream().read(buffer, -1, 15);
            fail("expected exception");
        } catch (ArrayIndexOutOfBoundsException ex) {
            // excpected
        }

        try {
            ls.getInputStream().read(buffer, 0, -1);
            fail("expected exception");
        } catch (ArrayIndexOutOfBoundsException ex) {
            // excpected
        }

        // Try read of length 0
        ls.getOutputStream().write(42);
        countRead = ls1.getInputStream().read(buffer, 0, 0);
        assertEquals(0, countRead);
        assertEquals(42, ls1.getInputStream().read());

        ss.close();

        ls.close();

        // Try write on closed socket

        try {
            ls.getOutputStream().write(42);
            fail("expected exception");
        } catch (IOException ex) {
            // Expected
        }

        // Try read on closed socket

        try {
            ls.getInputStream().read();
            fail("expected exception");
        } catch (IOException ex) {
            // Expected
        }

        // Try write on socket whose peer has closed

        try {
            ls1.getOutputStream().write(42);
            fail("expected exception");
        } catch (IOException ex) {
            // Expected
        }

        // Try read on socket whose peer has closed

        assertEquals(-1, ls1.getInputStream().read());

        ls1.close();
    }

    public void testLocalConnections() throws IOException {
        String address = ADDRESS_PREFIX + "_testLocalConnections";
        // create client and server socket
        LocalServerSocket localServerSocket = new LocalServerSocket(address);
        LocalSocket clientSocket = new LocalSocket();

        // establish connection between client and server
        LocalSocketAddress locSockAddr = new LocalSocketAddress(address);
        assertFalse(clientSocket.isConnected());
        clientSocket.connect(locSockAddr);
        assertTrue(clientSocket.isConnected());

        LocalSocket serverSocket = localServerSocket.accept();
        assertTrue(serverSocket.isConnected());
        assertTrue(serverSocket.isBound());
        try {
            serverSocket.bind(localServerSocket.getLocalSocketAddress());
            fail("Cannot bind a LocalSocket from accept()");
        } catch (IOException expected) {
        }
        try {
            serverSocket.connect(locSockAddr);
            fail("Cannot connect a LocalSocket from accept()");
        } catch (IOException expected) {
        }

        Credentials credent = clientSocket.getPeerCredentials();
        assertTrue(0 != credent.getPid());

        // send data from client to server
        OutputStream clientOutStream = clientSocket.getOutputStream();
        clientOutStream.write(12);
        InputStream serverInStream = serverSocket.getInputStream();
        assertEquals(12, serverInStream.read());

        //send data from server to client
        OutputStream serverOutStream = serverSocket.getOutputStream();
        serverOutStream.write(3);
        InputStream clientInStream = clientSocket.getInputStream();
        assertEquals(3, clientInStream.read());

        // Test sending and receiving file descriptors
        clientSocket.setFileDescriptorsForSend(new FileDescriptor[]{FileDescriptor.in});
        clientOutStream.write(32);
        assertEquals(32, serverInStream.read());

        FileDescriptor[] out = serverSocket.getAncillaryFileDescriptors();
        assertEquals(1, out.length);
        FileDescriptor fd = clientSocket.getFileDescriptor();
        assertTrue(fd.valid());

        //shutdown input stream of client
        clientSocket.shutdownInput();
        assertEquals(-1, clientInStream.read());

        //shutdown output stream of client
        clientSocket.shutdownOutput();
        try {
            clientOutStream.write(10);
            fail("testLocalSocket shouldn't come to here");
        } catch (IOException e) {
            // expected
        }

        //shutdown input stream of server
        serverSocket.shutdownInput();
        assertEquals(-1, serverInStream.read());

        //shutdown output stream of server
        serverSocket.shutdownOutput();
        try {
            serverOutStream.write(10);
            fail("testLocalSocket shouldn't come to here");
        } catch (IOException e) {
            // expected
        }

        //close client socket
        clientSocket.close();
        try {
            clientInStream.read();
            fail("testLocalSocket shouldn't come to here");
        } catch (IOException e) {
            // expected
        }

        //close server socket
        serverSocket.close();
        try {
            serverInStream.read();
            fail("testLocalSocket shouldn't come to here");
        } catch (IOException e) {
            // expected
        }
    }

    public void testAccessors() throws IOException {
        String address = ADDRESS_PREFIX + "_testAccessors";
        LocalSocket socket = new LocalSocket();
        LocalSocketAddress addr = new LocalSocketAddress(address);

        assertFalse(socket.isBound());
        socket.bind(addr);
        assertTrue(socket.isBound());
        assertEquals(addr, socket.getLocalSocketAddress());

        String str = socket.toString();
        assertTrue(str.contains("impl:android.net.LocalSocketImpl"));

        socket.setReceiveBufferSize(1999);
        assertEquals(1999 << 1, socket.getReceiveBufferSize());

        socket.setSendBufferSize(3998);
        assertEquals(3998 << 1, socket.getSendBufferSize());

        assertEquals(0, socket.getSoTimeout());
        socket.setSoTimeout(1996);
        assertTrue(socket.getSoTimeout() > 0);

        try {
            socket.getRemoteSocketAddress();
            fail("testLocalSocketSecondary shouldn't come to here");
        } catch (UnsupportedOperationException e) {
            // expected
        }

        try {
            socket.isClosed();
            fail("testLocalSocketSecondary shouldn't come to here");
        } catch (UnsupportedOperationException e) {
            // expected
        }

        try {
            socket.isInputShutdown();
            fail("testLocalSocketSecondary shouldn't come to here");
        } catch (UnsupportedOperationException e) {
            // expected
        }

        try {
            socket.isOutputShutdown();
            fail("testLocalSocketSecondary shouldn't come to here");
        } catch (UnsupportedOperationException e) {
            // expected
        }

        try {
            socket.connect(addr, 2005);
            fail("testLocalSocketSecondary shouldn't come to here");
        } catch (UnsupportedOperationException e) {
            // expected
        }

        socket.close();
    }

    // http://b/31205169
    public void testSetSoTimeout_readTimeout() throws Exception {
        String address = ADDRESS_PREFIX + "_testSetSoTimeout_readTimeout";

        try (LocalSocketPair socketPair = LocalSocketPair.createConnectedSocketPair(address)) {
            final LocalSocket clientSocket = socketPair.mClientSocket;

            // Set the timeout in millis.
            int timeoutMillis = 1000;
            clientSocket.setSoTimeout(timeoutMillis);

            // Avoid blocking the test run if timeout doesn't happen by using a separate thread.
            Callable<Result> reader = () -> {
                try {
                    clientSocket.getInputStream().read();
                    return Result.noException("Did not block");
                } catch (IOException e) {
                    return Result.exception(e);
                }
            };
            // Allow the configured timeout, plus some slop.
            int allowedTime = timeoutMillis + 2000;
            Result result = runInSeparateThread(allowedTime, reader);

            // Check the message was a timeout, it's all we have to go on.
            String expectedMessage = Os.strerror(OsConstants.EAGAIN);
            result.assertThrewIOException(expectedMessage);
        }
    }

    // http://b/31205169
    public void testSetSoTimeout_writeTimeout() throws Exception {
        String address = ADDRESS_PREFIX + "_testSetSoTimeout_writeTimeout";

        try (LocalSocketPair socketPair = LocalSocketPair.createConnectedSocketPair(address)) {
            final LocalSocket clientSocket = socketPair.mClientSocket;

            // Set the timeout in millis.
            int timeoutMillis = 1000;
            clientSocket.setSoTimeout(timeoutMillis);

            // Set a small buffer size so we know we can flood it.
            clientSocket.setSendBufferSize(100);
            final int bufferSize = clientSocket.getSendBufferSize();

            // Avoid blocking the test run if timeout doesn't happen by using a separate thread.
            Callable<Result> writer = () -> {
                try {
                    byte[] toWrite = new byte[bufferSize * 2];
                    clientSocket.getOutputStream().write(toWrite);
                    return Result.noException("Did not block");
                } catch (IOException e) {
                    return Result.exception(e);
                }
            };
            // Allow the configured timeout, plus some slop.
            int allowedTime = timeoutMillis + 2000;

            Result result = runInSeparateThread(allowedTime, writer);

            // Check the message was a timeout, it's all we have to go on.
            String expectedMessage = Os.strerror(OsConstants.EAGAIN);
            result.assertThrewIOException(expectedMessage);
        }
    }

    public void testAvailable() throws Exception {
        String address = ADDRESS_PREFIX + "_testAvailable";

        try (LocalSocketPair socketPair = LocalSocketPair.createConnectedSocketPair(address)) {
            LocalSocket clientSocket = socketPair.mClientSocket;
            LocalSocket serverSocket = socketPair.mServerSocket.accept();

            OutputStream clientOutputStream = clientSocket.getOutputStream();
            InputStream serverInputStream = serverSocket.getInputStream();
            assertEquals(0, serverInputStream.available());

            byte[] buffer = new byte[50];
            clientOutputStream.write(buffer);
            assertEquals(50, serverInputStream.available());

            InputStream clientInputStream = clientSocket.getInputStream();
            OutputStream serverOutputStream = serverSocket.getOutputStream();
            assertEquals(0, clientInputStream.available());
            serverOutputStream.write(buffer);
            assertEquals(50, serverInputStream.available());

            serverSocket.close();
        }
    }

    // http://b/34095140
    public void testLocalSocketCreatedFromFileDescriptor() throws Exception {
        String address = ADDRESS_PREFIX + "_testLocalSocketCreatedFromFileDescriptor";

        // Establish connection between a local client and server to get a valid client socket file
        // descriptor.
        try (LocalSocketPair socketPair = LocalSocketPair.createConnectedSocketPair(address)) {
            // Extract the client FileDescriptor we can use.
            FileDescriptor fileDescriptor = socketPair.mClientSocket.getFileDescriptor();
            assertTrue(fileDescriptor.valid());

            ParcelFileDescriptor parcelFileDescriptor = ParcelFileDescriptor.dup(fileDescriptor);

            LocalSocket clientSocketCreatedFromFileDescriptor =
                    LocalSocket.createConnectedLocalSocket(parcelFileDescriptor);
            // Create the LocalSocket we want to test.
            assertTrue(clientSocketCreatedFromFileDescriptor.isConnected());
            assertTrue(clientSocketCreatedFromFileDescriptor.isBound());

            // Test the LocalSocket can be used for communication.
            LocalSocket serverSocket = socketPair.mServerSocket.accept();
            OutputStream clientOutputStream =
                    clientSocketCreatedFromFileDescriptor.getOutputStream();
            InputStream serverInputStream = serverSocket.getInputStream();

            clientOutputStream.write(12);
            assertEquals(12, serverInputStream.read());

            // Closing clientSocketCreatedFromFileDescriptor does not close the file descriptor.
            clientSocketCreatedFromFileDescriptor.close();
            assertTrue(fileDescriptor.valid());

            // .. while closing the LocalSocket that owned the file descriptor does.
            socketPair.mClientSocket.close();
            assertFalse(fileDescriptor.valid());
        }
    }

    public void testFlush() throws Exception {
        String address = ADDRESS_PREFIX + "_testFlush";

        try (LocalSocketPair socketPair = LocalSocketPair.createConnectedSocketPair(address)) {
            LocalSocket clientSocket = socketPair.mClientSocket;
            LocalSocket serverSocket = socketPair.mServerSocket.accept();

            OutputStream clientOutputStream = clientSocket.getOutputStream();
            InputStream serverInputStream = serverSocket.getInputStream();
            testFlushWorks(clientOutputStream, serverInputStream);

            OutputStream serverOutputStream = serverSocket.getOutputStream();
            InputStream clientInputStream = clientSocket.getInputStream();
            testFlushWorks(serverOutputStream, clientInputStream);

            serverSocket.close();
        }
    }

    private void testFlushWorks(OutputStream outputStream, InputStream inputStream)
            throws Exception {
        final int bytesToTransfer = 50;
        StreamReader inputStreamReader = new StreamReader(inputStream, bytesToTransfer);

        byte[] buffer = new byte[bytesToTransfer];
        outputStream.write(buffer);
        assertEquals(bytesToTransfer, inputStream.available());

        // Start consuming the data.
        inputStreamReader.start();

        // This doesn't actually flush any buffers, it just polls until the reader has read all the
        // bytes.
        outputStream.flush();

        inputStreamReader.waitForCompletion(5000);
        inputStreamReader.assertBytesRead(bytesToTransfer);
        assertEquals(0, inputStream.available());
    }

    private static class StreamReader extends Thread {
        private final InputStream mIs;
        private final int mExpectedByteCount;
        private final CountDownLatch mCompleteLatch = new CountDownLatch(1);

        private volatile Exception mException;
        private int mBytesRead;

        private StreamReader(InputStream is, int expectedByteCount) {
            this.mIs = is;
            this.mExpectedByteCount = expectedByteCount;
        }

        @Override
        public void run() {
            try {
                byte[] buffer = new byte[10];
                int readCount;
                while ((readCount = mIs.read(buffer)) >= 0) {
                    mBytesRead += readCount;
                    if (mBytesRead >= mExpectedByteCount) {
                        break;
                    }
                }
            } catch (IOException e) {
                mException = e;
            } finally {
                mCompleteLatch.countDown();
            }
        }

        public void waitForCompletion(long waitMillis) throws Exception {
            if (!mCompleteLatch.await(waitMillis, TimeUnit.MILLISECONDS)) {
                fail("Timeout waiting for completion");
            }
            if (mException != null) {
                throw new Exception("Read failed", mException);
            }
        }

        public void assertBytesRead(int expected) {
            assertEquals(expected, mBytesRead);
        }
    }

    private static class Result {
        private final String mType;
        private final Exception mException;

        private Result(String type, Exception e) {
            this.mType = type;
            this.mException = e;
        }

        static Result noException(String description) {
            return new Result(description, null);
        }

        static Result exception(Exception e) {
            return new Result(e.getClass().getName(), e);
        }

        void assertThrewIOException(String expectedMessage) {
            assertEquals("Unexpected result type", IOException.class.getName(), mType);
            assertEquals("Unexpected exception message", expectedMessage, mException.getMessage());
        }
    }

    private static Result runInSeparateThread(int allowedTime, final Callable<Result> callable)
            throws Exception {
        ExecutorService service = Executors.newSingleThreadScheduledExecutor();
        Future<Result> future = service.submit(callable);
        Result result = future.get(allowedTime, TimeUnit.MILLISECONDS);
        if (!future.isDone()) {
            fail("Worker thread appears blocked");
        }
        return result;
    }

    private static class LocalSocketPair implements AutoCloseable {
        static LocalSocketPair createConnectedSocketPair(String address) throws Exception {
            LocalServerSocket localServerSocket = new LocalServerSocket(address);
            final LocalSocket clientSocket = new LocalSocket();

            // Establish connection between client and server
            LocalSocketAddress locSockAddr = new LocalSocketAddress(address);
            clientSocket.connect(locSockAddr);
            assertTrue(clientSocket.isConnected());
            return new LocalSocketPair(localServerSocket, clientSocket);
        }

        final LocalServerSocket mServerSocket;
        final LocalSocket mClientSocket;

        LocalSocketPair(LocalServerSocket serverSocket, LocalSocket clientSocket) {
            this.mServerSocket = serverSocket;
            this.mClientSocket = clientSocket;
        }

        public void close() throws Exception {
            mServerSocket.close();
            mClientSocket.close();
        }
    }
}
