package dev.echo.engine.util;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes every byte to multiple downstream streams.
 */
public final class TeeOutputStream extends OutputStream {
    private final OutputStream[] branches;

    public TeeOutputStream(OutputStream... branches) {
        this.branches = branches.clone();
    }

    @Override
    public void write(int b) throws IOException {
        IOException first = null;
        for (OutputStream branch : branches) {
            try {
                branch.write(b);
            } catch (IOException error) {
                if (first == null) {
                    first = error;
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        IOException first = null;
        for (OutputStream branch : branches) {
            try {
                branch.write(b, off, len);
            } catch (IOException error) {
                if (first == null) {
                    first = error;
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }

    @Override
    public void flush() throws IOException {
        for (OutputStream branch : branches) {
            branch.flush();
        }
    }

    @Override
    public void close() throws IOException {
        IOException first = null;
        for (OutputStream branch : branches) {
            try {
                branch.close();
            } catch (IOException error) {
                if (first == null) {
                    first = error;
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }
}
