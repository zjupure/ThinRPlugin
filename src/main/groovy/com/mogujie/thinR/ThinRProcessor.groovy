package com.mogujie.thinR

import org.gradle.api.GradleException
import org.objectweb.asm.*

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.regex.Matcher
import java.util.zip.ZipEntry

/**
 * Created by wangzhi on 16/7/28.
 */
class ThinRProcessor {
    HashMap<String, Integer> map
    String pkgName
    static def MATCHER_NO_STYLEABLE = '''.*(/|\\\\)R2?\\$(?!styleable).*?\\.class|.*(/|\\\\)R2?\\.class'''
    static def MATCHER_STYLEABLE =  '''.*(/|\\\\)R2?\\$styleable\\.class'''
    static def FIELD_R_REGEX = '''/R2?\\$(?!styleable).*'''
    static def FIELD_R_STYLEABLE_REGEX = '''/R2?\\$styleable'''
    static def MATCHER_R = '''.*(/|\\\\)R\\$.*?\\.class|.*(/|\\\\)R\\.class'''
    static def MATCHER_R2 = '''.*(/|\\\\)R2?\\$.*?\\.class|.*(/|\\\\)R2?\\.class'''


    ThinRProcessor(File rClassDir, String pkgName) {
        this.pkgName = pkgName
        this.map = getRsMap(rClassDir)
        PrintUtil.verbose("r map ==> " + map.size())
    }

    boolean needKeepEntry(String path) {
        if (path ==~ MATCHER_NO_STYLEABLE) {
            PrintUtil.verbose("del r " + path)
            return false
        } else if (path ==~ MATCHER_STYLEABLE){
            String parent = new File(path).getParent()
            if (parent.endsWith(pkgName.replace(".", File.separator))) {
                return true
            } else {
                PrintUtil.verbose("del library r styleable " + path)
                return false
            }
        } else {
            return true
        }
    }


    void processDir(File dir) {
        dir.eachFileRecurse { file ->
            PrintUtil.verbose("  " + "file--> " + file.absolutePath)
            if (file.name.endsWith(".class")) {
                processClass(file)
            } else if (file.name.endsWith(".jar")) {
                processJar(file)
            }
        }
    }


    void processClass(File classFile) {
        File tempFile = new File(classFile.parentFile, classFile.name + "_bak")
        InputStream originIns = classFile.newInputStream()
        byte[] bytes = Utils.toByteArray(originIns)
        originIns.close()
        bytes = getEntryBytes(classFile.absolutePath, bytes)
        if (needKeepEntry(classFile.absolutePath)) {
            OutputStream outputStream = tempFile.newOutputStream()
            outputStream.write(bytes, 0, bytes.length)
            outputStream.flush()
            outputStream.close()
            Utils.renameFile(tempFile, classFile)
        } else {
            Utils.delFile(classFile)
        }
    }


    void processJar(File jarFile) {
        JarFile jf = new JarFile(jarFile)
        Enumeration<JarEntry> je = jf.entries()
        File tempJar = new File(jarFile.parentFile, "temp.jar")
        JarOutputStream jos = new JarOutputStream(new FileOutputStream(tempJar))

        while (je.hasMoreElements()) {
            JarEntry jarEntry = je.nextElement()
            ZipEntry zipEntry = new ZipEntry(jarEntry.getName())
            InputStream originIns = jf.getInputStream(jarEntry)
            byte[] bytes = Utils.toByteArray(originIns)
            originIns.close()
            bytes = getEntryBytes(jarEntry.getName(), bytes)
            PrintUtil.verbose("  " + "jarEntry--> " + jarEntry.name)
            if (needKeepEntry(jarEntry.getName())) {
                jos.putNextEntry(zipEntry)
                jos.write(bytes)
                jos.closeEntry()
            }

        }
        jos.close()
        jf.close()
        jarFile.delete()
        Utils.renameFile(tempJar, jarFile)
    }


    byte[] getEntryBytes(String path, byte[] bytes) {
        if (!path.endsWith(".class") || path ==~ MATCHER_R2) {
            PrintUtil.verbose("is origin r or not class  ")
            return bytes
        } else {
            return replaceRToConstant(bytes, map, pkgName.replace(".", "/"))
        }
    }


    public static byte[] replaceRToConstant(byte[] bytes, Map<String, Integer> map, String pkgName) {
        ClassReader cr = new ClassReader(bytes)
        ClassWriter cw = new ClassWriter(cr, 0)
        String className = ""
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM4, cw) {
            @Override
            void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                PrintUtil.verbose("class name => " + name)
                className = name
                super.visit(version, access, name, signature, superName, interfaces)
            }
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                             String signature, String[] exceptions) {

                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                mv = new MethodVisitor(Opcodes.ASM4, mv) {

                    @Override
                    void visitFieldInsn(int i, String owner, String fieldName, String fdesc) {
                        //xxx/R$mipmap.xxx

                        def m = owner =~ ThinRProcessor.FIELD_R_REGEX
                        assert m instanceof Matcher
                        if (m) {
                            String p = m[0] - "/"
                            int value = -1
                            try {
                                value = map.get(p + "," + fieldName)
                            } catch (Exception e) {
                                throw new GradleException(owner + ":" + fieldName
                                        + " not found! in " + className + "'s " + name)
                            }

                            PrintUtil.verbose("R  Ref==> " + owner + "," + fieldName + "," + " replace to " + value)
                            Integer integer = new Integer(value)
                            super.visitLdcInsn(integer)
                        } else {

                            String newOwner = owner
                            def r = owner =~ ThinRProcessor.FIELD_R_STYLEABLE_REGEX
                            assert  r instanceof  Matcher
                            if (r) {
                                newOwner = pkgName + r[0]
                                PrintUtil.verbose("R  styleable Ref==>" + owner + "," + fieldName + "," + " replace to " +
                                    newOwner + "," + fieldName)
                            }

                            super.visitFieldInsn(i, newOwner, fieldName, fdesc)
                        }

                    }

                }

                return mv

            }
        }
        cr.accept(cv, 0)

        return cw.toByteArray()
    }


    public static Map getRsMap(File dir) {
        HashMap<String, Integer> map = new HashMap<>()
        if (dir.exists()) {
            dir.eachFile { file ->
                if (file.absolutePath ==~ MATCHER_R) {
                    PrintUtil.verbose("find r file ==> " + file.absolutePath)
                    map.putAll(getOneRMap(file))
                }
            }
        }
        return map

    }

    public static Map getOneRMap(File rFile) {
        InputStream inputStream = new FileInputStream(rFile)

        String type = rFile.name - ".class"
        ClassReader cr = new ClassReader(inputStream)
        PrintUtil.info("--------------------- start process file: " + rFile + "  ------------------")
        Map<String, Integer> map = new HashMap<>()
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM4) {

            @Override
            FieldVisitor visitField(int i, String s, String s1, String s2, Object o) {
                FieldVisitor fv = super.visitField(i, s, s1, s2, o)
                if (o instanceof Integer) {
                    map.put(type + "," + s, o)
                }
                return fv
            }

        }
        cr.accept(cv, 0)
        inputStream.close()
        PrintUtil.info("--------------------- end process file: " + rFile + "  ------------------")
        return map

    }

}
