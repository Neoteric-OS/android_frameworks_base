package android.security;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @hide
 */
public class ChunkedStreamer {
    public interface Consumer {
        public static class ConsumeResult {
            public int consumed;
            public byte[] output;
        }

        ConsumeResult consume(byte[] input);
    }

    private static final int DEFAULT_MAX_CHUNK_SIZE = 1024 * 1024;
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private final Consumer mConsumer;
    private final int mMaxChunkSize;

    private byte[] mBuffered;
    private int mBufferedOffset;
    private int mBufferedLength;

    public ChunkedStreamer(Consumer consumer) {
        this(consumer, DEFAULT_MAX_CHUNK_SIZE);
    }

    public ChunkedStreamer(Consumer consumer, int maxChunkSize) {
        mConsumer = consumer;
        mMaxChunkSize = maxChunkSize;
    }

    public byte[] stream(byte[] input, int inputOffset, int inputLength) {
        if (input.length == 0) {
            return EMPTY_BYTE_ARRAY;
        }

        ByteArrayOutputStream bufferedOutput = null;

        while (inputLength > 0) {
            byte[] chunk;
            int inputBytesInChunk;
            if ((mBufferedLength + inputLength) > mMaxChunkSize) {
                // Too much input for one chunk -- extract one max-sized chunk and feed it into the
                // Consumer.
                chunk = new byte[mMaxChunkSize];
                System.arraycopy(mBuffered, mBufferedOffset, chunk, 0, mBufferedLength);
                inputBytesInChunk = chunk.length - mBufferedLength;
                System.arraycopy(input, inputOffset, chunk, mBufferedLength, inputBytesInChunk);
            } else {
                // All of available input fits into one chunk.
                if ((mBufferedLength == 0) && (inputOffset == 0)
                        && (inputLength == input.length)) {
                    // Nothing buffered and all of input array needs to be fed into the Consumer.
                    chunk = input;
                    inputBytesInChunk = input.length;
                } else {
                    // Need to combine buffered data with input data into one array.
                    chunk = new byte[mBufferedLength + inputLength];
                    inputBytesInChunk = inputLength;
                    System.arraycopy(mBuffered, mBufferedOffset, chunk, 0, mBufferedLength);
                    System.arraycopy(input, inputOffset, chunk, mBufferedLength, inputLength);
                }
            }

            Consumer.ConsumeResult consumeResult = mConsumer.consume(chunk);
            if (consumeResult.consumed == chunk.length) {
                // The whole chunk was consumed
                mBuffered = EMPTY_BYTE_ARRAY;
                mBufferedOffset = 0;
                mBufferedLength = 0;
                inputOffset += inputBytesInChunk;
                inputLength -= inputBytesInChunk;
            } else if (consumeResult.consumed == 0) {
                // Nothing was consumed. More input needed.
                mBuffered = chunk;
                mBufferedOffset = 0;
                mBufferedLength = chunk.length;
                if ((inputLength - inputBytesInChunk) > 0) {
                    // More input is available, but it wasn't included into the previous chunk
                    // because the chunk reached its maximum permitted size
                    // Shouldn't have happened.
                    throw new RuntimeException("Nothing consumed from max-sized chunk: "
                            + chunk.length + " bytes");
                } else {
                    // No more input data available
                    inputOffset += inputBytesInChunk;
                    inputLength = 0;
                }
            } else if (consumeResult.consumed < chunk.length) {
                // The chunk was consumed only partially -- buffer the rest of the chunk
                mBuffered = chunk;
                mBufferedOffset = consumeResult.consumed;
                mBufferedLength = chunk.length - consumeResult.consumed;

                int inputConsumed = consumeResult.consumed - mBuffered.length;
                if (inputConsumed >= 0) {
                    // Some of input data was consumed -- buffer the rest of the chunk.
                    inputOffset += inputConsumed;
                    inputLength -= inputConsumed;
                }
            } else {
                throw new RuntimeException("Consumed more than provided: " + consumeResult.consumed
                        + ", provided: " + chunk.length);
            }

            if ((consumeResult.output != null) && (consumeResult.output.length > 0)) {
                if (inputLength > 0) {
                    // More output might be produced in this loop -- buffer the current output
                    if (bufferedOutput == null) {
                        bufferedOutput = new ByteArrayOutputStream();
                        try {
                            bufferedOutput.write(consumeResult.output);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to buffer output", e);
                        }
                    }
                } else {
                    // No more output will be produced in this loop
                    if (bufferedOutput == null) {
                        // No previously buffered output
                        return consumeResult.output;
                    } else {
                        // There was some previously buffered output
                        try {
                            bufferedOutput.write(consumeResult.output);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to buffer output", e);
                        }
                        return bufferedOutput.toByteArray();
                    }
                }
            }
        }

        if (bufferedOutput == null) {
            // No output produced
            return EMPTY_BYTE_ARRAY;
        } else {
            return bufferedOutput.toByteArray();
        }
    }
}
