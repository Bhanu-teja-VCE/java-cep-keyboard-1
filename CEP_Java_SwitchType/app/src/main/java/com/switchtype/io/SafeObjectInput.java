package com.switchtype.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Reading arbitrary objects from a file is a known security risk (a crafted file can make
 * the program create unexpected classes). This stream accepts only an explicit list of
 * class names and refuses everything else.
 */
public class SafeObjectInput extends ObjectInputStream {

    private final Set<String> allowed;

    public SafeObjectInput(InputStream in, String... allowedClasses) throws IOException {
        super(in);
        this.allowed = new HashSet<String>(Arrays.asList(allowedClasses));
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
        if (!allowed.contains(desc.getName())) {
            throw new InvalidClassException(desc.getName(), "not allowed in this file");
        }
        return super.resolveClass(desc);
    }
}
