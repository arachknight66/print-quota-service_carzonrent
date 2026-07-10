package com.printkeep.quota.ipp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@Getter
@AllArgsConstructor
public class IppAttribute {
    private final byte tag;
    private final String name;
    private final byte[] value;

    public String getValueAsString() {
        if (value == null) {
            return null;
        }
        return new String(value, StandardCharsets.UTF_8);
    }

    public Integer getValueAsInteger() {
        if (value == null || value.length != 4) {
            return null;
        }
        return ByteBuffer.wrap(value).getInt();
    }

    public Boolean getValueAsBoolean() {
        if (value == null || value.length != 1) {
            return null;
        }
        return value[0] != 0;
    }

    @Override
    public String toString() {
        String stringValue;
        if (tag == 0x21) {
            stringValue = String.valueOf(getValueAsInteger());
        } else if (tag == 0x22) {
            stringValue = String.valueOf(getValueAsBoolean());
        } else if (tag >= 0x40 && tag <= 0x48) {
            stringValue = getValueAsString();
        } else {
            stringValue = "binary[" + (value != null ? value.length : 0) + " bytes]";
        }
        return name + " (" + String.format("0x%02X", tag) + "): " + stringValue;
    }
}
