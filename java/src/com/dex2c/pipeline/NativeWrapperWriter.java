package com.dex2c.pipeline;

import com.dex2c.model.MethodInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class NativeWrapperWriter {

    private NativeWrapperWriter() {
    }

    static void write(
            Path cppFile,
            List<MethodInfo> methods,
            Map<Integer, String> nativeStrings)
            throws IOException {

        StringBuilder out = new StringBuilder();

        out.append("#include <jni.h>\n");
        out.append("#include <android/log.h>\n");
        out.append("#include <string>\n\n");

        out.append("""
                static jclass d2c_find_class(JNIEnv *env, const char *name) {
                    return env->FindClass(name);
                }

                """);

        /*
         * Native string storage.
         *
         * The Java/smali side calls:
         *
         * NativeStrings.get(id)
         *
         * and the actual string is returned from native code.
         */
        writeNativeStrings(out, nativeStrings);

        Set<String> emitted = new HashSet<>();

        for (MethodInfo method : methods) {

            if (!emitted.add(
                    JniNames.longName(method))) {
                continue;
            }

            writeMethod(out, method);
        }

        Files.createDirectories(
                cppFile.getParent());

        Files.writeString(
                cppFile,
                out.toString(),
                StandardCharsets.UTF_8);
    }

    private static void writeNativeStrings(
            StringBuilder out,
            Map<Integer, String> strings) {

        out.append(
                "extern \"C\" JNIEXPORT jstring JNICALL\n");

        out.append(
                "Java_com_dex2c_NativeStrings_get"
                        + "(JNIEnv *env, jclass clazz, jint id) {\n");

        out.append(
                "    switch (id) {\n");

        for (Map.Entry<Integer, String> entry :
                strings.entrySet()) {

            out.append("        case ")
                    .append(entry.getKey())
                    .append(":\n");

            out.append(
                    "            return env->NewStringUTF(\"")
                    .append(
                            escapeCppString(
                                    entry.getValue()))
                    .append("\");\n");
        }

        out.append(
                "        default:\n");

        out.append(
                "            return nullptr;\n");

        out.append(
                "    }\n");

        out.append(
                "}\n\n");
    }

    private static String escapeCppString(
            String value) {

        StringBuilder out =
                new StringBuilder();

        for (int i = 0;
             i < value.length();
             i++) {

            char c = value.charAt(i);

            switch (c) {

                case '\\':
                    out.append("\\\\");
                    break;

                case '"':
                    out.append("\\\"");
                    break;

                case '\n':
                    out.append("\\n");
                    break;

                case '\r':
                    out.append("\\r");
                    break;

                case '\t':
                    out.append("\\t");
                    break;

                case '\b':
                    out.append("\\b");
                    break;

                case '\f':
                    out.append("\\f");
                    break;

                default:

                    if (c < 0x20) {

                        out.append(
                                String.format(
                                        "\\x%02x",
                                        (int) c));

                    } else {

                        out.append(c);
                    }

                    break;
            }
        }

        return out.toString();
    }

    private static void writeMethod(
            StringBuilder out,
            MethodInfo method) {

        List<String> params =
                Descriptor.params(
                        method.descriptor());

        String ret =
                Descriptor.returnType(
                        method.descriptor());

        String retType =
                Descriptor.jniType(ret);

        out.append(
                "extern \"C\" JNIEXPORT ")
                .append(retType)
                .append(" JNICALL\n");

        out.append(
                JniNames.longName(method))
                .append("(JNIEnv *env, ");

        out.append(
                method.isStatic()
                        ? "jclass clazz"
                        : "jobject thiz");

        for (int i = 0;
             i < params.size();
             i++) {

            out.append(", ")
                    .append(
                            Descriptor.jniType(
                                    params.get(i)))
                    .append(" p")
                    .append(i);
        }

        out.append(") {\n");

        if (method.isStatic()) {

            out.append(
                    "    jclass target = "
                            + "d2c_find_class(env, \"")
                    .append(
                            method.className(),
                            1,
                            method.className().length() - 1)
                    .append("\");\n");

        } else {

            out.append(
                    "    jclass target = "
                            + "env->GetObjectClass(thiz);\n");
        }

        out.append(
                "    if (target == nullptr) { ");

        if (!ret.equals("V")) {

            out.append(
                    "return 0; ");

        } else {

            out.append(
                    "return; ");
        }

        out.append("}\n");

        out.append(
                "    jmethodID mid = env->")
                .append(
                        method.isStatic()
                                ? "GetStaticMethodID"
                                : "GetMethodID")
                .append(
                        "(target, \"")
                .append(
                        JniNames.renamedMethod(method))
                .append(
                        "\", \"")
                .append(
                        method.descriptor())
                .append(
                        "\");\n");

        out.append(
                "    if (mid == nullptr) { ");

        if (!ret.equals("V")) {

            out.append(
                    "return 0; ");

        } else {

            out.append(
                    "return; ");
        }

        out.append("}\n");

        if (!params.isEmpty()) {

            out.append(
                    "    jvalue args[")
                    .append(
                            params.size())
                    .append(
                            "];\n");

            for (int i = 0;
                 i < params.size();
                 i++) {

                out.append(
                        "    args[")
                        .append(i)
                        .append("].")
                        .append(
                                Descriptor.jvalueField(
                                        params.get(i)))
                        .append(
                                " = p")
                        .append(i)
                        .append(
                                ";\n");
            }
        }

        String call =
                "Call"
                        + (method.isStatic()
                        ? "Static"
                        : "")
                        + Descriptor.callSuffix(ret)
                        + "MethodA";

        out.append(
                "    ");

        if (!ret.equals("V")) {

            out.append(
                    "return (")
                    .append(retType)
                    .append(") ");
        }

        out.append(
                "env->")
                .append(call)
                .append("(")
                .append(
                        method.isStatic()
                                ? "target"
                                : "thiz")
                .append(
                        ", mid, ")
                .append(
                        params.isEmpty()
                                ? "nullptr"
                                : "args")
                .append(
                        ");\n");

        if (ret.equals("V")) {

            out.append(
                    "    return;\n");
        }

        out.append(
                "}\n\n");
    }
}
