package org.jetbrains.plugins.scala.nailgun;

import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * used in {@link org.jetbrains.plugins.scala.compiler.NonServerRunner}.
 */
public class MainLightRunner {

    public static void main(String[] args) throws ReflectiveOperationException {
        if (args.length < 3) throw invalidUsageException();

        String classpathStr = args[1];
        Path buildSystemDir = Paths.get(args[2]);
        String[] argsToDelegate = Arrays.copyOfRange(args, 3, args.length);
        URLClassLoader classLoader = NailgunRunner.constructClassLoader(classpathStr);
        runMainMethod(buildSystemDir, argsToDelegate, classLoader);
    }

    @SuppressWarnings({"SameParameterValue", "OptionalGetWithoutIsPresent"})
    private static void runMainMethod(Path buildSystemDir, String[] args, ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> mainClass = Utils.loadAndSetupMainClass(classLoader, buildSystemDir);
        Method mainMethod = Arrays.stream(mainClass.getDeclaredMethods()).filter(x -> x.getName().equals("main")).findFirst().get();
        mainMethod.invoke(null, (Object) args); // use as varargs, do not pass arra
    }

    private static IllegalArgumentException invalidUsageException() {
        String usage = "Usage: " + NailgunRunner.class.getSimpleName() +
                " [classpath] [system-dir-path] [other args]";
        return new IllegalArgumentException(usage);
    }
}
