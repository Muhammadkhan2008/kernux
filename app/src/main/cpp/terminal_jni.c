// Kernux native PTY layer.
// Banata hai ek pseudo-terminal aur usme shell process chalata hai.
// Java side se isi PTY ka file-descriptor padha/likha jaata hai.

#include <jni.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <pty.h>
#include <termios.h>
#include <signal.h>

// PTY master fd banata hai, child process me shell chalata hai.
// return: master fd (>=0) ya -1 on error. childPid out-param me jaata hai.
JNIEXPORT jint JNICALL
Java_com_kernux_app_terminal_NativePty_createSubprocess(
        JNIEnv *env, jclass clazz,
        jstring cmd, jstring cwd, jobjectArray args,
        jobjectArray envVars, jintArray pidOut,
        jint rows, jint cols) {

    const char *cmd_str = (*env)->GetStringUTFChars(env, cmd, NULL);
    const char *cwd_str = (*env)->GetStringUTFChars(env, cwd, NULL);

    // args[] ko C char*[] me convert karo
    int argc = (*env)->GetArrayLength(env, args);
    char **argv = (char **) malloc((argc + 1) * sizeof(char *));
    for (int i = 0; i < argc; i++) {
        jstring s = (jstring) (*env)->GetObjectArrayElement(env, args, i);
        const char *cs = (*env)->GetStringUTFChars(env, s, NULL);
        argv[i] = strdup(cs);
        (*env)->ReleaseStringUTFChars(env, s, cs);
    }
    argv[argc] = NULL;

    // env[] ko C char*[] me convert karo
    int envc = (*env)->GetArrayLength(env, envVars);
    char **envp = (char **) malloc((envc + 1) * sizeof(char *));
    for (int i = 0; i < envc; i++) {
        jstring s = (jstring) (*env)->GetObjectArrayElement(env, envVars, i);
        const char *cs = (*env)->GetStringUTFChars(env, s, NULL);
        envp[i] = strdup(cs);
        (*env)->ReleaseStringUTFChars(env, s, cs);
    }
    envp[envc] = NULL;

    struct winsize sz;
    sz.ws_row = (unsigned short) rows;
    sz.ws_col = (unsigned short) cols;
    sz.ws_xpixel = 0;
    sz.ws_ypixel = 0;

    int master;
    pid_t pid = forkpty(&master, NULL, NULL, &sz);

    if (pid < 0) {
        return -1;
    }

    if (pid == 0) {
        // ---- Child process ----
        if (chdir(cwd_str) != 0) {
            // cwd na mile to bhi chalne do
        }
        execve(cmd_str, argv, envp);
        // execve fail hua to
        _exit(127);
    }

    // ---- Parent process ----
    (*env)->ReleaseStringUTFChars(env, cmd, cmd_str);
    (*env)->ReleaseStringUTFChars(env, cwd, cwd_str);

    jint pidArr[1];
    pidArr[0] = (jint) pid;
    (*env)->SetIntArrayRegion(env, pidOut, 0, 1, pidArr);

    return master;
}

JNIEXPORT jint JNICALL
Java_com_kernux_app_terminal_NativePty_readBytes(
        JNIEnv *env, jclass clazz, jint fd, jbyteArray buf, jint off, jint len) {
    jbyte *b = (*env)->GetByteArrayElements(env, buf, NULL);
    ssize_t r = read(fd, b + off, (size_t) len);
    (*env)->ReleaseByteArrayElements(env, buf, b, 0);
    if (r < 0) return -1;
    return (jint) r;
}

JNIEXPORT jint JNICALL
Java_com_kernux_app_terminal_NativePty_writeBytes(
        JNIEnv *env, jclass clazz, jint fd, jbyteArray buf, jint off, jint len) {
    jbyte *b = (*env)->GetByteArrayElements(env, buf, NULL);
    ssize_t w = write(fd, b + off, (size_t) len);
    (*env)->ReleaseByteArrayElements(env, buf, b, JNI_ABORT);
    if (w < 0) return -1;
    return (jint) w;
}

JNIEXPORT void JNICALL
Java_com_kernux_app_terminal_NativePty_setPtyWindowSize(
        JNIEnv *env, jclass clazz, jint fd, jint rows, jint cols) {
    struct winsize sz;
    sz.ws_row = (unsigned short) rows;
    sz.ws_col = (unsigned short) cols;
    sz.ws_xpixel = 0;
    sz.ws_ypixel = 0;
    ioctl(fd, TIOCSWINSZ, &sz);
}

JNIEXPORT void JNICALL
Java_com_kernux_app_terminal_NativePty_closeFd(
        JNIEnv *env, jclass clazz, jint fd) {
    close(fd);
}

// Child ke exit hone ka wait. Blocking.
JNIEXPORT jint JNICALL
Java_com_kernux_app_terminal_NativePty_waitFor(
        JNIEnv *env, jclass clazz, jint pid) {
    int status;
    while (waitpid(pid, &status, 0) < 0 && errno == EINTR) {}
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}
