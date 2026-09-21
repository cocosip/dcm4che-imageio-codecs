package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * JPEG arithmetic entropy coder from ISO/IEC 10918-1, Annex D.
 *
 * <p>The contexts use the JPEG compact representation: low seven bits are the
 * probability state and bit seven is the MPS. A context value of zero is the
 * initial state (state 0, MPS 0).</p>
 */
final class JpegQmCoder {
    private JpegQmCoder() {
    }

    private static int nextLpsState(int state, int index) {
        return (state & 0x80) ^ JpegArithmeticContexts.nlps(index)
                ^ (JpegArithmeticContexts.switchMps(index) << 7);
    }

    static final class Encoder {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private int c;
        private int a = 0x10000;
        private int ct = 11;
        private int buffer = -1;
        private int stackedFf;
        private int pendingZero;
        private boolean finished;

        void encode(int[] contexts, int context, int value) {
            if (finished) {
                throw new IllegalStateException("arithmetic encoder is finished");
            }
            if (context < 0 || context >= contexts.length || (value & ~1) != 0) {
                throw new IllegalArgumentException("invalid arithmetic context or symbol");
            }
            int state = contexts[context] & 0xff;
            int index = state & 0x7f;
            int qe = JpegArithmeticContexts.qe(index);
            a -= qe;
            if (value != (state >>> 7)) {
                if (a >= qe) {
                    c += a;
                    a = qe;
                }
                contexts[context] = nextLpsState(state, index);
            } else {
                if (a >= 0x8000) {
                    return;
                }
                if (a < qe) {
                    c += a;
                    a = qe;
                }
                contexts[context] = ((state & 0x80) ^ JpegArithmeticContexts.nmps(index));
            }
            renormalize();
        }

        void restart() {
            finish();
            c = 0;
            a = 0x10000;
            ct = 11;
            buffer = -1;
            stackedFf = 0;
            pendingZero = 0;
            finished = false;
        }

        int size() {
            return output.size();
        }

        byte[] finish() {
            if (!finished) {
                int temp = (a - 1 + c) & 0xffff0000;
                c = temp < c ? temp + 0x8000 : temp;
                c <<= ct;
                if ((c & 0xf8000000) != 0) {
                    if (buffer >= 0) {
                        flushPendingZeros();
                        emit(buffer + 1);
                    }
                    pendingZero += stackedFf;
                    stackedFf = 0;
                } else {
                    if (buffer == 0) {
                        pendingZero++;
                    } else if (buffer >= 0) {
                        flushPendingZeros();
                        emit(buffer);
                    }
                    flushStackedFf();
                }
                if ((c & 0x07fff800) != 0) {
                    flushPendingZeros();
                    emit((c >>> 19) & 0xff);
                    if ((c & 0x07f800) != 0) {
                        emit((c >>> 11) & 0xff);
                    }
                }
                finished = true;
            }
            return output.toByteArray();
        }

        private void renormalize() {
            do {
                a <<= 1;
                c <<= 1;
                if (--ct == 0) {
                    int temp = c >>> 19;
                    if (temp > 0xff) {
                        if (buffer >= 0) {
                            flushPendingZeros();
                            emit(buffer + 1);
                        }
                        pendingZero += stackedFf;
                        stackedFf = 0;
                        buffer = temp & 0xff;
                    } else if (temp == 0xff) {
                        stackedFf++;
                    } else {
                        if (buffer == 0) {
                            pendingZero++;
                        } else if (buffer >= 0) {
                            flushPendingZeros();
                            emit(buffer);
                        }
                        flushStackedFf();
                        buffer = temp & 0xff;
                    }
                    c &= 0x7ffff;
                    ct += 8;
                }
            } while (a < 0x8000);
        }

        private void flushPendingZeros() {
            while (pendingZero-- > 0) {
                output.write(0);
            }
            pendingZero = 0;
        }

        private void flushStackedFf() {
            flushPendingZeros();
            while (stackedFf-- > 0) {
                output.write(0xff);
                output.write(0);
            }
            stackedFf = 0;
        }

        private void emit(int value) {
            output.write(value & 0xff);
            if ((value & 0xff) == 0xff) {
                output.write(0);
            }
        }
    }

    static final class Decoder {
        private final byte[] input;
        private int position;
        private int c;
        private int a;
        private int ct = -16;
        private boolean markerSeen;

        Decoder(byte[] input) throws IOException {
            if (input == null) {
                throw new NullPointerException("input");
            }
            this.input = input.clone();
            initialize();
        }

        int decode(int[] contexts, int context) throws IOException {
            if (context < 0 || context >= contexts.length) {
                throw new IllegalArgumentException("invalid arithmetic context");
            }
            while (a < 0x8000) {
                if (--ct < 0) {
                    int data = readDataByte();
                    c = (c << 8) | data;
                    if ((ct += 8) < 0) {
                        if (++ct == 0) {
                            a = 0x8000;
                        }
                    }
                }
                a <<= 1;
            }
            int state = contexts[context] & 0xff;
            int index = state & 0x7f;
            int qe = JpegArithmeticContexts.qe(index);
            int temp = a - qe;
            a = temp;
            temp <<= ct;
            int value;
            if (c >= temp) {
                c -= temp;
                if (a < qe) {
                    a = qe;
                    contexts[context] = ((state & 0x80) ^ JpegArithmeticContexts.nmps(index));
                    value = state >>> 7;
                } else {
                    a = qe;
                    contexts[context] = nextLpsState(state, index);
                    state ^= 0x80;
                    value = state >>> 7;
                }
            } else if (a < 0x8000) {
                if (a < qe) {
                    contexts[context] = nextLpsState(state, index);
                    state ^= 0x80;
                } else {
                    contexts[context] = ((state & 0x80) ^ JpegArithmeticContexts.nmps(index));
                }
                value = state >>> 7;
            } else {
                value = state >>> 7;
            }
            return value;
        }

        void restart() throws IOException {
            initialize();
        }

        private void initialize() throws IOException {
            c = 0;
            a = 0;
            ct = -16;
            markerSeen = false;
        }

        private int readDataByte() throws IOException {
            if (position >= input.length || markerSeen) {
                return 0;
            }
            int value = input[position++] & 0xff;
            if (value == 0xff) {
                while (position < input.length && (input[position] & 0xff) == 0xff) {
                    position++;
                }
                if (position < input.length && input[position] == 0) {
                    position++;
                    return 0xff;
                }
                markerSeen = true;
                return 0;
            }
            return value;
        }
    }
}
