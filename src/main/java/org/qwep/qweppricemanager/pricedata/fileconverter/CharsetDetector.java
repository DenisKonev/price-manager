package org.qwep.qweppricemanager.pricedata.fileconverter;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;


@Slf4j
public class CharsetDetector {

    private final String[] charsetsToBeTested;

    public CharsetDetector(String[] charsetsToBeTested) {
        this.charsetsToBeTested = charsetsToBeTested;
    }

    public Charset detectCharset(File f) {

        Charset charset = null;

        for (String charsetName : charsetsToBeTested) {
            charset = detectCharset(f, Charset.forName(charsetName));
            if (charset != null) {
                break;
            }
        }
        log.info("detected charset '{}' for file: '{}'", charset, f);
        return charset == null ? StandardCharsets.UTF_8 : charset;
    }

    private Charset detectCharset(File f, Charset charset) {
        try {
            BufferedInputStream input = new BufferedInputStream(new FileInputStream(f));

            CharsetDecoder decoder = charset.newDecoder();
            decoder.reset();

            byte[] buffer = new byte[512];
            boolean identified = true;
            while ((input.read(buffer) != -1) && identified) {
                identified = identify(buffer, decoder);
            }

            input.close();

            if (identified) {
                return charset;
            } else {
                return null;
            }

        } catch (Exception e) {
            return null;
        }
    }

    private boolean identify(byte[] bytes, CharsetDecoder decoder) {
        try {
            decoder.decode(ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException e) {
            return false;
        }
        return true;
    }

}
