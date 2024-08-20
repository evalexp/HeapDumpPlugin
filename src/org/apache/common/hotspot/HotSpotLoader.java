package org.apache.common.hotspot;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class HotSpotLoader extends ClassLoader {
    private ClassLoader defaultLoader;
    private Map<String, byte[]> cache = new HashMap<String, byte[]>();
    private Map<String, Class<?>> classMap = new HashMap<String, Class<?>>();
    public HotSpotLoader() {
        this.defaultLoader = Thread.currentThread().getContextClassLoader();
    }

    public boolean cacheJar(ZipInputStream jarStream) {
        ZipEntry entry;
        try {
            while ((entry = jarStream.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.endsWith(".class")) {
                    name = name.substring(0, name.length() - 6).replaceAll("/", ".");
                    int n;
                    byte[] buff = new byte[1024];
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    while ((n = jarStream.read(buff)) != -1) {
                        baos.write(buff, 0, n);
                    }
                    this.cache.put(name, baos.toByteArray());
                }
                jarStream.closeEntry();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (classMap.containsKey(name)) {
            return classMap.get(name);
        }
        byte[] classBytes = cache.get(name);
        if (classBytes != null && classBytes.length != 0) {
            Class<?> clazz = this.defineClass(classBytes, 0, classBytes.length);
            classMap.put(name, clazz);
            cache.remove(name);
            return clazz;
        } else {
            try {
                return this.defaultLoader.loadClass(name);
            } catch (Exception e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }
}
