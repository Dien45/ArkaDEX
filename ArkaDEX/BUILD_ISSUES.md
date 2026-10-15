## Build Issues

The build fails due to a system-level security feature (grsecurity/PaX) that prevents marking memory pages as executable. This is required by the JIT compiler in the JVM.

### Error Message
```
Error occurred during initialization
Failed mark memory page executable check grsecurity/PaX enabled
```

### Attempted Fixes
- Installed various JDK versions (OpenJDK 8, 11, 17)
- Set JVM options to force interpreted mode (`-Xint`) and reduce memory usage
- Tried to install alternative JVMs (JamVM, Cacao) but they are not available in the Alpine repositories
- Generated Gradle wrapper and attempted to build with system Gradle

### Conclusion
The build environment is incompatible with the PaX security feature enabled in the system kernel. To build this project, you must:
1. Disable PaX for the Java binary (if you have control over the system), or
2. Use a different build environment without PaX enabled, or
3. Use a JVM that does not require executable memory (such as a fully ahead-of-time compiled JVM, which is not available in standard distributions).

### Workflow File
The GitHub Actions workflow (`.github/workflows/build.yml`) is configured to build on Ubuntu-latest, which does not have PaX enabled by default. The build should succeed in that environment.

### Local Build (if PaX is not an issue)
If you are in an environment without PaX, you can build with:
```bash
cd ArkaDEX
./gradlew assembleDebug
```