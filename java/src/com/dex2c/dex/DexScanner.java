package com.dex2c.dex;

import com.dex2c.filter.MethodFilter;
import com.dex2c.model.MethodInfo;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MultiDexContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class DexScanner {
    private final MethodFilter filter;

    public DexScanner(MethodFilter filter) {
        this.filter = filter;
    }

    public List<MethodInfo> scan(Path input) throws IOException {
        if (!Files.exists(input)) {
            throw new IOException("Input file does not exist: " + input);
        }

        if (input.getFileName().toString().endsWith(".apk")) {
            return scanApk(input);
        }
        return scanDex("classes.dex", DexFileFactory.loadDexFile(input.toFile(), Opcodes.getDefault()));
    }

    private List<MethodInfo> scanApk(Path apk) throws IOException {
        MultiDexContainer<? extends DexBackedDexFile> container =
                DexFileFactory.loadDexContainer(apk.toFile(), Opcodes.getDefault());
        List<MethodInfo> out = new ArrayList<>();
        for (String entryName : container.getDexEntryNames()) {
            MultiDexContainer.DexEntry<? extends DexBackedDexFile> entry =
                    container.getEntry(entryName);
            if (entry != null) {
                out.addAll(scanDex(entryName, entry.getDexFile()));
            }
        }
        out.sort(Comparator.comparing(MethodInfo::dexEntry).thenComparing(MethodInfo::toDexSignature));
        return out;
    }

    private List<MethodInfo> scanDex(String entryName, DexBackedDexFile dexFile) {
        List<MethodInfo> out = new ArrayList<>();
        for (ClassDef classDef : dexFile.getClasses()) {
            for (Method method : classDef.getMethods()) {
                MethodInfo info = MethodInfo.from(entryName, classDef, method);
                if (filter.matches(info)) {
                    out.add(info);
                }
            }
        }
        out.sort(Comparator.comparing(MethodInfo::toDexSignature));
        return out;
    }
}
