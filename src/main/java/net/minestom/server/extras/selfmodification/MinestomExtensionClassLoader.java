package net.minestom.server.extras.selfmodification;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public class MinestomExtensionClassLoader extends HierarchyClassLoader {

    /**
     * Root ClassLoader, everything goes through it before any attempt at loading is done inside this classloader.
     */
    private final MinestomRootClassLoader root;
    private static final boolean DEBUG = Boolean.getBoolean("classloader.debug");
    private static final boolean DUMP = DEBUG || Boolean.getBoolean("classloader.dump");

    public MinestomExtensionClassLoader(String name, URL[] urls, MinestomRootClassLoader root) {
        super(name, urls, root);
        this.root = root;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return root.loadClass(name);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        return root.loadClass(name, resolve);
    }

    /**
     * Assumes the name is not null, nor it does represent a protected class.
     *
     * @param name
     * @return The loaded class
     * @throws ClassNotFoundException if the class is not found inside this classloader
     */
    public Class<?> loadClassAsChild(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> loadedClass = findLoadedClass(name);
        if (loadedClass != null) {
            return loadedClass;
        }

        try {
            // not in children, attempt load in this classloader
            String path = name.replace(".", "/") + ".class";
            InputStream in = getResourceAsStream(path);
            if (in == null) {
                throw new ClassNotFoundException("Could not load class " + name);
            }
            try (in) {
                byte[] bytes = in.readAllBytes();
                bytes = root.transformBytes(bytes, name);
                if (DUMP) {
                    Path parent = Path.of("classes", path).getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.write(Path.of("classes", path), bytes);
                }
                Class<?> clazz = defineClass(name, bytes, 0, bytes.length);
                if (resolve) {
                    resolveClass(clazz);
                }
                return clazz;
            } catch (Throwable e) {
                throw new ClassNotFoundException("Could not load class " + name, e);
            }
        } catch (ClassNotFoundException e) {
            for (MinestomExtensionClassLoader child : children) {
                try {
                    Class<?> loaded = child.loadClassAsChild(name, resolve);
                    return loaded;
                } catch (ClassNotFoundException e1) {
                    // move on to next child
                    e.addSuppressed(e1);
                }
            }
            throw e;
        }
    }

    @Override
    @Deprecated(forRemoval = false, since = "9")
    protected void finalize() throws Throwable {
        super.finalize();
        System.err.println("Class loader " + getName() + " finalized.");
    }

    static {
        ClassLoader.registerAsParallelCapable();
    }
}
