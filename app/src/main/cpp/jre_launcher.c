/*
 * Saturn JRE launcher (native).
 * Ditulis untuk Saturn: fork, lalu di child buka libjvm + libjli dan panggil JLI_Launch.
 * Tahap ini cuma untuk tes JVM (-version). Stdout/stderr child diarahkan ke file.
 */
#include <android/log.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#define TAG "SaturnJRE"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/*
 * Bionic: android_update_LD_LIBRARY_PATH tidak ada di stub libdl NDK,
 * jadi dicari saat runtime. Kalau tidak ada, fallback ke setenv.
 */
typedef void (*update_ld_path_t)(const char *);

static void set_ld_path(const char *path) {
    update_ld_path_t fn = (update_ld_path_t)dlsym(RTLD_DEFAULT, "android_update_LD_LIBRARY_PATH");
    if (fn) {
        fn(path);
    } else {
        setenv("LD_LIBRARY_PATH", path, 1);
    }
}

typedef jint (*JLI_Launch_t)(int argc, char **argv,
                             int jargc, const char **jargv,
                             int appclassc, const char **appclassv,
                             const char *fullversion, const char *dotversion,
                             const char *pname, const char *lname,
                             jboolean javaargs, jboolean cpwildcard,
                             jboolean javaw, jint ergo);

/* Jalan di proses child. Tidak boleh memakai JNI di sini. */
static int child_run(const char *jli_path, const char *jvm_path,
                     const char *ld_path, char **argv, const char *out_path) {
    int fd = open(out_path, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd >= 0) {
        dup2(fd, STDOUT_FILENO);
        dup2(fd, STDERR_FILENO);
        close(fd);
    }

    set_ld_path(ld_path);

    if (!dlopen(jvm_path, RTLD_NOW | RTLD_GLOBAL)) {
        fprintf(stderr, "dlopen libjvm gagal: %s\n", dlerror());
        return 101;
    }
    void *jli = dlopen(jli_path, RTLD_NOW | RTLD_GLOBAL);
    if (!jli) {
        fprintf(stderr, "dlopen libjli gagal: %s\n", dlerror());
        return 102;
    }
    JLI_Launch_t launch = (JLI_Launch_t)dlsym(jli, "JLI_Launch");
    if (!launch) {
        fprintf(stderr, "JLI_Launch tidak ditemukan\n");
        return 103;
    }

    int argc = 0;
    while (argv[argc]) argc++;

    int rc = launch(argc, argv, 0, NULL, 0, NULL,
                    "1.8.0-internal", "1.8", argv[0], argv[0],
                    JNI_FALSE, JNI_TRUE, JNI_FALSE, 0);
    fflush(stdout);
    fflush(stderr);
    return rc;
}

JNIEXPORT jint JNICALL
Java_com_sally_saturn_core_JvmLauncher_runJvm(JNIEnv *env, jobject thiz,
                                              jstring jJli, jstring jJvm,
                                              jstring jLd, jobjectArray jArgs,
                                              jstring jOut) {
    (void)thiz;
    const char *jli = (*env)->GetStringUTFChars(env, jJli, NULL);
    const char *jvm = (*env)->GetStringUTFChars(env, jJvm, NULL);
    const char *ld  = (*env)->GetStringUTFChars(env, jLd, NULL);
    const char *out = (*env)->GetStringUTFChars(env, jOut, NULL);

    jsize n = (*env)->GetArrayLength(env, jArgs);
    char **argv = calloc((size_t)n + 2, sizeof(char *));
    int rc = -1;

    if (argv) {
        argv[0] = strdup("java");
        for (jsize i = 0; i < n; i++) {
            jstring s = (jstring)(*env)->GetObjectArrayElement(env, jArgs, i);
            const char *c = (*env)->GetStringUTFChars(env, s, NULL);
            argv[i + 1] = strdup(c);
            (*env)->ReleaseStringUTFChars(env, s, c);
            (*env)->DeleteLocalRef(env, s);
        }

        pid_t pid = fork();
        if (pid == 0) {
            _exit(child_run(jli, jvm, ld, argv, out));
        } else if (pid < 0) {
            LOGE("fork gagal: %s", strerror(errno));
            rc = -2;
        } else {
            int status = 0;
            while (waitpid(pid, &status, 0) < 0 && errno == EINTR) {}
            rc = WIFEXITED(status) ? WEXITSTATUS(status) : -WTERMSIG(status);
            LOGI("JVM selesai, rc=%d", rc);
        }

        for (jsize i = 0; i < n + 1; i++) free(argv[i]);
        free(argv);
    }

    (*env)->ReleaseStringUTFChars(env, jJli, jli);
    (*env)->ReleaseStringUTFChars(env, jJvm, jvm);
    (*env)->ReleaseStringUTFChars(env, jLd, ld);
    (*env)->ReleaseStringUTFChars(env, jOut, out);
    return rc;
}
