/*
MIT License

Copyright (c) 2019-2024 Fox2Code

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package com.fox2code.rebuild;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.security.SecureClassLoader;
import java.util.*;
import java.util.function.Consumer;

public class ClassDataProvider {
    public static boolean debugClassResolution = "true".equalsIgnoreCase(System.getProperty("rebuild.debug"));
    private static final ClassLoader BOOTSTRAP_CLASS_LOADER;
    private static final ClData[] emptyClDataArray = new ClData[0];
    private static final int ASM_API = supportedAPI("ASM9", "ASM8", "ASM7", "ASM6", "ASM5");
    // Allow ClassDataProvider to be used with older ASM versions.
    private static int supportedAPI(String... versions) {
        for (String version : versions) {
            try {
                return Opcodes.class.getDeclaredField(version).getInt(null);
            } catch (Exception ignored) {}
        }
        throw new Error("ASM version too old, need at least ASM " +
                versions[versions.length - 1] + " (Recommended version: " + versions[0] + ")");
    }

    private static final ClData object;
    private static final ClData objectArray;

    static {
        // Use bootstrap class loader if the JVM support it
        ClassLoader bootstrapClassLoader = Object.class.getClassLoader();
        if (bootstrapClassLoader == null) {
            bootstrapClassLoader = new SecureClassLoader(null) {};
        }
        BOOTSTRAP_CLASS_LOADER = bootstrapClassLoader;
        object = new ObjectCLData();
        objectArray = new ObjectCLDataArray();
    }

    private final HashMap<String,ClData> clDataHashMap;
    private final ClassLoader classLoader;
    private final Consumer<ClassNode> clPatcher;

    public ClassDataProvider(ClassLoader classLoader) {
        this(classLoader, null);
    }

    public ClassDataProvider(ClassLoader classLoader, Consumer<ClassNode> clPatcher) {
        this.clDataHashMap = new HashMap<>();
        this.clDataHashMap.put("java/lang/Object", object);
        this.clDataHashMap.put("[java/lang/Object", objectArray);
        this.classLoader = classLoader == null ?
                ClassLoader.getSystemClassLoader() : classLoader;
        this.clPatcher = clPatcher;
    }

    public static abstract class ClData extends ClassData {
        final String name;
        String superClass;
        int access;

        private ClData(String name) {
            this.name = name;
            this.access = Opcodes.ACC_PUBLIC;
        }

        @Override
        public String getName() {
            return this.name.replace('/', '.');
        }

        @Override
        public String getDescriptor() {
            return "L" + this.name + ";";
        }

        @Override
        public String getInternalName() {
            return this.name;
        }

        @Override
        public int getModifiers() {
            return this.access;
        }

        @Override
        public boolean isInterface() {
            return Modifier.isInterface(this.access);
        }

        @Override
        public boolean isFinal() {
            return Modifier.isFinal(this.access);
        }

        @Override
        public boolean isPublic() {
            return Modifier.isPublic(this.access);
        }

        @Override
        public boolean isCustom() {
            return false;
        }

        @Override
        public abstract ClData getSuperclass();

        @Override
        public ClassData[] getInterfaces() {
            return emptyClDataArray;
        }

        @Override
        public boolean isAssignableFrom(ClassData clData) {
            while (clData != null) {
                if (clData==this) return true;
                clData = clData.getSuperclass();
            }
            return false;
        }

    }

    private static final class ObjectCLData extends ClData {
        private ObjectCLData() {
            super("java/lang/Object");
        }

        @Override
        public ClData getSuperclass() {
            return null;
        }

        @Override
        public boolean isAssignableFrom(ClassData clData) {
            return clData == this;
        }

    }

    private static final class ObjectCLDataArray extends ClData {
        private ObjectCLDataArray() {
            super("[java/lang/Object");
        }

        @Override
        public String getName() {
            return "[Ljava.lang.Object;";
        }

        @Override
        public String getDescriptor() {
            return "[Ljava/lang/Object;";
        }

        @Override
        public boolean isAssignableFrom(ClassData clData) {
            return clData == this || clData == object;
        }

        @Override
        public ClData getSuperclass() {
            return object;
        }
    }

    class ClData2 extends ClData {
        List<String> interfaces;
        boolean custom = false;

        private ClData2(String name) {
            super(name);
        }

        @Override
        public ClData getSuperclass() {
            if (this.superClass==null) return null;
            return getClassData(this.superClass);
        }

        @Override
        public ClassData[] getInterfaces() {
            if (interfaces == null) {
                return emptyClDataArray;
            }
            ClassData[] classData = new ClassData[interfaces.size()];
            int i = 0;
            for (String inName:interfaces) {
                classData[i] = getClassData(inName);
                i++;
            }
            return classData;
        }

        @Override
        public boolean isAssignableFrom(ClassData clData) {
            if (clData == null) return false;
            if (this.isInterface() && clData instanceof ClData2 &&
                    ((ClData2) clData).interfaces != null) {
                for (String cl : ((ClData2) clData).interfaces) {
                    if (this.isAssignableFrom(getClassData(cl))) {
                        return true;
                    }
                }
            }
            do {
                if (clData == this) return true;
                clData = clData.getSuperclass();
            } while (clData != null);
            return false;
        }

        @Override
        public boolean isCustom() {
            return custom;
        }
    }

    class ClData2Array extends ClData {
        private final ClData clData;

        private ClData2Array(ClData clData) {
            super("["+clData.getName());
            this.clData = clData;
            this.access = Opcodes.ACC_PUBLIC;
        }

        @Override
        public String getName() {
            return this.getDescriptor().replace('/', '.');
        }

        @Override
        public String getDescriptor() {
            return "[" + this.clData.getDescriptor();
        }

        @Override
        public ClData getSuperclass() {
            return object;
        }

        @Override
        public boolean isCustom() {
            return clData.isCustom();
        }
    }

    public ClData getClassData(String clName) {
        if (clName.startsWith("[")) {
            if (clName.endsWith(";")) {
                int i = 0;
                while (clName.charAt(i) == '[') {
                    i++;
                }
                clName = clName.substring(0, i) +
                        clName.substring(i + 1, clName.length() - 1);
            }
        } else if (clName.endsWith(";")) {
            throw new IllegalArgumentException("Can't put desc as class Data -> "+clName);
        }
        clName = clName.replace('.','/');
        ClData clData = clDataHashMap.get(clName);
        if (clData!=null) return clData;
        if (clName.startsWith("[")) {
            clDataHashMap.put(clName, clData = new ClData2Array(getClassData(clName.substring(1))));
            return clData;
        }
        clData = new ClData2(clName);
        final ClData2 tClData = (ClData2) clData;
        try {
            ClassReader classReader = new ClassReader(Objects.requireNonNull(
                    this.classLoader.getResourceAsStream(clName + ".class")));
            ClassVisitor classVisitor = new ClassVisitor(ASM_API) {
                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    tClData.access = access & ~Opcodes.ACC_SUPER;
                    tClData.superClass = superName;
                    if (interfaces != null && interfaces.length != 0) {
                        tClData.interfaces = Arrays.asList(interfaces);
                    }
                }
            };
            if (this.clPatcher != null) {
                ClassNodeHelper.acceptWithClPatcher(classReader, classVisitor, this.clPatcher);
            } else {
                classReader.accept(classVisitor, ClassReader.SKIP_CODE);
            }
        } catch (Exception e) {
            try { // Try to use the boot class loader as a fallback (Help fix issues on Java9+)
                Class<?> cl = Class.forName(clName.replace('/','.'), false, BOOTSTRAP_CLASS_LOADER);
                tClData.access = cl.getModifiers();
                tClData.superClass = cl.getGenericSuperclass().getTypeName().replace('.','/');
                Type[] classes = cl.getGenericInterfaces();
                if (classes.length != 0) {
                    String[] interfaces = new String[classes.length];
                    for (int i = 0; i < interfaces.length;i++) {
                        interfaces[i] = classes[i].getTypeName().replace('.','/');
                    }
                    tClData.interfaces = Arrays.asList(interfaces);
                }
            } catch (Exception e2) {
                if (debugClassResolution) {
                    System.out.println("DEBUG: Failed to resolve -> " + clName);
                }
                clData.superClass = "java/lang/Object";
            }
        }
        clDataHashMap.put(clName,clData);
        return clData;
    }

    public ClassWriter newClassWriter() {
        return new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                if (type1.equals(type2)) return type1;
                if (type1.equals("java/lang/Object") || type2.equals("java/lang/Object") ||
                        type1.startsWith("[") || type2.startsWith("[")) return "java/lang/Object";
                try {
                    ClData c, d;
                    try {
                        c = getClassData(type1);
                        d = getClassData(type2);
                    } catch (Exception e) {
                        throw new RuntimeException(e.toString());
                    }
                    if (c.isAssignableFrom(d)) {
                        return type1;
                    }
                    if (d.isAssignableFrom(c)) {
                        return type2;
                    }
                    if (c.isInterface() || d.isInterface()) {
                        return "java/lang/Object";
                    } else {
                        do {
                            c = c.getSuperclass();
                        } while (!c.isAssignableFrom(d));
                        return c.getName().replace('.', '/');
                    }
                } catch (Exception e) {
                    return "java/lang/Object";
                }
            }
        };
    }

    public void addClasses(Map<String, byte[]> classes) {
        for (Map.Entry<String, byte[]> entry:classes.entrySet()) if (entry.getKey().endsWith(".class")) {
            String name = entry.getKey().substring(0,entry.getKey().length()-6);
            ClData2 clData = new ClData2(name);
            try {
                ClassReader classReader = new ClassReader(entry.getValue());
                ClassVisitor classVisitor = new ClassVisitor(ASM_API) {
                    @Override
                    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                        clData.access = access & ~Opcodes.ACC_SUPER;
                        clData.superClass = superName;
                        clData.custom = true;
                        if (interfaces != null && interfaces.length != 0) {
                            clData.interfaces = Arrays.asList(interfaces);
                        }
                    }
                };
                if (this.clPatcher != null) {
                    ClassNodeHelper.acceptWithClPatcher(classReader, classVisitor, this.clPatcher);
                } else {
                    classReader.accept(classVisitor, ClassReader.SKIP_CODE);
                }

            } catch (Exception e) {
                if (debugClassResolution) {
                    System.out.println("DEBUG: Invalid input class -> " + name);
                }
                clData.superClass = "java/lang/Object";
            }
            clDataHashMap.put(name, clData);
        }
    }

    public void addClassesNodes(Map<String, ? extends ClassNode> classes) {
        ClassNodeHelper.addClassesNodes(this, classes);
    }

    // Allow ClassDataProvider to not require ClassTree at runtime, funnily enough, it's due to frames
    private static class ClassNodeHelper {
        static void acceptWithClPatcher(
                ClassReader classReader, ClassVisitor classVisitor, Consumer<ClassNode> clPatcher) {
            ClassNode classNode = new ClassNode();
            classReader.accept(classNode, ClassReader.SKIP_CODE);
            clPatcher.accept(classNode);
            classNode.accept(classVisitor);
        }

        static void addClassesNodes(ClassDataProvider classDataProvider, Map<String, ? extends ClassNode> classes) {
            for (Map.Entry<String, ? extends ClassNode> entry:classes.entrySet()) {
                ClData2 clData = classDataProvider.new ClData2(entry.getKey());
                ClassNode classNode = entry.getValue();
                if (classNode.name != null) {
                    classNode.accept(new ClassVisitor(ASM_API) {
                        @Override
                        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                            clData.access = access & ~Opcodes.ACC_SUPER;
                            clData.superClass = superName;
                            clData.custom = true;
                            if (interfaces != null && interfaces.length != 0) {
                                clData.interfaces = Arrays.asList(interfaces);
                            }
                        }
                    });
                } else {
                    if (debugClassResolution) {
                        System.out.println("DEBUG: Invalid input class -> " + clData.name);
                    }
                    clData.superClass = "java/lang/Object";
                }
                classDataProvider.clDataHashMap.put(clData.name, clData);
            }
        }
    }
}
