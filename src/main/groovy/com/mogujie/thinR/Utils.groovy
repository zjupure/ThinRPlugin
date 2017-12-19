package com.mogujie.thinR

import com.google.common.base.Joiner
import com.google.common.io.Files

import java.security.MessageDigest

/**
 * Created by wangzhi on 16/9/12.
 */
class Utils {

    private static final Joiner PATH_JOINER = Joiner.on(File.separatorChar);

    public static String joinPath(String... paths) {
        PATH_JOINER.join(paths)
    }

    public static File joinFile(File file, String... paths) {
        return new File(file, joinPath(paths))
    }

    public static byte[] toByteArray(final InputStream input) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream()
        final byte[] buffer = new byte[8024]
        int n = 0
        long count = 0
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n)
            count += n
        }
        return output.toByteArray()
    }

    public static void delFile(File file) {
        if (!file.delete()) {
            throw new RuntimeException("del file ${file} failed ");
        }
    }

    public static void renameFile(File originFile, File targetFile) {
        if (targetFile.exists()) {
            targetFile.delete()
        }
        targetFile.parentFile.mkdirs()
        if (!originFile.renameTo(targetFile)) {
            throw new RuntimeException("${originFile} rename to ${targetFile} failed ");
        }

    }

    public static void copy(File src, File dst) {
        if (!src.exists()) {
            return
        }

        if (src.isFile()) {
            if (!dst.getParentFile().exists()) {
                dst.getParentFile().mkdirs()
            }
            dst.delete()
            dst.createNewFile()
            // copy
            Files.copy(src, dst)
            return
        }

        if (!dst.exists()) {
            dst.mkdirs()
        }

        src.list().each {name ->
            File srcFile = new File(src, name)
            File dstFile = new File(dst, name)
            copy(srcFile, dstFile)
        }
    }

    public static int compareVersion(String v1, String v2) {
        String[] va1 = v1.split("\\.")
        String[] va2 = v2.split("\\.")

        int idx = 0
        int minLen = Math.max(va1.length, va2.length)
        int diff = 0
        while (idx < minLen
            && (diff = va1[idx].length() - va2[idx].length()) == 0
            && (diff = va1[idx].compareTo(va2[idx])) == 0) {
            ++idx
        }

        return (diff != 0) ? diff : va1.length - va2.length
    }

    private static final char[] HEX_DIGITS = ['0', '1', '2', '3', '4',
        '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f']

    public static String md5(String str) {

        MessageDigest messageDigest = MessageDigest.getInstance("MD5")
        messageDigest.reset()
        messageDigest.update(str.getBytes("UTF-8"))
        byte[] byteArray = messageDigest.digest()

        StringBuilder hexString = new StringBuilder()
        for (byte b : byteArray) {
            hexString.append(HEX_DIGITS[b >> 4 & 0xf])
            hexString.append(HEX_DIGITS[b & 0xf])
        }

        return hexString.toString()
    }
}
