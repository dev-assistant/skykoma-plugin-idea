package cn.hylstudio.skykoma.plugin.idea.util;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class SelfFirstClassLoader extends URLClassLoader {

    private static final String[] SELF_FIRST_PACKAGES = {
            "org.jetbrains.kotlin.scripting.compiler.plugin.impl.",
            "org.jetbrains.kotlin.com.intellij.",
    };

    private static final String[] CONFLICTING_PREFIXES = {
            "kotlin-stdlib-", "kotlin-reflect-", "kotlin-script-runtime-",
            "kotlin-scripting-", "kotlinx-serialization-", "kotlinx-coroutines-",
            "kotlin-daemon-embeddable-",
            "kotlin-build-tools-api-", "kotlin-serialization-compiler-plugin-"
    };

    private final ClassLoader parent;

    public SelfFirstClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, null);
        this.parent = parent;
    }

    private boolean isSelfFirst(String name) {
        for (String pkg : SELF_FIRST_PACKAGES) {
            if (name.startsWith(pkg)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c != null) {
                return c;
            }

            if (isSelfFirst(name)) {
                try {
                    c = findClass(name);
                    if (resolve) {
                        resolveClass(c);
                    }
                    return c;
                } catch (ClassNotFoundException ignored) {
                }
            }

            if (parent != null) {
                try {
                    c = parent.loadClass(name);
                    if (resolve) {
                        resolveClass(c);
                    }
                    return c;
                } catch (ClassNotFoundException ignored) {
                }
            }

            if (!isSelfFirst(name)) {
                try {
                    c = findClass(name);
                    if (resolve) {
                        resolveClass(c);
                    }
                    return c;
                } catch (ClassNotFoundException ignored) {
                }
            }

            throw new ClassNotFoundException(name);
        }
    }

    @Override
    public URL getResource(String name) {
        URL url = findResource(name);
        if (url != null) {
            return url;
        }
        if (parent != null) {
            return parent.getResource(name);
        }
        return null;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        List<URL> urls = new ArrayList<>();
        Enumeration<URL> selfResources = findResources(name);
        while (selfResources.hasMoreElements()) {
            urls.add(selfResources.nextElement());
        }
        if (parent != null) {
            Enumeration<URL> parentResources = parent.getResources(name);
            while (parentResources.hasMoreElements()) {
                urls.add(parentResources.nextElement());
            }
        }
        return java.util.Collections.enumeration(urls);
    }

    public static SelfFirstClassLoader fromPluginLibDir(Path pluginPath, ClassLoader parent) throws IOException {
        Path libDir = pluginPath.resolve("lib");
        List<URL> urls = new ArrayList<>();
        if (Files.isDirectory(libDir)) {
            Files.list(libDir)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        if (!name.endsWith(".jar")) {
                            return false;
                        }
                        for (String prefix : CONFLICTING_PREFIXES) {
                            if (name.startsWith(prefix)) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .forEach(p -> {
                        try {
                            urls.add(p.toUri().toURL());
                        } catch (IOException ignored) {
                        }
                    });
        }
        return new SelfFirstClassLoader(urls.toArray(new URL[0]), parent);
    }
}
